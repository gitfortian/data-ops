#!/usr/bin/env node
/**
 * A7.1: trusted real-environment Project/RBAC acceptance.
 * Only POSTs login; all other requests are read-only GETs.
 */
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';
import { parseEvidenceJson } from '../product/lossless-json.mjs';

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const ACCOUNT_API = '/yak-security/api/v1/account/';

function required(env, name) {
  const value = env[name]?.trim();
  if (!value) throw new Error(name + ' is required');
  return value;
}
function positiveId(env, name) {
  const value = required(env, name);
  if (!/^[1-9]\d*$/.test(value)) throw new Error(name + ' must be a positive decimal ID');
  return value;
}
export function validateInputs(env) {
  const url = new URL(required(env, 'YAK_OPS_BASE_URL'));
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if ((url.protocol !== 'https:' && !(url.protocol === 'http:' && loopback))
      || url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('YAK_OPS_BASE_URL must be an HTTPS root URL (HTTP only for localhost)');
  }
  const primary = positiveId(env, 'YAK_OPS_A7_PRIMARY_PROJECT_ID');
  const secondary = positiveId(env, 'YAK_OPS_A7_SECONDARY_PROJECT_ID');
  if (BigInt(primary) === BigInt(secondary)) throw new Error('Project IDs must differ');
  return {
    baseUrl: url.origin, primary, secondary,
    metricId: positiveId(env, 'YAK_OPS_A7_METRIC_ID'),
    username: required(env, 'YAK_OPS_USERNAME'),
    password: required(env, 'YAK_OPS_PASSWORD'),
    deniedUsername: required(env, 'YAK_OPS_DENIED_USERNAME'),
    deniedPassword: required(env, 'YAK_OPS_DENIED_PASSWORD'),
    commit: env.YAK_OPS_ACCEPTANCE_COMMIT || null,
  };
}
function cookieValues(headers) {
  if (typeof headers.getSetCookie === 'function') return headers.getSetCookie();
  return (headers.get('set-cookie') ?? '').split(/,(?=\s*[^;,=\s]+=)/).filter(Boolean);
}
function session() {
  const cookies = new Map();
  return {
    observe(headers) {
      for (const entry of cookieValues(headers)) {
        const raw = entry.split(';', 1)[0];
        const pivot = raw.indexOf('=');
        if (pivot > 0) cookies.set(raw.slice(0, pivot).trim(), raw.slice(pivot + 1));
      }
    },
    header() { return [...cookies].map(([k, v]) => k + '=' + v).join('; '); },
  };
}
export function businessCode(result) {
  const raw = result.payload?.code ?? result.payload?.errorCode;
  return raw == null ? null : String(raw);
}
export function isSuccess(result) {
  const code = businessCode(result);
  return result.httpStatus >= 200 && result.httpStatus < 300
    && (code === null || code === '200') && result.payload?.success !== false;
}
export function isDenied(result, codes = ['401', '403']) {
  return codes.includes(String(result.httpStatus)) || codes.includes(businessCode(result));
}
async function request(fetcher, config, client, path, { method = 'GET', projectId, data } = {}) {
  const headers = { Accept: 'application/json' };
  if (projectId !== undefined) headers[PROJECT_HEADER] = projectId;
  if (data !== undefined) headers['Content-Type'] = 'application/json';
  if (client?.header()) headers.Cookie = client.header();
  const response = await fetcher(new URL(path, config.baseUrl + '/'), {
    method, headers, redirect: 'manual',
    body: data === undefined ? undefined : JSON.stringify(data),
  });
  client?.observe(response.headers);
  const raw = await response.text();
  let payload;
  try { payload = parseEvidenceJson(raw); } catch { payload = null; }
  return { httpStatus: response.status, payload };
}
async function login(fetcher, config, username, password) {
  const client = session();
  const result = await request(fetcher, config, client, ACCOUNT_API + 'login', {
    method: 'POST', data: { userName: username, pw: password },
  });
  if (!isSuccess(result) || !client.header()) throw new Error('trusted-environment login failed');
  const current = await request(fetcher, config, client, ACCOUNT_API + 'current');
  if (!isSuccess(current) || !current.payload?.data) {
    throw new Error('authenticated current identity unavailable');
  }
  return { client, identity: current.payload.data };
}
function memberOf(identity, projectId) {
  if (!Array.isArray(identity?.projectList)) {
    throw new Error('account/current missing authoritative projectList');
  }
  return identity.projectList.some(p => {
    if (p?.id == null || !/^\d+$/.test(String(p.id))) return false;
    return BigInt(String(p.id)) === BigInt(projectId);
  });
}
function assert(condition, message) {
  if (!condition) throw new Error('A7.1 fail-closed: ' + message);
}
export async function runAcceptance(env, fetcher = fetch) {
  const cfg = validateInputs(env);
  const path = '/api/v1/metrics/' + encodeURIComponent(cfg.metricId);
  const owner = await login(fetcher, cfg, cfg.username, cfg.password);
  assert(memberOf(owner.identity, cfg.primary), 'owner not a member of primary Project');
  assert(memberOf(owner.identity, cfg.secondary), 'owner cannot switch to secondary Project');

  // Same authenticated cookie, only the selected workspace header changes.
  const first = await request(fetcher, cfg, owner.client, path, { projectId: cfg.primary });
  assert(isSuccess(first), 'primary Project cannot read Golden Metric');
  const metric = first.payload?.data;
  assert(metric != null && String(metric.id) === cfg.metricId, 'Golden Metric identity mismatch');
  assert(typeof metric.metricCode === 'string' && metric.metricCode.length > 0,
    'Golden Metric code missing');
  const switched = await request(fetcher, cfg, owner.client, path, { projectId: cfg.secondary });
  const other = isSuccess(switched) ? switched.payload?.data : null;
  assert(!other || String(other.id) !== cfg.metricId,
    'secondary Project exposed primary Golden Metric');
  const restored = await request(fetcher, cfg, owner.client, path, { projectId: cfg.primary });
  assert(isSuccess(restored) && String(restored.payload?.data?.id) === cfg.metricId
    && restored.payload.data.metricCode === metric.metricCode,
    'return to primary Project did not restore the same Metric');

  const anonymous = await request(fetcher, cfg, null, path, { projectId: cfg.primary });
  assert(isDenied(anonymous), 'anonymous Metric read not rejected with 401/403');

  // A 403 for someone outside the project is NOT proof of RBAC denial.
  const denied = await login(fetcher, cfg, cfg.deniedUsername, cfg.deniedPassword);
  assert(memberOf(denied.identity, cfg.primary), 'denied principal is not a primary Project member');
  const forbidden = await request(fetcher, cfg, denied.client, path, { projectId: cfg.primary });
  assert(isDenied(forbidden, ['403']), 'member without metric read permission was not forbidden');

  return {
    probe: 'a7-project-rbac-real-env', acceptanceIssue: 368,
    commit: cfg.commit, primaryProjectId: cfg.primary, secondaryProjectId: cfg.secondary,
    metricId: cfg.metricId,
    assertions: {
      ownerMembershipBothProjects: 'PASSED', firstProjectRead: 'PASSED',
      switchDoesNotLeakSameMetric: 'PASSED', restoreOriginalProjectRead: 'PASSED',
      anonymousReadDenied: 'PASSED', memberWithoutPermissionForbidden: 'PASSED',
    },
    observations: {
      switchHttpStatus: switched.httpStatus, switchBusinessCode: businessCode(switched),
      anonymousHttpStatus: anonymous.httpStatus, deniedHttpStatus: forbidden.httpStatus,
    },
    credentialCaptured: false, readOnly: true, browserInteractionValidated: false,
    capturedAt: new Date().toISOString(),
  };
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  runAcceptance(process.env)
    .then(result => console.log(JSON.stringify(result, null, 2)))
    .catch(error => {
      console.error('A7.1 Project/RBAC evidence FAILED: ' + error.message);
      process.exitCode = 1;
    });
}
