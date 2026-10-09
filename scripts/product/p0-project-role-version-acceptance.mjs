#!/usr/bin/env node
/**
 * P0 #336 / consolidated PR 2 of 4: read-only real-environment isolation receipt.
 *
 * Live mode REQUIRES a trusted non-production deployment plus three independent accounts:
 * project A reader, project B reader, and a same-project A user without metric:read.
 * All probes are GET, apart from the normal account login POST. Never publishes,
 * edits, creates approval records, or simulates concurrency against production.
 *
 * No account/password/cookie/body is serialized to the receipt. The deployment identity
 * fields are QA/Release declarations; this script cannot attest a running process.
 */
import { pathToFileURL } from 'node:url';

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';

export function classifyMetricResponse(status, envelope, expectedId, identityField = 'id') {
  if (status === 401 || (status >= 300 && status < 400)) return 'AUTH_FAILURE';
  if (status === 403 || status === 404) return 'DENIED';
  if (status >= 500 || status < 200) return 'UNAVAILABLE';
  if (status >= 400) return 'DENIED';
  if (!envelope || typeof envelope !== 'object') return 'INDETERMINATE';
  if (Object.hasOwn(envelope, 'code') && ![200, '200'].includes(envelope.code)) {
    const code = Number(envelope.code);
    if (code === 401) return 'AUTH_FAILURE';
    // Business errors like 999/internal 5xx are outages, not permission-denial proof.
    if (code === 999 || (code >= 500 && code < 600)) return 'UNAVAILABLE';
    return code >= 400 ? 'DENIED' : 'INDETERMINATE';
  }
  if (envelope.success === false) return 'INDETERMINATE';
  const value = Object.hasOwn(envelope, 'data') ? envelope.data : envelope;
  if (value == null) return 'ABSENT';
  if (typeof value !== 'object' || value[identityField] == null) return 'INDETERMINATE';
  return String(value[identityField]) === String(expectedId) ? 'FOUND' : 'WRONG_ID';
}

export function allowedVerdict(actual, expected) {
  if (expected === 'FOUND') return actual === 'FOUND';
  if (expected === 'DENIED') return actual === 'DENIED';
  if (expected === 'NOT_VISIBLE') return actual === 'DENIED' || actual === 'ABSENT';
  return false;
}

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`required environment input absent: ${name}`);
  return value;
}

function positiveId(name) {
  const v = required(name);
  if (!/^[1-9]\d*$/.test(v) || !Number.isSafeInteger(Number(v))) {
    throw new Error(`invalid positive integer: ${name}`);
  }
  return v;
}

function session() {
  const cookies = new Map();
  return {
    cookieHeader() {
      return [...cookies.entries()].map(([key, val]) => `${key}=${val}`).join('; ');
    },
    store(headers) {
      const values = typeof headers.getSetCookie === 'function'
        ? headers.getSetCookie()
        : (headers.get('set-cookie') || '').split(/,(?=\s*[^;,=\s]+=)/g);
      for (const item of values) {
        const pair = item.split(';', 1)[0];
        const cut = pair.indexOf('=');
        if (cut > 0) cookies.set(pair.slice(0, cut).trim(), pair.slice(cut + 1).trim());
      }
    },
  };
}

async function apiRequest(base, actor, route, { method = 'GET', body, projectId } = {}) {
  const headers = { Accept: 'application/json' };
  if (body != null) headers['Content-Type'] = 'application/json';
  if (projectId) headers[PROJECT_HEADER] = projectId;
  const cookie = actor.cookieHeader();
  if (cookie) headers.Cookie = cookie;
  const response = await fetch(new URL(route, base), {
    method,
    headers,
    body: body == null ? undefined : JSON.stringify(body),
    redirect: 'manual',
    signal: AbortSignal.timeout(15000),
  });
  actor.store(response.headers);
  const raw = await response.text();
  let envelope = null;
  try { envelope = JSON.parse(raw); } catch { /* never include raw response in output */ }
  return { status: response.status, envelope };
}

async function login(base, username, password) {
  const actor = session();
  const loginResult = await apiRequest(base, actor, '/yak-security/api/v1/account/login', {
    method: 'POST', body: { userName: username, pw: password },
  });
  if (loginResult.status !== 200 ||
      loginResult.envelope?.success === false ||
      (loginResult.envelope?.code != null &&
        ![200, '200'].includes(loginResult.envelope.code))) {
    throw new Error('login rejected; permission evidence unavailable');
  }
  const current = await apiRequest(base, actor, '/yak-security/api/v1/account/current');
  if (current.status !== 200 ||
      current.envelope?.success === false ||
      (current.envelope?.code != null &&
        ![200, '200'].includes(current.envelope.code)) ||
      !current.envelope?.data) {
    throw new Error('login cannot verify current session');
  }
  return actor;
}

function caseSpecs(a, b, metricA, metricB, versionA, versionB) {
  return [
    ['A-own-metric', 'A', a, metricA, 'FOUND', 'metric'],
    ['B-own-metric', 'B', b, metricB, 'FOUND', 'metric'],
    ['A-exact-version', 'A', a, metricA, 'FOUND', 'version', versionA],
    ['B-exact-version', 'B', b, metricB, 'FOUND', 'version', versionB],
    ['A-no-B-membership', 'A', b, metricB, 'DENIED', 'metric'],
    ['B-no-A-membership', 'B', a, metricA, 'DENIED', 'metric'],
    ['A-metric-not-in-B', 'B', b, metricA, 'NOT_VISIBLE', 'metric'],
    ['B-metric-not-in-A', 'A', a, metricB, 'NOT_VISIBLE', 'metric'],
    ['restricted-A-without-metric-read', 'R', a, metricA, 'DENIED', 'metric'],
  ];
}

async function main() {
  const base = new URL(required('YAK_OPS_BASE_URL'));
  if (base.protocol !== 'http:' && base.protocol !== 'https:') {
    throw new Error('YAK_OPS_BASE_URL must use http(s)');
  }
  const projectA = positiveId('YAK_OPS_PROJECT_A_ID');
  const projectB = positiveId('YAK_OPS_PROJECT_B_ID');
  if (projectA === projectB) throw new Error('Project A and B must be different');
  const metricA = positiveId('YAK_OPS_PROJECT_A_METRIC_ID');
  const metricB = positiveId('YAK_OPS_PROJECT_B_METRIC_ID');
  const versionA = positiveId('YAK_OPS_PROJECT_A_METRIC_VERSION');
  const versionB = positiveId('YAK_OPS_PROJECT_B_METRIC_VERSION');
  const declaredCommit = required('YAK_OPS_DEPLOYED_COMMIT');
  const receiptRef = required('YAK_OPS_DEPLOYMENT_RECEIPT_REF');
  if (!/^[0-9a-f]{40}$/i.test(declaredCommit)) throw new Error('invalid deployed commit');
  if (!/^[a-zA-Z0-9:/._#-]{1,180}$/.test(receiptRef)) {
    throw new Error('deployment receipt reference must be a non-sensitive stable ID');
  }
  if (metricA === metricB) throw new Error('fixture metric IDs must differ');

  const users = {
    A: required('YAK_OPS_PROJECT_A_USERNAME'),
    B: required('YAK_OPS_PROJECT_B_USERNAME'),
    R: required('YAK_OPS_RESTRICTED_A_USERNAME'),
  };
  if (new Set(Object.values(users)).size !== 3) {
    throw new Error('three distinct real account names are required');
  }
  const actors = {
    A: await login(base, users.A, required('YAK_OPS_PROJECT_A_PASSWORD')),
    B: await login(base, users.B, required('YAK_OPS_PROJECT_B_PASSWORD')),
    R: await login(base, users.R, required('YAK_OPS_RESTRICTED_A_PASSWORD')),
  };

  const results = [];
  for (const [id, actor, project, metric, expected, mode, version] of
    caseSpecs(projectA, projectB, metricA, metricB, versionA, versionB)) {
    const suffix = mode === 'version' ? `/versions/${version}` : '';
    const response = await apiRequest(base, actors[actor],
      `/api/v1/metrics/${metric}${suffix}`, { projectId: project });
    const actual = classifyMetricResponse(response.status, response.envelope,
      mode === 'version' ? version : metric, mode === 'version' ? 'version' : 'id');
    results.push({ caseId: id, expected, actual, httpStatus: response.status,
      result: allowedVerdict(actual, expected) ? 'PASS' : 'FAIL' });
  }
  const report = {
    schema: 'P0-PROJECT-ROLE-VERSION-20261009',
    type: 'LIVE_HTTP_READ_ONLY',
    runAt: new Date().toISOString(),
    // Declarations are not independent build/runtime attestation.
    deployment: { declaredCommit, receiptRef, independentlyVerified: false },
    projectPair: { projectA, projectB },
    accountSeparation: '3_DISTINCT_LOGGED_IN_IDENTITIES',
    probes: results,
    verdict: results.every(item => item.result === 'PASS') ? 'PROBES_PASS_PENDING_QA' : 'FAIL',
    goldenExitSigned: false,
  };
  process.stdout.write(JSON.stringify(report, null, 2) + '\n');
  if (report.verdict === 'FAIL') process.exitCode = 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    // No headers, account IDs, cookies, credentials or raw remote body in errors.
    process.stderr.write(JSON.stringify({
      verdict: 'BLOCKED_OR_EXECUTION_FAILED', reason: String(error.message).slice(0, 180),
      goldenExitSigned: false,
    }) + '\n');
    process.exitCode = 2;
  });
}
