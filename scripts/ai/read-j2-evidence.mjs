import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { digest } from './scenario-evidence.mjs';
import { readApiData } from './evidence-http.mjs';

const id = v => Number.isSafeInteger(v) && v > 0;
const nonempty = v => typeof v === 'string' && !!v.trim() && v.length <= 128;
const match = (actual, expected) => String(actual) === String(expected);
export function validateJ2Subject(v) {
  if (!v || v.version !== 'F-030-J2-v1' || !id(v.projectId) || !id(v.modelId) || !id(v.standardId)
      || !id(v.standardVersion) || !id(v.metricId) || !id(v.metricVersion) || !id(v.datasetId)
      || typeof v.columnName !== 'string' || !/^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$/.test(v.columnName)
      || !id(v.mapping?.datasourceId) || !['database', 'table', 'column'].every(k => nonempty(v.mapping[k]))) {
    throw new Error('INVALID_SUBJECT');
  }
  return v;
}

// Only compare current source evidence. These observations do not prove who performed this journey.
export function inspectJ2(subject, reads) {
  const s = validateJ2Subject(subject);
  const stages = [];
  const stage = (name, keys, condition) => {
    const unavailable = keys.filter(k => reads[k]?.status !== 'READ');
    stages.push({ name, status: unavailable.length ? 'UNAVAILABLE' : condition() ? 'SOURCE_MATCHED' : 'EVIDENCE_GAP',
      ...(unavailable.length ? { unavailable } : {}) });
  };
  const value = key => reads[key]?.data;
  const model = value('model'), structure = value('structure'), standard = value('standard');
  const fields = structure?.structure?.columns;
  stage('saved-model-standard', ['model', 'structure', 'standard'], () => match(model?.id, s.modelId)
    && model.layerCode === 'DWD' && id(model.domainId) && id(model.processId)
    && match(structure?.structure?.modelId, s.modelId) && Array.isArray(fields)
    && fields.filter(f => f?.columnName === s.columnName).length === 1
    && match(fields.find(f => f?.columnName === s.columnName)?.stdTypeId, s.standardId)
    && match(standard?.id, s.standardId) && match(standard?.version, s.standardVersion)
    && standard?.kind === 'TYPE' && standard.status === 'ENABLED');
  const mappings = value('mappings');
  stage('saved-source-mapping', ['mappings'], () => Array.isArray(mappings)
    && mappings.filter(m => m?.targetColumn === s.columnName).length === 1
    && mappings.some(m => m?.targetColumn === s.columnName && m.mapped === true
      && match(m.sourceDatasourceId, s.mapping.datasourceId) && m.sourceDatabase === s.mapping.database
      && m.sourceTable === s.mapping.table && m.sourceColumn === s.mapping.column));
  const metric = value('metric'), version = value('metricVersion');
  let snapshot;
  try { snapshot = JSON.parse(version?.snapshot); } catch { /* Missing immutable snapshot is a gap. */ }
  const versionMatches = match(version?.version, s.metricVersion) && id(version?.id)
    && version.editable === false && typeof version.snapshot === 'string' && !!snapshot;
  stage('saved-atomic-metric-model', ['model', 'metric', 'metricVersion'], () => match(metric?.id, s.metricId)
    && versionMatches && snapshot.metricType === 'ATOMIC' && match(snapshot.modelId, s.modelId)
    && id(model?.domainId) && id(model?.processId) && match(snapshot.domainId, model.domainId) && match(snapshot.processId, model.processId));
  const snapshotHash = versionMatches ? digest(version.snapshot) : null;
  const exact = v => snapshotHash != null && match(v?.metricId, s.metricId)
    && match(v?.metricVersion, s.metricVersion) && match(v?.metricVersionId, version.id) && v?.snapshotDigest === snapshotHash;
  const validation = value('validation'), publication = value('publication');
  stage('exact-version-validation', ['metricVersion', 'validation'], () => exact(validation)
    && id(validation.evidenceId) && validation.result === 'PASSED' && validation.providerState === 'READY');
  stage('active-exact-version-publication', ['metricVersion', 'publication'], () => exact(publication)
    && id(publication.publicationEventId) && publication.snapshot === version.snapshot);
  const references = value('references');
  stage('declared-exact-dataset-reference', ['metricVersion', 'publication', 'references'], () => exact(publication)
    && Array.isArray(references) && references.some(r => r?.usageType === 'DATASET'
      && match(r.usageId, s.datasetId) && match(r.metricVersion, s.metricVersion)));
  const product = value('product');
  stage('canonical-dataset-readable', ['product'], () => product?.state === 'FOUND'
    && product.product?.productKey?.productType === 'DATASET'
    && product.product.productKey.sourceIdentity === String(s.datasetId)
    && match(product.product.projectId, s.projectId));
  // ConsumerImpact is aggregate coverage, never proof of this run/query or exact MetricVersion execution.
  const impact = value('impact');
  const runtimeCoverage = reads.impact?.status !== 'READ' ? 'UNAVAILABLE'
    : impact?.productKey?.productType !== 'DATASET' || impact.productKey.sourceIdentity !== String(s.datasetId) ? 'IDENTITY_MISMATCH'
      : ['READY', 'EMPTY', 'FORBIDDEN', 'UNAVAILABLE'].includes(impact.usageState) ? impact.usageState : 'UNKNOWN';
  return { stages, runtimeCoverage, sourceStatus: stages.every(v => v.status === 'SOURCE_MATCHED') ? 'SOURCE_EVIDENCE_OBSERVED' : 'INCOMPLETE',
    journeyReview: 'PENDING', operationAudit: 'PENDING', exactQueryEvidence: 'PENDING', acceptanceStatus: 'PENDING_MANUAL_E2E' };
}

export async function collectJ2(subject, config, request = fetch) {
  const s = validateJ2Subject(subject);
  const routes = {
    model: `/api/v1/modeling/models/${s.modelId}`,
    structure: `/api/v1/modeling/models/${s.modelId}/structure/edit-context`,
    mappings: `/api/v1/modeling/models/${s.modelId}/mappings`,
    standard: `/api/v1/semantic/standards/${s.standardId}`,
    metric: `/api/v1/metrics/${s.metricId}`,
    metricVersion: `/api/v1/metrics/${s.metricId}/versions/${s.metricVersion}`,
    validation: `/api/v1/metrics/${s.metricId}/versions/${s.metricVersion}/validation/latest-ready`,
    publication: `/api/v1/metrics/${s.metricId}/publication`,
    references: `/api/v1/metrics/${s.metricId}/usage`,
    product: `/api/v1/consumption/products/${encodeURIComponent(`DATASET:${s.datasetId}`)}`,
    impact: `/api/v1/consumption/impact?productKey=${encodeURIComponent(`DATASET:${s.datasetId}`)}&usageLimit=200`,
  };
  const reads = {};
  for (const [key, route] of Object.entries(routes)) {
    try {
      const response = await request(new URL(route, config.baseUrl), { method: 'GET', redirect: 'error',
        headers: { ...config.authHeaders, 'X-YAK-SECURITY-PROJECT-ID': String(s.projectId) }, signal: AbortSignal.timeout(15000) });
      reads[key] = { status: 'READ', data: await readApiData(response) };
    } catch (error) {
      const code = /^(HTTP_\d+|API_REJECTED|RESPONSE_TOO_LARGE)$/.test(error.message) ? error.message : 'READ_ERROR';
      reads[key] = { status: 'UNAVAILABLE', code };
    }
  }
  return { ...inspectJ2(s, reads), subjectHash: digest(JSON.stringify(s)), projectId: s.projectId, modelId: s.modelId,
    standardId: s.standardId, standardVersion: s.standardVersion, metricId: s.metricId,
    metricVersion: s.metricVersion, productKey: `DATASET:${s.datasetId}`,
    reads: Object.fromEntries(Object.entries(reads).map(([key, v]) => [key, v.status === 'READ'
      ? { status: v.status, hash: digest(JSON.stringify(v.data ?? null)) } : v])) };
}

export async function main(args = process.argv.slice(2), env = process.env) {
  const option = key => args.find(v => v.startsWith(`${key}=`))?.slice(key.length + 1);
  const mode = option('--mode') ?? 'offline';
  if (!['offline', 'real'].includes(mode)) throw new Error('INVALID_MODE');
  let report = { mode, acceptanceStatus: 'NOT_RUN', journeyReview: 'PENDING', operationAudit: 'PENDING', exactQueryEvidence: 'PENDING' };
  if (mode === 'real') {
    if (!option('--subject') || !env.AI_EVAL_BASE_URL || !env.AI_EVAL_AUTH_HEADERS) throw new Error('MISSING_INPUTS');
    const base = new URL(env.AI_EVAL_BASE_URL), authHeaders = JSON.parse(env.AI_EVAL_AUTH_HEADERS);
    if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.pathname !== '/' || base.search || base.hash
        || !authHeaders || typeof authHeaders !== 'object' || Array.isArray(authHeaders)
        || Object.entries(authHeaders).some(([k, v]) => !['authorization', 'cookie'].includes(k.toLowerCase()) || typeof v !== 'string')) throw new Error('INVALID_CONNECTION');
    report = { mode, ...await collectJ2(JSON.parse(await readFile(resolve(option('--subject')), 'utf8')), { baseUrl: base.href, authHeaders }) };
  }
  const output = resolve(option('--out') ?? '.task-ai-evaluation/j2.json');
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, JSON.stringify({ ...report, createdAt: new Date().toISOString(), commit: env.AI_EVAL_COMMIT ?? 'UNKNOWN' }, null, 2) + '\n');
  console.log(`${mode}: J2 evidence report: ${output}`);
  // Source observations alone cannot close the manual E2E acceptance gate.
  if (mode === 'real') process.exitCode = 2;
  return report;
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  main().catch(() => { console.error('J2 evidence setup failed; check documented inputs.'); process.exitCode = 1; });
}
