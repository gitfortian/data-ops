#!/usr/bin/env node

/**
 * Phase4 #104 one-command real-environment acceptance bundle.
 *
 * Reuses the source-owned Dataset/Data Service evidence runners, then captures
 * canonical Consumption Detail, stable source navigation and known Consumer/Impact
 * for both products. The output is a single reviewable JSON bundle.
 *
 * Required environment variables are the union of the two existing runners:
 *   YAK_OPS_USERNAME
 *   YAK_OPS_PASSWORD
 *   YAK_OPS_PROJECT_ID
 *   YAK_OPS_DATASET_ID
 *   YAK_OPS_DATA_SERVICE_ID
 *   YAK_OPS_DATA_SERVICE_API_KEY
 *
 * Optional:
 *   YAK_OPS_BASE_URL (default: http://localhost:9001)
 *   YAK_OPS_PUBLIC_BASE_URL
 *   YAK_OPS_ACCEPTANCE_COMMIT
 *   plus all optional variables supported by the two child evidence runners.
 */

import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const BASE_URL = (process.env.YAK_OPS_BASE_URL || 'http://localhost:9001').replace(/\/+$/, '');
const USERNAME = required('YAK_OPS_USERNAME');
const PASSWORD = required('YAK_OPS_PASSWORD');
const PROJECT_ID = positiveId('YAK_OPS_PROJECT_ID');
const DATASET_ID = positiveId('YAK_OPS_DATASET_ID');
const DATA_SERVICE_ID = positiveId('YAK_OPS_DATA_SERVICE_ID');
required('YAK_OPS_DATA_SERVICE_API_KEY');

const scriptDir = dirname(fileURLToPath(import.meta.url));
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

function runJsonScript(name) {
  const result = spawnSync(process.execPath, [join(scriptDir, name)], {
    env: process.env,
    encoding: 'utf8',
    maxBuffer: 16 * 1024 * 1024,
  });
  if (result.status !== 0) {
    const detail = (result.stderr || result.stdout || '').trim().slice(0, 2000);
    throw new Error(`${name} failed with exit ${result.status}: ${detail}`);
  }
  try {
    return JSON.parse(result.stdout);
  } catch {
    throw new Error(`${name} did not emit valid JSON`);
  }
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
  if (payload && typeof payload === 'object' && !Array.isArray(payload)
      && Object.prototype.hasOwnProperty.call(payload, 'data')) {
    return payload.data;
  }
  return payload;
}

function productKeyValue(value) {
  if (typeof value === 'string') return value;
  if (!value || typeof value !== 'object') return null;
  const type = value.productType ?? value.type;
  const identity = value.sourceIdentity ?? value.identity;
  return type && identity != null ? `${String(type).toUpperCase()}:${identity}` : null;
}

function canonicalSummary(detail) {
  const product = detail?.product ?? null;
  return {
    state: detail?.state ?? null,
    productKey: productKeyValue(product?.productKey),
    sourceRef: product?.sourceRef ?? null,
    activeVersion: product?.activeVersion ?? null,
    lifecycle: product?.lifecycle ?? null,
    availability: product?.availability ?? null,
    access: product?.access ?? null,
    navigation: detail?.navigation ?? null,
    governanceEvidence: detail?.governanceEvidence ?? [],
    reason: detail?.reason ?? null,
  };
}

function impactSummary(view) {
  return {
    productKey: productKeyValue(view?.productKey),
    subscriptionState: view?.subscriptionState ?? null,
    usageState: view?.usageState ?? null,
    consumers: Array.isArray(view?.consumers) ? view.consumers : [],
    coverageNote: view?.coverageNote ?? null,
  };
}

function assert(condition, message) {
  if (!condition) throw new Error(`acceptance assertion failed: ${message}`);
}

async function captureConsumption(productType, sourceId, expectedKey) {
  const detail = dataOf(await request(
    `/api/v1/consumption/products/${encodeURIComponent(expectedKey)}`,
    { projectScoped: true },
  ));
  const navigation = dataOf(await request(
    `/api/v1/consumption/navigation/sources/${encodeURIComponent(productType)}/${encodeURIComponent(sourceId)}`,
    { projectScoped: true },
  ));
  const impact = dataOf(await request(
    `/api/v1/consumption/impact?productKey=${encodeURIComponent(expectedKey)}&usageLimit=200`,
    { projectScoped: true },
  ));

  const canonical = canonicalSummary(detail);
  const impactView = impactSummary(impact);
  assert(canonical.state === 'FOUND', `${expectedKey} canonical detail state must be FOUND`);
  assert(canonical.productKey === expectedKey, `${expectedKey} canonical detail must preserve ProductKey`);
  assert(impactView.productKey === expectedKey, `${expectedKey} impact must preserve ProductKey`);
  assert(navigation != null, `${expectedKey} source navigation must resolve`);

  return { canonical, sourceNavigation: navigation, impact: impactView };
}

async function main() {
  const datasetGolden = runJsonScript('phase4-real-env-dataset-evidence.mjs');
  const dataServiceGolden = runJsonScript('phase4-real-env-data-service-evidence.mjs');

  const datasetKey = `DATASET:${DATASET_ID}`;
  const dataServiceKey = `DATA_SERVICE:${DATA_SERVICE_ID}`;
  assert(datasetGolden.productKey === datasetKey, 'Dataset evidence ProductKey mismatch');
  assert(dataServiceGolden.productKey === dataServiceKey, 'Data Service evidence ProductKey mismatch');
  assert(dataServiceGolden.publicInvocation?.request?.auth?.yakLoginCookieSent === false,
    'Data Service public invoke must not send Yak login cookie');
  assert(dataServiceGolden.publicInvocation?.request?.auth?.projectHeaderSent === false,
    'Data Service public invoke must not send Project header');
  assert(dataServiceGolden.usageNormalization?.state === 'NORMALIZED',
    'Data Service InvocationRecord must normalize to Usage Evidence');

  await request('/yak-security/api/v1/account/login', {
    method: 'POST',
    body: { userName: USERNAME, pw: PASSWORD },
  });
  const currentUser = dataOf(await request('/yak-security/api/v1/account/current'));

  const datasetConsumption = await captureConsumption('DATASET', DATASET_ID, datasetKey);
  const dataServiceConsumption = await captureConsumption('DATA_SERVICE', DATA_SERVICE_ID, dataServiceKey);

  const bundle = {
    probe: 'phase4-real-env-acceptance',
    acceptanceIssue: 104,
    phase: 4,
    commit: process.env.YAK_OPS_ACCEPTANCE_COMMIT?.trim() || null,
    baseUrl: BASE_URL,
    projectId: PROJECT_ID,
    authenticatedUser: {
      id: currentUser?.id ?? currentUser?.userId ?? null,
      username: currentUser?.userName ?? currentUser?.username ?? null,
    },
    dataset: {
      golden: datasetGolden,
      consumption: datasetConsumption,
    },
    dataService: {
      golden: dataServiceGolden,
      consumption: dataServiceConsumption,
    },
    assertions: {
      datasetRealQueryEvidence: true,
      dataServicePublicInvokeEvidence: true,
      dataServiceExternalAuthorizationBoundary: true,
      dataServiceUsageNormalized: true,
      canonicalProductIdentityStable: true,
      sourceNavigationResolved: true,
      consumerImpactCaptured: true,
    },
    capturedAt: new Date().toISOString(),
  };

  console.log(JSON.stringify(bundle, null, 2));
}

main().catch((error) => {
  console.error(`Phase4 real-environment acceptance failed: ${error.message}`);
  process.exitCode = 1;
});
