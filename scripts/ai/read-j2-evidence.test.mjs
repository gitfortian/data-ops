import test from 'node:test';
import assert from 'node:assert/strict';
import { inspectJ2, collectJ2, validateJ2Subject } from './read-j2-evidence.mjs';
import { digest } from './scenario-evidence.mjs';

const subject = { version: 'F-030-J2-v1', projectId: 1, modelId: 10, columnName: 'customer_id', standardId: 20,
  standardVersion: 2, metricId: 30, metricVersion: 3, datasetId: 40,
  mapping: { datasourceId: 50, database: 'private-db', table: 'private-table', column: 'id' } };
const snapshot = JSON.stringify({ metricType: 'ATOMIC', modelId: 10, domainId: 11, processId: 12, businessDesc: 'private-description' });
const exact = { metricId: 30, metricVersion: 3, metricVersionId: 31, snapshotDigest: digest(snapshot) };
const data = () => ({
  model: { id: 10, layerCode: 'DWD', domainId: 11, processId: 12 }, structure: { structure: { modelId: 10, columns: [{ columnName: 'customer_id', stdTypeId: 20 }] } },
  standard: { id: 20, version: 2, kind: 'TYPE', status: 'ENABLED' },
  mappings: [{ targetColumn: 'customer_id', mapped: true, sourceDatasourceId: 50, sourceDatabase: 'private-db', sourceTable: 'private-table', sourceColumn: 'id' }],
  metric: { id: 30, version: 4, modelId: 999 }, // Current draft must not replace the accepted snapshot.
  metricVersion: { id: 31, version: 3, snapshot, editable: false },
  validation: { ...exact, evidenceId: 32, result: 'PASSED', providerState: 'READY' },
  publication: { ...exact, publicationEventId: 33, snapshot },
  references: [{ usageType: 'DATASET', usageId: 40, metricVersion: 3 }],
  product: { state: 'FOUND', product: { productKey: { productType: 'DATASET', sourceIdentity: '40' }, projectId: 1 } },
  impact: { productKey: { productType: 'DATASET', sourceIdentity: '40' }, usageState: 'READY', consumers: [{ successfulUsageCount: 8 }] },
});
const reads = values => Object.fromEntries(Object.entries(values).map(([key, value]) => [key, { status: 'READ', data: value }]));

test('J2 compares saved references and immutable exact version but leaves journey/audit/query pending', () => {
  const result = inspectJ2(subject, reads(data()));
  assert.equal(result.sourceStatus, 'SOURCE_EVIDENCE_OBSERVED');
  assert.equal(result.runtimeCoverage, 'READY');
  assert.equal(result.acceptanceStatus, 'PENDING_MANUAL_E2E');
  assert.equal(result.exactQueryEvidence, 'PENDING');
  assert.equal(result.operationAudit, 'PENDING');
});

test('J2 never turns wrong identities, unknown versions, digest drift or unavailable providers into matches', () => {
  const mutations = [
    d => { d.model.layerCode = 'ODS'; },
    d => { d.model.processId = 99; },
    d => { d.structure.structure.columns[0].stdTypeId = 21; },
    d => { d.standard.version = 3; },
    d => { d.mappings[0].sourceColumn = 'different'; },
    d => { d.metricVersion.snapshot = JSON.stringify({ metricType: 'ATOMIC', modelId: 999 }); },
    d => { d.metricVersion.editable = true; },
    d => { d.validation.metricVersionId = 99; },
    d => { d.validation.snapshotDigest = 'x'; },
    d => { d.publication.metricVersion = 4; },
    d => { d.publication.snapshot = '{}'; },
    d => { d.references[0].metricVersion = null; },
    d => { d.product.product.projectId = 2; },
  ];
  for (const mutate of mutations) {
    const values = data(); mutate(values);
    assert.equal(inspectJ2(subject, reads(values)).sourceStatus, 'INCOMPLETE');
  }
  const unavailable = reads(data()); unavailable.validation = { status: 'UNAVAILABLE', code: 'HTTP_403' };
  assert.equal(inspectJ2(subject, unavailable).stages.find(v => v.name === 'exact-version-validation').status, 'UNAVAILABLE');
  for (const state of ['EMPTY', 'UNAVAILABLE', 'FORBIDDEN']) {
    const values = data(); values.impact.usageState = state;
    assert.equal(inspectJ2(subject, reads(values)).runtimeCoverage, state);
  }
  const values = data(); values.impact.productKey.sourceIdentity = '999';
  assert.equal(inspectJ2(subject, reads(values)).runtimeCoverage, 'IDENTITY_MISMATCH');
});

test('J2 collector uses only fixed project-scoped GET routes, hashes private data and reports unavailable reads', async () => {
  const d = data(), calls = [];
  const values = [d.model, d.structure, d.mappings, d.standard, d.metric, d.metricVersion,
    d.validation, d.publication, d.references, d.product, d.impact];
  const result = await collectJ2(subject, { baseUrl: 'https://fixture.invalid', authHeaders: { Cookie: 'private-token' } }, async (url, options) => {
    calls.push({ url, options });
    return new Response(JSON.stringify({ code: 200, data: values[calls.length - 1] }));
  });
  assert.equal(calls.length, 11);
  assert.ok(calls.every(v => v.options.method === 'GET' && v.options.headers['X-YAK-SECURITY-PROJECT-ID'] === '1' && v.options.redirect === 'error'));
  assert.equal(result.sourceStatus, 'SOURCE_EVIDENCE_OBSERVED');
  assert.ok(!JSON.stringify(result).includes('private-'));
  const failed = await collectJ2(subject, { baseUrl: 'https://fixture.invalid', authHeaders: {} }, async () => new Response('private-error', { status: 403 }));
  assert.equal(failed.sourceStatus, 'INCOMPLETE');
  assert.ok(Object.values(failed.reads).every(v => v.code === 'HTTP_403'));
  assert.ok(!JSON.stringify(failed).includes('private-error'));
});

test('invalid J2 subject cannot issue a request or inject a route', async () => {
  assert.throws(() => validateJ2Subject({ ...subject, metricId: '../../credentials' }));
  assert.throws(() => validateJ2Subject({ ...subject, projectId: -1 }));
  assert.throws(() => validateJ2Subject({ ...subject, columnName: undefined }));
  let calls = 0;
  await assert.rejects(collectJ2({ ...subject, modelId: null }, {}, async () => { calls++; }));
  assert.equal(calls, 0);
});
