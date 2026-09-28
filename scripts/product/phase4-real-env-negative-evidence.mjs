#!/usr/bin/env node

/**
 * Phase4 #104 safe negative-path evidence runner.
 *
 * Always verifies public Data Service invocation rejects an invalid API key.
 * Optionally verifies cross-project masking and Dataset action denial when the
 * corresponding environment is supplied. It does not mutate product metadata,
 * permissions, API keys, Consumers or subscriptions.
 *
 * Required:
 *   YAK_OPS_USERNAME
 *   YAK_OPS_PASSWORD
 *   YAK_OPS_PROJECT_ID
 *   YAK_OPS_DATASET_ID
 *   YAK_OPS_DATA_SERVICE_ID
 *
 * Optional:
 *   YAK_OPS_BASE_URL
 *   YAK_OPS_PUBLIC_BASE_URL
 *   YAK_OPS_DATA_SERVICE_PARAMS_JSON
 *   YAK_OPS_CROSS_PROJECT_ID
 *   YAK_OPS_DENIED_USERNAME
 *   YAK_OPS_DENIED_PASSWORD
 */

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const BASE_URL = (process.env.YAK_OPS_BASE_URL || 'http://localhost:9001').replace(/\/+$/, '');
const PUBLIC_BASE_URL = (process.env.YAK_OPS_PUBLIC_BASE_URL || BASE_URL).replace(/\/+$/, '');
const USERNAME = required('YAK_OPS_USERNAME');
const PASSWORD = required('YAK_OPS_PASSWORD');
const PROJECT_ID = positiveId('YAK_OPS_PROJECT_ID');
const DATASET_ID = positiveId('YAK_OPS_DATASET_ID');
const DATA_SERVICE_ID = positiveId('YAK_OPS_DATA_SERVICE_ID');
const PARAMETERS = jsonObject('YAK_OPS_DATA_SERVICE_PARAMS_JSON', {});
const CROSS_PROJECT_ID = optionalPositiveId('YAK_OPS_CROSS_PROJECT_ID');
const DENIED_USERNAME = process.env.YAK_OPS_DENIED_USERNAME?.trim() || null;
const DENIED_PASSWORD = process.env.YAK_OPS_DENIED_PASSWORD?.trim() || null;

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function positiveId(name) {
  const value = required(name);
  if (!/^\d+$/.test(value) || Number(value) <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}

function optionalPositiveId(name) {
  const value = process.env[name]?.trim();
  if (!value) return null;
  if (!/^\d+$/.test(value) || Number(value) <= 0) throw new Error(`${name} must be a positive integer`);
  return value;
}

function jsonObject(name, fallback) {
  const raw = process.env[name]?.trim();
  if (!raw) return fallback;
  const value = JSON.parse(raw);
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${name} must contain a JSON object`);
  return Object.fromEntries(Object.entries(value)
    .filter(([, entry]) => entry !== null && entry !== undefined)
    .map(([key, entry]) => [String(key), String(entry)]));
}

function newSession() {
  return { cookies: new Map() };
}

function rememberCookies(session, headers) {
  const values = typeof headers.getSetCookie === 'function'
    ? headers.getSetCookie()
    : fallbackSetCookies(headers.get('set-cookie'));
  for (const value of values) {
    const pair = value.split(';', 1)[0];
    const separator = pair.indexOf('=');
    if (separator > 0) session.cookies.set(pair.slice(0, separator).trim(), pair.slice(separator + 1).trim());
  }
}

function fallbackSetCookies(value) {
  if (!value) return [];
  return value.split(/,(?=\s*[^;,=\s]+=)/g).map((part) => part.trim());
}

function cookieHeader(session) {
  return [...session.cookies.entries()].map(([name, value]) => `${name}=${value}`).join('; ');
}

async function rawRequest(path, {
  method = 'GET', body, projectId, session, baseUrl = BASE_URL, headers: suppliedHeaders = {},
} = {}) {
  const headers = { Accept: 'application/json', ...suppliedHeaders };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (projectId) headers[PROJECT_HEADER] = String(projectId);
  const cookie = session ? cookieHeader(session) : '';
  if (cookie) headers.Cookie = cookie;

  const response = await fetch(new URL(path, `${baseUrl}/`), {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    redirect: 'manual',
  });
  if (session) rememberCookies(session, response.headers);
  const text = await response.text();
  return { status: response.status, ok: response.ok, payload: parseJson(text), raw: text };
}

function parseJson(text) {
  if (!text) return null;
  try { return JSON.parse(text); } catch { return null; }
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

function safeMessage(result) {
  const payload = result.payload;
  if (payload && typeof payload === 'object') {
    const message = payload.message ?? payload.msg ?? payload.error;
    if (message) return String(message).slice(0, 300);
  }
  return (result.raw || '<empty response>').replace(/\s+/g, ' ').slice(0, 300);
}

async function login(username, password) {
  const session = newSession();
  const result = await rawRequest('/yak-security/api/v1/account/login', {
    method: 'POST', body: { userName: username, pw: password }, session,
  });
  if (!result.ok) throw new Error(`login failed for ${username}: HTTP ${result.status}`);
  return session;
}

async function main() {
  if ((DENIED_USERNAME && !DENIED_PASSWORD) || (!DENIED_USERNAME && DENIED_PASSWORD)) {
    throw new Error('YAK_OPS_DENIED_USERNAME and YAK_OPS_DENIED_PASSWORD must be supplied together');
  }

  const ownerSession = await login(USERNAME, PASSWORD);
  const serviceResult = await rawRequest(`/api/v1/data-service/${DATA_SERVICE_ID}`, {
    projectId: PROJECT_ID, session: ownerSession,
  });
  if (!serviceResult.ok) throw new Error(`Data Service read failed: HTTP ${serviceResult.status}`);
  const service = dataOf(serviceResult.payload) ?? {};
  if (!service.runtimePath) throw new Error(`Data Service ${DATA_SERVICE_ID} does not expose runtimePath`);

  const invalidKeyUrl = new URL(service.runtimePath, `${PUBLIC_BASE_URL}/`);
  for (const [name, value] of Object.entries(PARAMETERS)) invalidKeyUrl.searchParams.set(name, value);
  const invalidKey = await rawRequest(`${invalidKeyUrl.pathname}${invalidKeyUrl.search}`, {
    baseUrl: PUBLIC_BASE_URL,
    headers: { 'X-API-Key': `phase4-invalid-${Date.now()}-${Math.random().toString(36).slice(2)}` },
  });
  if (invalidKey.status !== 401 && invalidKey.status !== 403) {
    throw new Error(`invalid API key must be rejected with 401/403, got HTTP ${invalidKey.status}: ${safeMessage(invalidKey)}`);
  }

  let crossProject = { state: 'NOT_EXECUTED', reason: 'YAK_OPS_CROSS_PROJECT_ID not supplied' };
  if (CROSS_PROJECT_ID) {
    const productKey = `DATASET:${DATASET_ID}`;
    const result = await rawRequest(`/api/v1/consumption/products/${encodeURIComponent(productKey)}`, {
      projectId: CROSS_PROJECT_ID, session: ownerSession,
    });
    const detail = dataOf(result.payload);
    const masked = !result.ok || ['NOT_FOUND', 'NOT_DISCOVERABLE', 'FORBIDDEN'].includes(detail?.state);
    if (!masked) throw new Error(`cross-project Dataset ${productKey} was not masked/denied`);
    crossProject = {
      state: 'PASSED',
      requestProjectId: CROSS_PROJECT_ID,
      httpStatus: result.status,
      lookupState: detail?.state ?? null,
      message: safeMessage(result),
    };
  }

  let forbiddenDataset = {
    state: 'NOT_EXECUTED',
    reason: 'YAK_OPS_DENIED_USERNAME/YAK_OPS_DENIED_PASSWORD not supplied',
  };
  if (DENIED_USERNAME && DENIED_PASSWORD) {
    const deniedSession = await login(DENIED_USERNAME, DENIED_PASSWORD);
    const result = await rawRequest(`/api/v1/datasets/${DATASET_ID}/query`, {
      method: 'POST',
      body: { limit: 1 },
      projectId: PROJECT_ID,
      session: deniedSession,
    });
    if (result.status !== 403) {
      throw new Error(`low-permission Dataset query must return HTTP 403, got ${result.status}: ${safeMessage(result)}`);
    }
    forbiddenDataset = {
      state: 'PASSED',
      username: DENIED_USERNAME,
      httpStatus: result.status,
      message: safeMessage(result),
    };
  }

  console.log(JSON.stringify({
    probe: 'phase4-real-env-negative-evidence',
    acceptanceIssue: 104,
    projectId: PROJECT_ID,
    datasetId: DATASET_ID,
    dataServiceId: DATA_SERVICE_ID,
    invalidApiKey: {
      state: 'PASSED',
      httpStatus: invalidKey.status,
      message: safeMessage(invalidKey),
      credentialCaptured: false,
      yakLoginCookieSent: false,
      projectHeaderSent: false,
    },
    crossProject,
    forbiddenDataset,
    capturedAt: new Date().toISOString(),
  }, null, 2));
}

main().catch((error) => {
  console.error(`Phase4 negative evidence failed: ${error.message}`);
  process.exitCode = 1;
});
