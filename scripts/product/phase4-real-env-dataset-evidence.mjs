#!/usr/bin/env node

/**
 * Phase4 #104 real-environment Dataset Golden evidence runner.
 *
 * This runner authenticates through Yak Security, reads one existing ONLINE Dataset,
 * executes the real Dataset Query boundary, then reads the matching QueryPerformance
 * record so the acceptance evidence contains both the business result and source-owned
 * execution diagnostics.
 *
 * Required environment variables:
 *   YAK_OPS_USERNAME
 *   YAK_OPS_PASSWORD
 *   YAK_OPS_PROJECT_ID
 *   YAK_OPS_DATASET_ID
 *
 * Optional:
 *   YAK_OPS_BASE_URL (default: http://localhost:9001)
 *   YAK_OPS_DATASET_QUERY_LIMIT (default: 10, max: 1000)
 *   YAK_OPS_EVIDENCE_INCLUDE_ROWS (default: false)
 */

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const BASE_URL = (process.env.YAK_OPS_BASE_URL || 'http://localhost:9001').replace(/\/+$/, '');
const USERNAME = required('YAK_OPS_USERNAME');
const PASSWORD = required('YAK_OPS_PASSWORD');
const PROJECT_ID = positiveId('YAK_OPS_PROJECT_ID');
const DATASET_ID = positiveId('YAK_OPS_DATASET_ID');
const QUERY_LIMIT = boundedPositiveInt('YAK_OPS_DATASET_QUERY_LIMIT', 10, 1000);
const INCLUDE_ROWS = /^true$/i.test(process.env.YAK_OPS_EVIDENCE_INCLUDE_ROWS || 'false');
const cookies = new Map();

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function positiveId(name) {
  const value = required(name);
  if (!/^\d+$/.test(value) || Number(value) <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}

function boundedPositiveInt(name, fallback, max) {
  const raw = process.env[name]?.trim();
  if (!raw) return fallback;
  if (!/^\d+$/.test(raw)) throw new Error(`${name} must be a positive integer`);
  const value = Number(raw);
  if (value <= 0 || value > max) throw new Error(`${name} must be between 1 and ${max}`);
  return value;
}

function rememberCookies(headers) {
  const values = typeof headers.getSetCookie === 'function'
    ? headers.getSetCookie()
    : fallbackSetCookies(headers.get('set-cookie'));

  for (const value of values) {
    const pair = value.split(';', 1)[0];
    const separator = pair.indexOf('=');
    if (separator <= 0) continue;
    cookies.set(pair.slice(0, separator).trim(), pair.slice(separator + 1).trim());
  }
}

function fallbackSetCookies(value) {
  if (!value) return [];
  return value.split(/,(?=\s*[^;,=\s]+=)/g).map((part) => part.trim());
}

function cookieHeader() {
  return [...cookies.entries()].map(([name, value]) => `${name}=${value}`).join('; ');
}

async function request(path, { method = 'GET', body, projectScoped = false } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (projectScoped) headers[PROJECT_HEADER] = PROJECT_ID;
  const cookie = cookieHeader();
  if (cookie) headers.Cookie = cookie;

  const response = await fetch(new URL(path, `${BASE_URL}/`), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'manual',
  });
  rememberCookies(response.headers);

  const text = await response.text();
  const payload = parseJson(text);
  if (!response.ok) {
    throw new Error(`${method} ${path} failed with HTTP ${response.status}: ${safeSummary(payload, text)}`);
  }
  return payload;
}

function parseJson(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function safeSummary(payload, raw) {
  if (payload && typeof payload === 'object') {
    const message = payload.message ?? payload.msg ?? payload.error;
    if (message) return String(message).slice(0, 300);
  }
  return (raw || '<empty response>').replace(/\s+/g, ' ').slice(0, 300);
}

function dataOf(payload) {
  if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
    if (Object.prototype.hasOwnProperty.call(payload, 'code') && Number(payload.code) !== 200) {
      throw new Error(`API returned application error code ${payload.code}`);
    }
    if (payload.success === false) throw new Error('API reported an unsuccessful response');
    if (Object.prototype.hasOwnProperty.call(payload, 'data')) return payload.data;
  }
  return payload;
}

function rowsOf(payload, label) {
  const value = dataOf(payload);
  if (Array.isArray(value)) return value;
  if (value && Array.isArray(value.records)) return value.records;
  if (value && Array.isArray(value.items)) return value.items;
  if (value && Array.isArray(value.list)) return value.list;
  throw new Error(`${label} response does not contain a recognizable list`);
}

function currentUserSummary(payload) {
  const user = dataOf(payload) ?? {};
  return {
    id: user.id ?? user.userId ?? null,
    username: user.userName ?? user.username ?? null,
    name: user.name ?? user.nickName ?? user.displayName ?? null,
  };
}

function datasetSummary(detail) {
  const dataset = detail?.dataset ?? {};
  const version = detail?.currentVersion ?? {};
  return {
    id: dataset.id ?? DATASET_ID,
    name: dataset.name ?? null,
    status: dataset.status ?? null,
    currentVersionId: dataset.currentVersionId ?? version.id ?? null,
    currentVersionNo: version.versionNo ?? null,
    sourceType: version.sourceType ?? null,
    sourceTaskAssetId: version.sourceTaskAssetId ?? null,
    sourceTaskRevisionId: version.sourceTaskRevisionId ?? null,
    sourceTaskRevisionNo: version.sourceTaskRevisionNo ?? null,
    dataSourceId: version.dataSourceId ?? null,
    fieldCount: Array.isArray(detail?.fields) ? detail.fields.length : null,
  };
}

function queryResultSummary(result) {
  return {
    queryId: result?.queryId ?? null,
    datasetId: result?.datasetId ?? null,
    datasetVersionId: result?.datasetVersionId ?? null,
    datasetVersionNo: result?.datasetVersionNo ?? null,
    bindings: result?.bindings ?? [],
    columns: result?.columns ?? [],
    returnedRows: result?.returnedRows ?? null,
    truncated: result?.truncated ?? null,
    elapsedMillis: result?.elapsedMillis ?? null,
    rows: INCLUDE_ROWS ? (result?.rows ?? []) : undefined,
    rowsCaptured: INCLUDE_ROWS,
  };
}

function performanceSummary(performance) {
  if (!performance) return null;
  return {
    queryId: performance.queryId ?? null,
    datasetId: performance.datasetId ?? null,
    datasetName: performance.datasetName ?? null,
    datasetVersionId: performance.datasetVersionId ?? null,
    datasetVersionNo: performance.datasetVersionNo ?? null,
    sourceType: performance.sourceType ?? null,
    dataSourceId: performance.dataSourceId ?? null,
    sqlHash: performance.sqlHash ?? null,
    status: performance.status ?? null,
    failureStage: performance.failureStage ?? null,
    errorType: performance.errorType ?? null,
    errorMessage: performance.errorMessage ?? null,
    waitMillis: performance.waitMillis ?? null,
    prepareMillis: performance.prepareMillis ?? null,
    executeMillis: performance.executeMillis ?? null,
    transferMillis: performance.transferMillis ?? null,
    totalMillis: performance.totalMillis ?? null,
    returnedRows: performance.returnedRows ?? null,
    truncated: performance.truncated ?? null,
    startedAt: performance.startedAt ?? null,
    finishedAt: performance.finishedAt ?? null,
  };
}

async function main() {
  await request('/yak-security/api/v1/account/login', {
    method: 'POST',
    body: { userName: USERNAME, pw: PASSWORD },
  });

  const currentUser = currentUserSummary(
    await request('/yak-security/api/v1/account/current'),
  );

  const detail = dataOf(await request(`/api/v1/datasets/${DATASET_ID}`, {
    projectScoped: true,
  }));
  const dataset = datasetSummary(detail);
  if (String(dataset.status ?? '').toUpperCase() !== 'ONLINE') {
    throw new Error(`Dataset ${DATASET_ID} is not ONLINE (status=${dataset.status ?? '<unknown>'})`);
  }
  if (!dataset.currentVersionNo || !dataset.currentVersionId) {
    throw new Error(`Dataset ${DATASET_ID} does not expose a current immutable version`);
  }

  const queryRequest = {
    versionNo: dataset.currentVersionNo,
    limit: QUERY_LIMIT,
  };
  const queryResult = dataOf(await request(`/api/v1/datasets/${DATASET_ID}/query`, {
    method: 'POST',
    body: queryRequest,
    projectScoped: true,
  }));
  const queryId = queryResult?.queryId;
  if (!queryId) throw new Error('Dataset Query succeeded but response did not contain queryId');

  const performances = rowsOf(await request(
    `/api/v1/datasets/query-performance?queryIds=${encodeURIComponent(queryId)}&limit=10`,
    { projectScoped: true },
  ), 'Dataset QueryPerformance');
  const performance = performances.find((item) => item?.queryId === queryId) ?? null;
  if (!performance) {
    throw new Error(`QueryPerformance evidence was not found for queryId=${queryId}`);
  }

  const evidence = {
    probe: 'phase4-real-env-dataset-golden',
    acceptanceIssue: 104,
    baseUrl: BASE_URL,
    projectId: PROJECT_ID,
    authenticatedUser: currentUser,
    productKey: `DATASET:${DATASET_ID}`,
    sourceRef: {
      sourceDomain: 'DATASET',
      sourceIdentity: String(DATASET_ID),
    },
    dataset,
    query: {
      endpoint: `/api/v1/datasets/${DATASET_ID}/query`,
      request: queryRequest,
      result: queryResultSummary(queryResult),
    },
    queryPerformance: performanceSummary(performance),
    capturedAt: new Date().toISOString(),
  };

  console.log(JSON.stringify(evidence, null, 2));
}

main().catch((error) => {
  console.error(`Phase4 Dataset Golden evidence failed: ${error.message}`);
  process.exitCode = 1;
});
