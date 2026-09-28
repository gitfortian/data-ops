#!/usr/bin/env node

/**
 * Phase4 #104 real-environment Data Service Golden evidence runner.
 *
 * Console authentication is used only for source definition, observability and
 * Consumption-owned Usage Evidence synchronization. The actual Data Service call
 * is executed through the public runtime plane with X-API-Key and deliberately
 * sends neither Yak login cookies nor the Project Space header.
 *
 * Required environment variables:
 *   YAK_OPS_USERNAME
 *   YAK_OPS_PASSWORD
 *   YAK_OPS_PROJECT_ID
 *   YAK_OPS_DATA_SERVICE_ID
 *   YAK_OPS_DATA_SERVICE_API_KEY
 *
 * Optional:
 *   YAK_OPS_BASE_URL (default: http://localhost:9001)
 *   YAK_OPS_PUBLIC_BASE_URL (default: YAK_OPS_BASE_URL)
 *   YAK_OPS_DATA_SERVICE_PARAMS_JSON (default: {})
 *   YAK_OPS_DATA_SERVICE_LOG_LIMIT (default: 50, max: 200)
 *   YAK_OPS_EVIDENCE_INCLUDE_ROWS (default: false)
 *   YAK_OPS_EVIDENCE_INCLUDE_PARAMS (default: false)
 */

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const BASE_URL = (process.env.YAK_OPS_BASE_URL || 'http://localhost:9001').replace(/\/+$/, '');
const PUBLIC_BASE_URL = (process.env.YAK_OPS_PUBLIC_BASE_URL || BASE_URL).replace(/\/+$/, '');
const USERNAME = required('YAK_OPS_USERNAME');
const PASSWORD = required('YAK_OPS_PASSWORD');
const PROJECT_ID = positiveId('YAK_OPS_PROJECT_ID');
const DATA_SERVICE_ID = positiveId('YAK_OPS_DATA_SERVICE_ID');
const API_KEY = required('YAK_OPS_DATA_SERVICE_API_KEY');
const PARAMETERS = jsonObject('YAK_OPS_DATA_SERVICE_PARAMS_JSON', {});
const LOG_LIMIT = boundedPositiveInt('YAK_OPS_DATA_SERVICE_LOG_LIMIT', 50, 200);
const INCLUDE_ROWS = /^true$/i.test(process.env.YAK_OPS_EVIDENCE_INCLUDE_ROWS || 'false');
const INCLUDE_PARAMS = /^true$/i.test(process.env.YAK_OPS_EVIDENCE_INCLUDE_PARAMS || 'false');
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
    throw new Error(`${name} must contain valid JSON`);
  }
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${name} must contain a JSON object`);
  }
  const result = {};
  for (const [key, entry] of Object.entries(value)) {
    if (entry === null || entry === undefined) continue;
    if (typeof entry === 'object') {
      throw new Error(`${name}.${key} must be a scalar value`);
    }
    result[String(key)] = String(entry);
  }
  return result;
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

async function publicInvoke(runtimePath, parameters) {
  const url = new URL(runtimePath, `${PUBLIC_BASE_URL}/`);
  for (const [name, value] of Object.entries(parameters)) {
    url.searchParams.set(name, value);
  }

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
      'X-API-Key': API_KEY,
    },
    redirect: 'manual',
  });
  return checkedPayload(response, 'GET', `${url.pathname}${url.search}`);
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

function dataServiceSummary(service) {
  return {
    id: service?.id ?? DATA_SERVICE_ID,
    name: service?.name ?? null,
    path: service?.path ?? null,
    runtimePath: service?.runtimePath ?? null,
    enabled: service?.enabled ?? null,
    authMode: service?.authMode ?? null,
    parameterNames: Array.isArray(service?.parameterNames) ? service.parameterNames : [],
    paginationEnabled: service?.paginationEnabled ?? null,
    sourceType: service?.sourceType ?? null,
    sourceRef: service?.sourceRef ?? null,
    sourceRevisionId: service?.sourceRevisionId ?? null,
    sourceRevisionNo: service?.sourceRevisionNo ?? null,
  };
}

function invocationResultSummary(result) {
  return {
    columns: result?.columns ?? [],
    rowCount: result?.rowCount ?? null,
    truncated: result?.truncated ?? null,
    durationMs: result?.durationMs ?? null,
    totalNum: result?.totalNum ?? null,
    pageNum: result?.pageNum ?? null,
    pageSize: result?.pageSize ?? null,
    rows: INCLUDE_ROWS ? (result?.rows ?? []) : undefined,
    rowsCaptured: INCLUDE_ROWS,
  };
}

function invocationRecordSummary(record) {
  return {
    id: record?.id ?? null,
    projectId: record?.projectId ?? null,
    apiId: record?.apiId ?? null,
    serviceName: record?.serviceName ?? null,
    servicePath: record?.servicePath ?? null,
    callerType: record?.callerType ?? null,
    apiKeyId: record?.apiKeyId ?? null,
    consumerId: record?.consumerId ?? null,
    apiKeyName: record?.apiKeyName ?? null,
    apiKeyPrefix: record?.apiKeyPrefix ?? null,
    sourceRevisionId: record?.sourceRevisionId ?? null,
    sourceRevisionNo: record?.sourceRevisionNo ?? null,
    paramsJson: INCLUDE_PARAMS ? (record?.paramsJson ?? null) : undefined,
    success: record?.success ?? null,
    durationMs: record?.durationMs ?? null,
    rowCount: record?.rowCount ?? null,
    errorMessage: record?.errorMessage ?? null,
    createTime: record?.createTime ?? null,
  };
}

function usageNormalizationSummary(result) {
  const usage = result?.evidence ?? null;
  return {
    state: result?.state ?? null,
    providerEvidenceRef: result?.providerEvidenceRef ?? null,
    message: result?.message ?? null,
    evidence: usage ? {
      id: usage.id ?? null,
      projectId: usage.projectId ?? null,
      productKey: usage.productKey ?? null,
      sourceVersion: usage.sourceVersion ?? null,
      consumerRef: usage.consumerRef ?? null,
      observedAt: usage.observedAt ?? null,
      consumptionMode: usage.consumptionMode ?? null,
      outcome: usage.outcome ?? null,
      provider: usage.provider ?? null,
      providerEvidenceRef: usage.providerEvidenceRef ?? null,
      deduplicationId: usage.deduplicationId ?? null,
      normalizedAt: usage.normalizedAt ?? null,
    } : null,
  };
}

function maxRecordId(records) {
  return records.reduce((max, record) => {
    const id = Number(record?.id);
    return Number.isFinite(id) ? Math.max(max, id) : max;
  }, 0);
}

function findNewInvocation(records, previousMaxId, service) {
  const candidates = records
    .filter((record) => Number(record?.id) > previousMaxId)
    .filter((record) => String(record?.apiId ?? '') === String(DATA_SERVICE_ID))
    .filter((record) => record?.success === true)
    .sort((left, right) => Number(right?.id ?? 0) - Number(left?.id ?? 0));

  const exactRevision = candidates.find((record) =>
    String(record?.sourceRevisionId ?? '') === String(service.sourceRevisionId ?? '')
      && String(record?.sourceRevisionNo ?? '') === String(service.sourceRevisionNo ?? ''));
  return exactRevision ?? candidates[0] ?? null;
}

function findUsageNormalization(results, invocationRecord) {
  const expectedRef = `invocation:${invocationRecord.id}`;
  return results.find((result) => result?.providerEvidenceRef === expectedRef) ?? null;
}

function publicRequestSummary(service) {
  return {
    endpoint: service.runtimePath,
    parameterNames: Object.keys(PARAMETERS).sort(),
    parameters: INCLUDE_PARAMS ? PARAMETERS : undefined,
    parametersCaptured: INCLUDE_PARAMS,
    auth: {
      mode: 'API_KEY',
      header: 'X-API-Key',
      credentialCaptured: false,
      yakLoginCookieSent: false,
      projectHeaderSent: false,
    },
  };
}

async function main() {
  await consoleRequest('/yak-security/api/v1/account/login', {
    method: 'POST',
    body: { userName: USERNAME, pw: PASSWORD },
  });

  const currentUser = currentUserSummary(
    await consoleRequest('/yak-security/api/v1/account/current'),
  );

  const service = dataServiceSummary(dataOf(await consoleRequest(
    `/api/v1/data-service/${DATA_SERVICE_ID}`,
    { projectScoped: true },
  )));

  if (service.enabled !== true) {
    throw new Error(`Data Service ${DATA_SERVICE_ID} is not enabled`);
  }
  if (String(service.authMode ?? '').toUpperCase() !== 'API_KEY') {
    throw new Error(
      `Data Service ${DATA_SERVICE_ID} must use API_KEY auth for #104 Golden evidence (authMode=${service.authMode ?? '<unknown>'})`,
    );
  }
  if (!service.runtimePath) {
    throw new Error(`Data Service ${DATA_SERVICE_ID} does not expose runtimePath`);
  }
  if (!service.sourceRevisionId || !service.sourceRevisionNo) {
    throw new Error(`Data Service ${DATA_SERVICE_ID} does not expose an active source revision`);
  }

  const beforeRecords = rowsOf(await consoleRequest(
    `/api/v1/data-service/${DATA_SERVICE_ID}/logs?limit=${LOG_LIMIT}`,
    { projectScoped: true },
  ), 'Data Service InvocationRecord');
  const previousMaxId = maxRecordId(beforeRecords);

  const invocationResult = dataOf(await publicInvoke(service.runtimePath, PARAMETERS));

  const afterRecords = rowsOf(await consoleRequest(
    `/api/v1/data-service/${DATA_SERVICE_ID}/logs?limit=${LOG_LIMIT}`,
    { projectScoped: true },
  ), 'Data Service InvocationRecord');
  const invocationRecord = findNewInvocation(afterRecords, previousMaxId, service);
  if (!invocationRecord) {
    throw new Error(
      `Successful public invoke returned but no new InvocationRecord was found for Data Service ${DATA_SERVICE_ID}`,
    );
  }
  if (!invocationRecord.apiKeyId || !invocationRecord.consumerId) {
    throw new Error(
      `InvocationRecord ${invocationRecord.id} is not attributable to both an API Key and managed Consumer`,
    );
  }

  const normalizationResults = rowsOf(await consoleRequest(
    `/api/v1/consumption/usage-evidence/data-service/synchronize?apiId=${encodeURIComponent(DATA_SERVICE_ID)}&limit=${LOG_LIMIT}`,
    { method: 'POST', projectScoped: true },
  ), 'Data Service Usage Evidence normalization');
  const usageNormalization = findUsageNormalization(normalizationResults, invocationRecord);
  if (!usageNormalization) {
    throw new Error(
      `No Usage Evidence normalization result was returned for InvocationRecord ${invocationRecord.id}`,
    );
  }
  if (usageNormalization.state !== 'NORMALIZED' || !usageNormalization.evidence) {
    throw new Error(
      `InvocationRecord ${invocationRecord.id} did not normalize to Usage Evidence: state=${usageNormalization.state ?? '<unknown>'}, message=${usageNormalization.message ?? '<none>'}`,
    );
  }

  const evidence = {
    probe: 'phase4-real-env-data-service-golden',
    acceptanceIssue: 104,
    consoleBaseUrl: BASE_URL,
    publicBaseUrl: PUBLIC_BASE_URL,
    projectId: PROJECT_ID,
    authenticatedConsoleUser: currentUser,
    productKey: `DATA_SERVICE:${DATA_SERVICE_ID}`,
    sourceRef: {
      sourceDomain: 'DATA_SERVICE',
      sourceIdentity: String(DATA_SERVICE_ID),
    },
    dataService: service,
    publicInvocation: {
      request: publicRequestSummary(service),
      result: invocationResultSummary(invocationResult),
    },
    invocationRecord: invocationRecordSummary(invocationRecord),
    usageNormalization: usageNormalizationSummary(usageNormalization),
    capturedAt: new Date().toISOString(),
  };

  console.log(JSON.stringify(evidence, null, 2));
}

main().catch((error) => {
  console.error(`Phase4 Data Service Golden evidence failed: ${error.message}`);
  process.exitCode = 1;
});
