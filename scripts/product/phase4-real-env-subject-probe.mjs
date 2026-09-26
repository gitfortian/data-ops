#!/usr/bin/env node

/**
 * Read-only prerequisite probe for Phase4 #104 real-environment acceptance.
 *
 * It authenticates through Yak Security's cookie-backed account contract, applies
 * the canonical Project Space header, and lists candidate Dataset/Data Service
 * subjects. It deliberately does not publish, query, invoke, or mutate anything.
 *
 * Required environment variables:
 *   YAK_OPS_USERNAME
 *   YAK_OPS_PASSWORD
 *   YAK_OPS_PROJECT_ID
 *
 * Optional:
 *   YAK_OPS_BASE_URL (default: http://localhost:9001)
 */

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const BASE_URL = (process.env.YAK_OPS_BASE_URL || 'http://localhost:9001').replace(/\/+$/, '');
const USERNAME = required('YAK_OPS_USERNAME');
const PASSWORD = required('YAK_OPS_PASSWORD');
const PROJECT_ID = positiveId('YAK_OPS_PROJECT_ID');
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
  const headers = {
    Accept: 'application/json',
  };
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

function datasetCandidate(item) {
  const dataset = item?.dataset ?? item ?? {};
  const version = item?.currentVersion ?? dataset?.currentVersion ?? null;
  return {
    id: dataset.id ?? item?.datasetId ?? null,
    name: dataset.name ?? item?.name ?? null,
    status: dataset.status ?? item?.status ?? null,
    currentVersionId: dataset.currentVersionId ?? version?.id ?? item?.currentVersionId ?? null,
    currentVersionNo: version?.versionNo ?? item?.currentVersionNo ?? null,
  };
}

function dataServiceCandidate(item) {
  return {
    id: item?.id ?? null,
    name: item?.name ?? null,
    path: item?.path ?? null,
    runtimePath: item?.runtimePath ?? null,
    enabled: item?.enabled ?? null,
    authMode: item?.authMode ?? null,
    sourceType: item?.sourceType ?? null,
    sourceRef: item?.sourceRef ?? null,
    sourceRevisionId: item?.sourceRevisionId ?? null,
    sourceRevisionNo: item?.sourceRevisionNo ?? null,
  };
}

function currentUserSummary(payload) {
  const user = dataOf(payload) ?? {};
  return {
    id: user.id ?? user.userId ?? null,
    username: user.userName ?? user.username ?? null,
    name: user.name ?? user.nickName ?? user.displayName ?? null,
  };
}

async function main() {
  await request('/yak-security/api/v1/account/login', {
    method: 'POST',
    body: { userName: USERNAME, pw: PASSWORD },
  });

  const currentUser = await request('/yak-security/api/v1/account/current');
  const datasetPayload = await request('/api/v1/datasets', { projectScoped: true });
  const dataServicePayload = await request('/api/v1/data-service', { projectScoped: true });

  const datasets = rowsOf(datasetPayload, 'Dataset').map(datasetCandidate);
  const dataServices = rowsOf(dataServicePayload, 'Data Service').map(dataServiceCandidate);
  const onlineDatasets = datasets.filter(
    (candidate) => String(candidate.status ?? '').toUpperCase() === 'ONLINE',
  );
  const activeDataServices = dataServices.filter((candidate) => candidate.enabled === true);

  const evidence = {
    probe: 'phase4-real-env-subjects',
    mode: 'READ_ONLY',
    baseUrl: BASE_URL,
    projectId: PROJECT_ID,
    authenticatedUser: currentUserSummary(currentUser),
    datasetCandidates: onlineDatasets,
    dataServiceCandidates: activeDataServices,
    counts: {
      datasetsTotal: datasets.length,
      onlineDatasets: onlineDatasets.length,
      dataServicesTotal: dataServices.length,
      activeDataServices: activeDataServices.length,
    },
  };

  console.log(JSON.stringify(evidence, null, 2));
  if (onlineDatasets.length === 0 || activeDataServices.length === 0) {
    process.exitCode = 2;
  }
}

main().catch((error) => {
  console.error(`Phase4 subject probe failed: ${error.message}`);
  process.exitCode = 1;
});
