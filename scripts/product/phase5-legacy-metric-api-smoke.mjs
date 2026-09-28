#!/usr/bin/env node

/**
 * Phase 5 / #124 read-only live compatibility smoke for the established Metric REST surface.
 *
 * Required:
 *   YAK_OPS_USERNAME
 *   YAK_OPS_PASSWORD
 *   YAK_OPS_PROJECT_ID
 *   YAK_OPS_METRIC_ID
 *
 * Optional:
 *   YAK_OPS_BASE_URL (default: http://localhost:9001)
 *   YAK_OPS_ACCEPTANCE_COMMIT
 *
 * This runner intentionally exercises only established read/discovery endpoints:
 * GET /api/v1/metrics/{id}, POST /api/v1/metrics/page, GET /api/v1/metrics/stats.
 * It never creates, updates, changes status, or deletes a Metric.
 */

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const BASE_URL = (process.env.YAK_OPS_BASE_URL || 'http://localhost:9001').replace(/\/+$/, '');
const USERNAME = required('YAK_OPS_USERNAME');
const PASSWORD = required('YAK_OPS_PASSWORD');
const PROJECT_ID = positiveId('YAK_OPS_PROJECT_ID');
const METRIC_ID = positiveId('YAK_OPS_METRIC_ID');

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function positiveId(name) {
  const value = required(name);
  if (!/^\d+$/.test(value) || Number(value) <= 0 || !Number.isSafeInteger(Number(value))) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}

function fallbackSetCookies(value) {
  if (!value) return [];
  return value.split(/,(?=\s*[^;,=\s]+=)/g).map((part) => part.trim());
}

function makeSession() {
  const cookies = new Map();
  return {
    remember(headers) {
      const values = typeof headers.getSetCookie === 'function'
        ? headers.getSetCookie()
        : fallbackSetCookies(headers.get('set-cookie'));
      for (const value of values) {
        const pair = value.split(';', 1)[0];
        const separator = pair.indexOf('=');
        if (separator <= 0) continue;
        cookies.set(pair.slice(0, separator).trim(), pair.slice(separator + 1).trim());
      }
    },
    cookieHeader() {
      return [...cookies.entries()].map(([name, value]) => `${name}=${value}`).join('; ');
    },
  };
}

function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function dataOf(payload) {
  if (payload && typeof payload === 'object' && !Array.isArray(payload)
      && Object.prototype.hasOwnProperty.call(payload, 'data')) {
    return payload.data;
  }
  return payload;
}

function safeSummary(payload, raw) {
  const message = payload?.message ?? payload?.msg ?? payload?.error;
  if (message) return String(message).slice(0, 300);
  return (raw || '<empty response>').replace(/\s+/g, ' ').slice(0, 300);
}

async function request(session, path, { method = 'GET', body, projectId = PROJECT_ID } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (projectId != null) headers[PROJECT_HEADER] = String(projectId);
  const cookie = session.cookieHeader();
  if (cookie) headers.Cookie = cookie;

  const response = await fetch(new URL(path, `${BASE_URL}/`), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'manual',
  });
  session.remember(response.headers);
  const text = await response.text();
  const payload = parseJson(text);
  if (!response.ok) {
    throw new Error(`${method} ${path} failed with HTTP ${response.status}: ${safeSummary(payload, text)}`);
  }
  return { status: response.status, data: dataOf(payload) };
}

async function login() {
  const session = makeSession();
  await request(session, '/yak-security/api/v1/account/login', {
    method: 'POST',
    body: { userName: USERNAME, pw: PASSWORD },
    projectId: null,
  });
  const current = await request(session, '/yak-security/api/v1/account/current', { projectId: null });
  assert(current.data != null, 'logged-in current user must resolve');
  return { session, currentUser: current.data };
}

function assert(condition, message) {
  if (!condition) throw new Error(`legacy Metric API assertion failed: ${message}`);
}

function coreMetric(metric) {
  return {
    id: metric?.id ?? null,
    metricCode: metric?.metricCode ?? null,
    metricName: metric?.metricName ?? null,
    domainId: metric?.domainId ?? null,
    processId: metric?.processId ?? null,
    metricType: metric?.metricType ?? null,
    status: metric?.status ?? null,
    version: metric?.version ?? null,
  };
}

async function main() {
  const { session, currentUser } = await login();

  const detailResponse = await request(session, `/api/v1/metrics/${METRIC_ID}`);
  const metric = detailResponse.data;
  assert(metric != null, 'GET detail must return a Metric');
  assert(String(metric.id) === String(METRIC_ID), 'GET detail Metric identity mismatch');
  assert(typeof metric.metricCode === 'string' && metric.metricCode.length > 0,
    'GET detail must preserve metricCode');
  assert(typeof metric.metricName === 'string' && metric.metricName.length > 0,
    'GET detail must preserve metricName');
  assert(Number.isSafeInteger(Number(metric.version)) && Number(metric.version) > 0,
    'GET detail must preserve positive version');

  const pageResponse = await request(session, '/api/v1/metrics/page', {
    method: 'POST',
    body: { pageNo: 1, pageSize: 20, keyword: metric.metricCode },
  });
  const page = pageResponse.data ?? {};
  const records = Array.isArray(page.bizData)
    ? page.bizData
    : Array.isArray(page.records)
      ? page.records
      : [];
  assert(records.some((item) => String(item?.id) === String(METRIC_ID)
      && item?.metricCode === metric.metricCode),
    'POST page must discover the same Metric by legacy keyword request');

  const statsResponse = await request(session, '/api/v1/metrics/stats');
  const stats = statsResponse.data;
  assert(stats != null && typeof stats === 'object', 'GET stats must return an object');
  assert(Number(stats.total) >= 1, 'GET stats total must include at least the Golden Metric');

  const bundle = {
    probe: 'phase5-legacy-metric-api-live-smoke',
    acceptanceIssue: 124,
    phase: 5,
    commit: process.env.YAK_OPS_ACCEPTANCE_COMMIT?.trim() || null,
    baseUrl: BASE_URL,
    projectId: PROJECT_ID,
    authenticatedUser: {
      id: currentUser?.id ?? currentUser?.userId ?? null,
      username: currentUser?.userName ?? currentUser?.username ?? null,
    },
    metric: coreMetric(metric),
    endpoints: {
      detail: { method: 'GET', path: `/api/v1/metrics/${METRIC_ID}`, httpStatus: detailResponse.status },
      page: { method: 'POST', path: '/api/v1/metrics/page', httpStatus: pageResponse.status,
        request: { pageNo: 1, pageSize: 20, keyword: metric.metricCode }, matchedMetric: true },
      stats: { method: 'GET', path: '/api/v1/metrics/stats', httpStatus: statsResponse.status,
        total: Number(stats.total) },
    },
    assertions: {
      authenticatedLegacyRead: true,
      detailCoreContractPresent: true,
      legacyPageDefaultsAccepted: true,
      pageDiscoversGoldenMetric: true,
      statsReadable: true,
      readOnlySmoke: true,
    },
    safety: {
      metricCreateExecuted: false,
      metricUpdateExecuted: false,
      statusMutationExecuted: false,
      metricDeleteExecuted: false,
      credentialsEmitted: false,
    },
    capturedAt: new Date().toISOString(),
  };

  console.log(JSON.stringify(bundle, null, 2));
}

main().catch((error) => {
  console.error(`Phase5 legacy Metric API live smoke failed: ${error.message}`);
  process.exitCode = 1;
});
