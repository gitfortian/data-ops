#!/usr/bin/env node

/**
 * Phase4 #104 real-environment Data Service Golden evidence runner.
 *
 * This runner authenticates only for the management/read side, reads one existing active
 * Data Service, invokes the public runtime without Yak session cookies or Project headers,
 * then reads the matching InvocationRecord from the authenticated observability plane.
 * The public request therefore proves Consumer/API Key/IP authorization independently from
 * console RBAC.
 *
 * Required environment variables:
 *   YAK_OPS_USERNAME
 *   YAK_OPS_PASSWORD
 *   YAK_OPS_PROJECT_ID
 *   YAK_OPS_DATA_SERVICE_ID
 *   YAK_OPS_DATA_SERVICE_API_KEY
 *   YAK_OPS_DATA_SERVICE_CONSUMER_ID
 *
 * Optional:
 *   YAK_OPS_BASE_URL (default: http://localhost:9001)
 *   YAK_OPS_DATA_SERVICE_PARAMS_JSON (default: {})
 *   YAK_OPS_DATA_SERVICE_LOG_LIMIT (default: 50, max: 200)
 *   YAK_OPS_EVIDENCE_INCLUDE_ROWS (default: false)
 */

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const BASE_URL = (process.env.YAK_OPS_BASE_URL || 'http://localhost:9001').replace(/\/+$/, '');
const USERNAME = required('YAK_OPS_USERNAME');
const PASSWORD = required('YAK_OPS_PASSWORD');
const PROJECT_ID = positiveId('YAK_OPS_PROJECT_ID');
const SERVICE_ID = positiveId('YAK_OPS_DATA_SERVICE_ID');
const API_KEY = required('YAK_OPS_DATA_SERVICE_API_KEY');
const CONSUMER_ID = positiveId('YAK_OPS_DATA_SERVICE_CONSUMER_ID');
const LOG_LIMIT = boundedPositiveInt('YAK_OPS_DATA_SERVICE_LOG_LIMIT', 50, 200);
const INCLUDE_ROWS = /^true$/i.test(process.env.YAK_OPS_EVIDENCE_INCLUDE_ROWS || 'false');
const PARAMETERS = jsonObject('YAK_OPS_DATA_SERVICE_PARAMS_JSON', {});
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

function jsonObject(name, fallback) {
  const raw = process.env[name]?.trim();
  if (!raw) return fallback;
  let value;
  try {
    value = JSON.parse(raw);
  } catch {
    throw new Error(`${name} must be valid JSON`);
  }
  if (!value || Array.isArray(value) || typeof value !== 'object') {
    throw new Error(`${name} must be a JSON object`);
  }
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

async function consoleRequest(path, { method = 'GET', body, projectScoped = false } = {}) {
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
  return checkedPayload(response, method, path);
}

async function publicInvoke(runtimePath) {
  const url = new URL(runtimePath, `${BASE_URL}/`);
  for (const [name, value] of Object.entries(PARAMETERS)) {
    if (value !== null && value !== undefined) url.searchParams.set(name, String(value));
  }

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      'X-API-Key': API_KEY,
    },
    redirect: 'manual',
  });
  return checkedPayload(response, 'GET', `${runtimePath}?<redacted-params>`);
}

async function checkedPayload(response, method, path) {
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
  if (payload && typeof payload === 'object' && !Array.isArray(payload)
      && Object.prototype.hasOwnProperty.call(payload, 'data')) {
    return payload.data;
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

function serviceSummary(detail) {
  return {
    id: detail?.id ?? SERVICE_ID,
    name: detail?.name ?? null,
    path: detail?.path ?? null,
    runtimePath: detail?.runtimePath ?? null,
    enabled: detail?.enabled ?? null,
    authMode: detail?.authMode ?? null,
    sourceType: detail?.sourceType ?? null,
    sourceRef: detail?.sourceRef ?? null,
    sourceRevisionId: detail?.sourceRevisionId ?? null,
    sourceRevisionNo: detail?.sourceRevisionNo ?? null,
    parameterNames: Array.isArray(detail?.parameterNames) ? detail.parameterNames : [],
    updateTime: detail?.updateTime ?? null,
  };
}

function invocationResultSummary(result) {
  if (!result) return null;
  return {
    columns: result.columns ?? [],
    rowCount: result.rowCount ?? null,
    durationMs: result.durationMs ?? null,
    rows: INCLUDE_ROWS ? (result.rows ?? []) : undefined,
    rowsCaptured: INCLUDE_ROWS,
  };
}

function invocationRecordSummary(record) {
  if (!record) return null;
  return {
    id: record.id ?? null,
    projectId: record.projectId ?? null,
    apiId: record.apiId ?? null,
    serviceName: record.serviceName ?? null,
    servicePath: record.servicePath ?? null,
    callerType: record.callerType ?? null,
    apiKeyId: record.apiKeyId ?? null,
    consumerId: record.consumerId ?? null,
    apiKeyName: record.apiKeyName ?? null,
    apiKeyPrefix: record.apiKeyPrefix ?? null,
    sourceRevisionId: record.sourceRevisionId ?? null,
    sourceRevisionNo: record.sourceRevisionNo ?? null,
    success: record.success ?? null,
    durationMs: record.durationMs ?? null,
    rowCount: record.rowCount ?? null,
    errorMessage: record.errorMessage ?? null,
    createTime: record.createTime ?? null,
  };
}

function numericId(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : 0;
}

async function main() {
  await consoleRequest('/yak-security/api/v1/account/login', {
    method: 'POST',
    body: { userName: USERNAME, pw: PASSWORD },
  });

  const currentUser = currentUserSummary(
    await consoleRequest('/yak-security/api/v1/account/current'),
  );

  const detail = dataOf(await consoleRequest(`/api/v1/data-service/${SERVICE_ID}`, {
    projectScoped: true,
  }));
  const service = serviceSummary(detail);
  if (service.enabled !== true) {
    throw new Error(`Data Service ${SERVICE_ID} is not enabled`);
  }
  if (!service.runtimePath) {
    throw new Error(`Data Service ${SERVICE_ID} does not expose runtimePath`);
  }
  if (!service.sourceRevisionId || !service.sourceRevisionNo) {
    throw new Error(`Data Service ${SERVICE_ID} does not expose an active source revision`);
  }

  const beforeLogs = rowsOf(await consoleRequest(
    `/api/v1/data-service/${SERVICE_ID}/logs?limit=${LOG_LIMIT}`,
    { projectScoped: true },
  ), 'Data Service invocation logs');
  const previousMaxId = beforeLogs.reduce((max, row) => Math.max(max, numericId(row?.id)), 0);

  const invokeResult = dataOf(await publicInvoke(service.runtimePath));

  const afterLogs = rowsOf(await consoleRequest(
    `/api/v1/data-service/${SERVICE_ID}/logs?limit=${LOG_LIMIT}`,
    { projectScoped: true },
  ), 'Data Service invocation logs');

  const invocationRecord = afterLogs.find((row) =>
    numericId(row?.id) > previousMaxId
      && String(row?.apiId ?? '') === String(SERVICE_ID)
      && String(row?.consumerId ?? '') === String(CONSUMER_ID)
      && row?.success === true) ?? null;

  if (!invocationRecord) {
    throw new Error(
      `Successful managed-consumer InvocationRecord was not found for service=${SERVICE_ID}, consumer=${CONSUMER_ID}`,
    );
  }

  if (String(invocationRecord.sourceRevisionId ?? '') !== String(service.sourceRevisionId)
      || String(invocationRecord.sourceRevisionNo ?? '') !== String(service.sourceRevisionNo)) {
    throw new Error('InvocationRecord source revision does not match the active Data Service revision');
  }

  const evidence = {
    probe: 'phase4-real-env-data-service-golden',
    acceptanceIssue: 104,
    baseUrl: BASE_URL,
    projectId: PROJECT_ID,
    authenticatedConsoleUser: currentUser,
    publicInvokeUsesYakSession: false,
    productKey: `DATA_SERVICE:${SERVICE_ID}`,
    sourceRef: {
      sourceDomain: 'DATA_SERVICE',
      sourceIdentity: String(SERVICE_ID),
    },
    dataService: service,
    managedConsumer: {
      consumerId: CONSUMER_ID,
      apiKeyProvided: true,
      apiKeyValueCaptured: false,
    },
    publicInvoke: {
      endpoint: service.runtimePath,
      parameterNames: Object.keys(PARAMETERS),
      result: invocationResultSummary(invokeResult),
    },
    invocationRecord: invocationRecordSummary(invocationRecord),
    capturedAt: new Date().toISOString(),
  };

  console.log(JSON.stringify(evidence, null, 2));
}

main().catch((error) => {
  console.error(`Phase4 Data Service Golden evidence failed: ${error.message}`);
  process.exitCode = 1;
});
