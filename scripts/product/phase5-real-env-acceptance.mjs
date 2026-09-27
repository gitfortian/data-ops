#!/usr/bin/env node

/**
 * Phase 5 / #124 one-command real-environment Metric acceptance bundle.
 *
 * Required:
 *   YAK_OPS_USERNAME
 *   YAK_OPS_PASSWORD
 *   YAK_OPS_PROJECT_ID
 *   YAK_OPS_METRIC_ID
 *
 * Optional:
 *   YAK_OPS_BASE_URL (default: http://localhost:9001)
 *   YAK_OPS_PHASE5_METRIC_VERSION (default: current Metric version)
 *   YAK_OPS_PHASE5_VALIDATE=true   -> append real Definition Validation evidence before asserting Golden state
 *   YAK_OPS_PHASE5_PUBLISH=true    -> publish exact current version, only when readiness=READY
 *   YAK_OPS_PHASE5_REQUIRE_OBSERVED_USAGE=true|false (default: true)
 *   YAK_OPS_CROSS_PROJECT_ID       -> safe read-isolation probe
 *   YAK_OPS_DENIED_USERNAME / YAK_OPS_DENIED_PASSWORD -> safe metric:read denial probe
 *   YAK_OPS_PHASE5_RUN_DATASET_EVIDENCE=true -> reuse Phase4 Dataset evidence runner when its env is supplied
 *   YAK_OPS_ACCEPTANCE_COMMIT
 *
 * The runner never automatically withdraws, edits the Metric, removes dependencies,
 * creates stale versions, or disables providers. Those destructive/controlled failure
 * scenarios are reported as manual fault-injection requirements instead of fabricated evidence.
 */

import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const PROJECT_HEADER = 'X-YAK-SECURITY-PROJECT-ID';
const BASE_URL = (process.env.YAK_OPS_BASE_URL || 'http://localhost:9001').replace(/\/+$/, '');
const USERNAME = required('YAK_OPS_USERNAME');
const PASSWORD = required('YAK_OPS_PASSWORD');
const PROJECT_ID = positiveId('YAK_OPS_PROJECT_ID');
const METRIC_ID = positiveId('YAK_OPS_METRIC_ID');
const VALIDATE = boolEnv('YAK_OPS_PHASE5_VALIDATE');
const PUBLISH = boolEnv('YAK_OPS_PHASE5_PUBLISH');
const REQUIRE_OBSERVED_USAGE = boolEnv('YAK_OPS_PHASE5_REQUIRE_OBSERVED_USAGE', true);
const RUN_DATASET_EVIDENCE = boolEnv('YAK_OPS_PHASE5_RUN_DATASET_EVIDENCE');
const scriptDir = dirname(fileURLToPath(import.meta.url));

function required(name) {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function positiveId(name, optional = false) {
  const value = process.env[name]?.trim();
  if (!value && optional) return null;
  if (!value) throw new Error(`${name} is required`);
  if (!/^\d+$/.test(value) || Number(value) <= 0 || !Number.isSafeInteger(Number(value))) {
    throw new Error(`${name} must be a positive integer`);
  }
  return value;
}

function boolEnv(name, defaultValue = false) {
  const value = process.env[name]?.trim().toLowerCase();
  if (!value) return defaultValue;
  if (['1', 'true', 'yes', 'on'].includes(value)) return true;
  if (['0', 'false', 'no', 'off'].includes(value)) return false;
  throw new Error(`${name} must be true/false`);
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

async function request(session, path, {
  method = 'GET',
  body,
  projectId = PROJECT_ID,
  allowFailure = false,
} = {}) {
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
  const result = {
    ok: response.ok,
    status: response.status,
    data: dataOf(payload),
    message: payload?.message ?? payload?.msg ?? null,
  };
  if (!response.ok && !allowFailure) {
    throw new Error(`${method} ${path} failed with HTTP ${response.status}: ${safeSummary(payload, text)}`);
  }
  return result;
}

async function login(userName, password) {
  const session = makeSession();
  await request(session, '/yak-security/api/v1/account/login', {
    method: 'POST',
    body: { userName, pw: password },
    projectId: null,
  });
  const current = await request(session, '/yak-security/api/v1/account/current', { projectId: null });
  return { session, currentUser: current.data };
}

function assert(condition, message) {
  if (!condition) throw new Error(`acceptance assertion failed: ${message}`);
}

function compactMetric(metric) {
  if (!metric) return null;
  return {
    id: metric.id ?? null,
    metricCode: metric.metricCode ?? null,
    metricName: metric.metricName ?? null,
    metricType: metric.metricType ?? null,
    version: metric.version ?? null,
    domainId: metric.domainId ?? null,
    processId: metric.processId ?? null,
    caliberId: metric.caliberId ?? null,
    unitId: metric.unitId ?? null,
    modelId: metric.modelId ?? null,
    refMetricId: metric.refMetricId ?? null,
    dependencyChanges: metric.dependencyChanges ?? [],
    authoringNextStep: metric.authoringNextStep ?? null,
  };
}

function compactVersion(version) {
  if (!version) return null;
  return {
    id: version.id ?? null,
    version: version.version ?? null,
    changeDesc: version.changeDesc ?? null,
    changedBy: version.changedBy ?? null,
    createTime: version.createTime ?? null,
    versionViewType: version.versionViewType ?? null,
    editable: version.editable ?? null,
    snapshotPresent: typeof version.snapshot === 'string' && version.snapshot.length > 0,
  };
}

function compactValidation(evidence) {
  if (!evidence) return null;
  return {
    evidenceId: evidence.evidenceId ?? null,
    metricId: evidence.metricId ?? null,
    metricVersionId: evidence.metricVersionId ?? null,
    metricVersion: evidence.metricVersion ?? null,
    result: evidence.result ?? null,
    provider: evidence.provider ?? null,
    snapshotDigest: evidence.snapshotDigest ?? null,
    issues: evidence.issues ?? [],
    checkedBy: evidence.checkedBy ?? null,
    checkedAt: evidence.checkedAt ?? null,
  };
}

function compactReadiness(readiness) {
  if (!readiness) return null;
  return {
    status: readiness.status ?? null,
    subject: readiness.subject ?? null,
    gates: readiness.gates ?? [],
  };
}

function compactPublication(contract) {
  if (!contract) return null;
  return {
    publicationEventId: contract.publicationEventId ?? null,
    metricId: contract.metricId ?? null,
    metricVersionId: contract.metricVersionId ?? null,
    metricVersion: contract.metricVersion ?? null,
    snapshotDigest: contract.snapshotDigest ?? null,
    publicationEvidence: contract.publicationEvidence ?? [],
    publishedBy: contract.publishedBy ?? null,
    publishedAt: contract.publishedAt ?? null,
    snapshotPresent: typeof contract.snapshot === 'string' && contract.snapshot.length > 0,
  };
}

function compactImpact(context) {
  if (!context) return null;
  return {
    metricId: context.metricId ?? null,
    metricCode: context.metricCode ?? null,
    metricName: context.metricName ?? null,
    metricVersion: context.metricVersion ?? null,
    dependencies: context.dependencies ?? [],
    lineage: context.lineage ?? null,
    referenceUsage: context.referenceUsage ?? [],
    observedUsage: context.observedUsage ?? [],
    generatedAt: context.generatedAt ?? null,
  };
}

function runPhase4DatasetEvidence() {
  const result = spawnSync(process.execPath, [join(scriptDir, 'phase4-real-env-dataset-evidence.mjs')], {
    env: process.env,
    encoding: 'utf8',
    maxBuffer: 16 * 1024 * 1024,
  });
  if (result.status !== 0) {
    const detail = (result.stderr || result.stdout || '').trim().slice(0, 2000);
    throw new Error(`phase4-real-env-dataset-evidence.mjs failed: ${detail}`);
  }
  try {
    return JSON.parse(result.stdout);
  } catch {
    throw new Error('phase4-real-env-dataset-evidence.mjs did not emit valid JSON');
  }
}

async function main() {
  const { session, currentUser } = await login(USERNAME, PASSWORD);
  assert(currentUser != null, 'logged-in current user must resolve');

  const metricResponse = await request(session, `/api/v1/metrics/${METRIC_ID}`);
  const metric = metricResponse.data;
  assert(metric != null, 'Metric canonical detail must resolve');
  assert(String(metric.id) === String(METRIC_ID), 'Metric canonical identity mismatch');

  const configuredVersion = positiveId('YAK_OPS_PHASE5_METRIC_VERSION', true);
  const version = configuredVersion ? Number(configuredVersion) : Number(metric.version);
  assert(Number.isSafeInteger(version) && version > 0, 'Metric current/exact version must be positive');

  const exactVersion = (await request(
    session, `/api/v1/metrics/${METRIC_ID}/versions/${version}`,
  )).data;
  assert(exactVersion != null, `MetricVersion v${version} must resolve`);
  assert(Number(exactVersion.version) === version, 'MetricVersion identity mismatch');
  assert(exactVersion.editable === false, 'Historical MetricVersion must be immutable/read-only');
  assert(typeof exactVersion.snapshot === 'string' && exactVersion.snapshot.length > 0,
    'MetricVersion must carry immutable snapshot evidence');

  let validationAction = null;
  if (VALIDATE) {
    validationAction = (await request(
      session,
      `/api/v1/metrics/${METRIC_ID}/versions/${version}/validation`,
      { method: 'POST' },
    )).data;
    assert(Number(validationAction?.metricVersion) === version, 'Validation evidence must bind exact version');
  }

  const validationHistory = (await request(
    session, `/api/v1/metrics/${METRIC_ID}/versions/${version}/validations`,
  )).data ?? [];
  const latestReady = (await request(
    session,
    `/api/v1/metrics/${METRIC_ID}/versions/${version}/validation/latest-ready`,
    { allowFailure: true },
  )).data ?? null;
  assert(latestReady?.result === 'READY',
    'Golden Metric requires latest READY validation evidence for the exact version');
  assert(Number(latestReady.metricVersion) === version,
    'latest READY validation evidence must bind the exact MetricVersion');

  const readiness = (await request(
    session, `/api/v1/metrics/${METRIC_ID}/versions/${version}/publication-readiness`,
  )).data;
  assert(readiness?.status === 'READY',
    `Golden Metric publication readiness must be READY, got ${readiness?.status ?? 'null'}`);
  assert(Number(readiness?.subject?.metricVersion) === version,
    'publication readiness subject must bind the exact MetricVersion');

  let publishAction = null;
  if (PUBLISH) {
    assert(Number(metric.version) === version,
      'Publish opt-in only accepts the current Metric version; use a dedicated Golden Metric fixture');
    publishAction = (await request(
      session,
      `/api/v1/metrics/${METRIC_ID}/versions/${version}/publish`,
      { method: 'POST' },
    )).data;
    assert(Number(publishAction?.metricVersion) === version, 'Published contract must bind exact version');
  }

  const activePublication = (await request(
    session, `/api/v1/metrics/${METRIC_ID}/publication`,
  )).data ?? null;
  const publicationHistory = (await request(
    session, `/api/v1/metrics/${METRIC_ID}/publication-history`,
  )).data ?? [];
  assert(activePublication != null, 'Golden Metric requires an active Published Metric Contract');
  assert(Number(activePublication.metricId) === Number(METRIC_ID),
    'Active publication must preserve Metric identity');
  assert(Number(activePublication.metricVersion) === version,
    'Active Published Metric Contract must bind the accepted exact version');
  assert(activePublication.metricVersionId != null,
    'Active publication must preserve immutable MetricVersion id');
  assert(activePublication.snapshotDigest,
    'Active publication must preserve immutable snapshot digest');
  assert(publicationHistory.some((event) => event.eventType === 'PUBLISHED'
      && Number(event.metricVersion) === version),
    'Publication ledger must contain a PUBLISHED event for the exact version');

  const usageSummary = (await request(
    session, `/api/v1/metrics/${METRIC_ID}/usage/summary`,
  )).data;
  const referenceUsage = (await request(
    session, `/api/v1/metrics/${METRIC_ID}/usage`,
  )).data ?? [];
  const impact = (await request(
    session, `/api/v1/metrics/impact/${METRIC_ID}/context`,
  )).data;

  assert(referenceUsage.length > 0,
    'Golden Metric requires at least one real downstream Reference Usage');
  assert(Number(impact?.metricId) === Number(METRIC_ID), 'Impact Context must preserve Metric identity');
  assert(Number(impact?.metricVersion) === Number(metric.version),
    'Impact Context must report current Metric version');
  assert(impact?.lineage != null, 'Impact Context must expose Lineage coverage');
  assert(impact.lineage.status === 'READY',
    `Golden Metric requires readable Lineage evidence, got ${impact.lineage.status ?? 'null'}`);
  assert(Array.isArray(impact?.referenceUsage), 'Impact Context must expose Reference Usage separately');
  assert(impact.referenceUsage.length > 0,
    'Impact Context must preserve real downstream Reference Usage');
  assert(Array.isArray(impact?.observedUsage), 'Impact Context must expose Observed Runtime Usage separately');
  const readyObservedProviders = impact.observedUsage.filter((coverage) => coverage.status === 'READY');
  if (REQUIRE_OBSERVED_USAGE) {
    assert(readyObservedProviders.some((coverage) => Array.isArray(coverage.evidence) && coverage.evidence.length > 0),
      'Golden Metric requires at least one provider with real Observed Runtime Usage evidence');
  }

  const crossProjectId = positiveId('YAK_OPS_CROSS_PROJECT_ID', true);
  let crossProject = { state: 'SKIPPED', reason: 'YAK_OPS_CROSS_PROJECT_ID not supplied' };
  if (crossProjectId) {
    const probe = await request(
      session, `/api/v1/metrics/${METRIC_ID}`,
      { projectId: crossProjectId, allowFailure: true },
    );
    const leakedSameMetric = probe.ok
      && Number(probe.data?.id) === Number(METRIC_ID)
      && probe.data?.metricCode === metric.metricCode;
    assert(!leakedSameMetric, 'cross-Project probe exposed the same Metric truth');
    crossProject = {
      state: 'PASSED',
      projectId: crossProjectId,
      httpStatus: probe.status,
      resolvedDifferentResource: probe.ok && probe.data != null,
    };
  }

  let forbiddenRead = {
    state: 'SKIPPED',
    reason: 'YAK_OPS_DENIED_USERNAME / YAK_OPS_DENIED_PASSWORD not supplied',
  };
  const deniedUser = process.env.YAK_OPS_DENIED_USERNAME?.trim();
  const deniedPassword = process.env.YAK_OPS_DENIED_PASSWORD?.trim();
  if (deniedUser && deniedPassword) {
    const denied = await login(deniedUser, deniedPassword);
    const probe = await request(
      denied.session, `/api/v1/metrics/${METRIC_ID}`,
      { allowFailure: true },
    );
    assert(!probe.ok, 'denied user unexpectedly read Metric canonical detail');
    forbiddenRead = { state: 'PASSED', httpStatus: probe.status };
  }

  let datasetGolden = null;
  if (RUN_DATASET_EVIDENCE) {
    datasetGolden = runPhase4DatasetEvidence();
    const datasetRefs = (impact.referenceUsage ?? []).filter((item) => item.usageType === 'DATASET');
    assert(datasetRefs.length > 0,
      'Phase4 Dataset runtime evidence was requested but this Metric has no DATASET Reference Usage');
    const expectedKeys = datasetRefs.map((item) => `DATASET:${item.usageId}`);
    assert(expectedKeys.includes(datasetGolden?.productKey),
      'Phase4 Dataset evidence must correspond to a Dataset realization referenced by this Metric');
  }

  const bundle = {
    probe: 'phase5-real-env-acceptance',
    acceptanceIssue: 124,
    phase: 5,
    commit: process.env.YAK_OPS_ACCEPTANCE_COMMIT?.trim() || null,
    baseUrl: BASE_URL,
    projectId: PROJECT_ID,
    authenticatedUser: {
      id: currentUser?.id ?? currentUser?.userId ?? null,
      username: currentUser?.userName ?? currentUser?.username ?? null,
    },
    subject: {
      metric: compactMetric(metric),
      exactVersion: compactVersion(exactVersion),
      stableBacklinks: {
        domain: metric.domainId ? `/semantic/domains?domainId=${metric.domainId}` : null,
        process: metric.processId
          ? `/semantic/processes?processId=${metric.processId}${metric.domainId ? `&domainId=${metric.domainId}` : ''}`
          : null,
        caliber: metric.caliberId ? `/semantic/standards?standardId=${metric.caliberId}` : null,
        unit: metric.unitId ? `/semantic/standards?standardId=${metric.unitId}` : null,
        model: metric.modelId ? `/modeling/models/${metric.modelId}` : null,
        impact: `/metric/impact?metricId=${METRIC_ID}`,
        lineage: `/data-analysis/lineage?metricId=${METRIC_ID}&direction=UPSTREAM`,
      },
    },
    validation: {
      mutationOptIn: VALIDATE,
      action: compactValidation(validationAction),
      latestReady: compactValidation(latestReady),
      history: validationHistory.map(compactValidation),
    },
    publication: {
      mutationOptIn: PUBLISH,
      readiness: compactReadiness(readiness),
      action: compactPublication(publishAction),
      active: compactPublication(activePublication),
      history: publicationHistory,
    },
    usageAndImpact: {
      usageSummary,
      referenceUsage,
      impact: compactImpact(impact),
      phase4DatasetGolden: datasetGolden,
    },
    negativeEvidence: {
      crossProject,
      forbiddenRead,
    },
    assertions: {
      loggedInRealUser: true,
      canonicalMetricResolved: true,
      exactImmutableMetricVersionResolved: true,
      immutableVersionReadOnly: true,
      latestReadyValidationExactVersion: true,
      publicationReadinessReady: true,
      activePublicationExactVersion: true,
      publicationLedgerCaptured: true,
      referenceUsagePresent: true,
      lineageCoverageReady: true,
      observedUsageCoverageCaptured: true,
      observedRuntimeEvidenceRequired: REQUIRE_OBSERVED_USAGE,
      observedRuntimeEvidencePresent: readyObservedProviders.some(
        (coverage) => Array.isArray(coverage.evidence) && coverage.evidence.length > 0),
      impactIdentityStable: true,
      crossProjectChecked: crossProject.state === 'PASSED',
      forbiddenReadChecked: forbiddenRead.state === 'PASSED',
      phase4DatasetRuntimeChecked: Boolean(datasetGolden),
    },
    remainingManualFaultInjection: [
      'remove/outdate a dependency and capture BLOCKED authoring/publication evidence',
      'make validation/publication gate provider unavailable and capture fail-closed UNAVAILABLE evidence',
      'attempt stale-version publish on a dedicated disposable Metric and capture PUBLICATION_CONFLICT',
      'edit Draft after publication and prove active Published Metric Contract remains on the immutable published version',
      'make Metric Reference Usage provider/path unavailable without rewriting it as zero',
      'make Phase4 UsageEvidence provider unavailable and distinguish it from EMPTY/evidence gap',
      'make Lineage provider unavailable and distinguish it from EMPTY/no direct relation',
      'audit-event review for Validation/Publish/Withdraw/lifecycle operations',
      'DB migration rollback/upgrade review and external API compatibility review',
    ],
    safety: {
      validationMutationExecuted: VALIDATE,
      publicationMutationExecuted: PUBLISH,
      withdrawalExecuted: false,
      metricEditExecuted: false,
      dependencyMutationExecuted: false,
      providerFaultInjected: false,
      credentialsEmitted: false,
    },
    capturedAt: new Date().toISOString(),
  };

  console.log(JSON.stringify(bundle, null, 2));
}

main().catch((error) => {
  console.error(`Phase5 real-environment acceptance failed: ${error.message}`);
  process.exitCode = 1;
});
