import test from 'node:test';
import assert from 'node:assert/strict';
import { verifySourceUsagePair } from './p0-source-usage-evidence-contract.mjs';

function fixture() {
  const datasetGolden = {
    projectId: '42',
    dataset: { id: '88', currentVersionId: '9001', currentVersionNo: 12 },
    query: { result: { queryId: 'q001', datasetId: 88,
      datasetVersionId: 9001, datasetVersionNo: 12 } },
    queryPerformance: { queryId: 'q001', datasetId: 88,
      datasetVersionId: 9001, datasetVersionNo: 12, status: 'SUCCESS' },
  };
  const dataServiceGolden = {
    projectId: '42',
    dataService: { id: '77', sourceRevisionId: '9007199254740995', sourceRevisionNo: 7 },
    invocationRecord: { id: '9007199254740993', projectId: '42', apiId: '77',
      sourceRevisionId: '9007199254740995', sourceRevisionNo: 7,
      apiKeyId: '11', consumerId: '19', success: true },
    usageNormalization: {
      state: 'NORMALIZED', providerEvidenceRef: 'invocation:9007199254740993',
      evidence: { outcome: 'SUCCESS', projectId: '42',
        consumerRef: { sourceIdentity: '19' },
        sourceVersion: { identity: '9007199254740995' },
        providerEvidenceRef: 'invocation:9007199254740993' },
    },
  };
  const datasetConsumption = {
    canonical: { activeVersion: { identity: '9001' } },
    impact: { usageState: 'READY', consumers: [
      { providerEvidenceRefs: ['DATASET_QUERY_PERFORMANCE:query:q001'] },
    ] },
  };
  const serviceConsumption = {
    canonical: { activeVersion: { identity: '9007199254740995' } },
    impact: { usageState: 'READY', consumers: [
      { providerEvidenceRefs: ['DATA_SERVICE_INVOCATION:invocation:9007199254740993'] },
    ] },
  };
  return { datasetGolden, dataServiceGolden, datasetConsumption, serviceConsumption };
}

function inspect(fixtureValue) {
  return verifySourceUsagePair(...Object.values(fixtureValue));
}

test('exact source audit, consumer, version and both impact backlinks are accepted', () => {
  const data = fixture();
  assert.equal(inspect(data).dataServiceNormalizedUsageAndImpactLinked, true);
  assert.equal(inspect(data).datasetExactAuditAndVersionLinked, true);
});

test('a query with failed audit or mismatched pinned DatasetVersion is never success', () => {
  const bad = fixture();
  bad.datasetGolden.queryPerformance.status = 'FAILED';
  assert.throws(() => inspect(bad), /source audit must record real SUCCESS/);
  bad.datasetGolden.queryPerformance.status = 'SUCCESS';
  bad.datasetGolden.queryPerformance.datasetVersionId = '9002';
  assert.throws(() => inspect(bad), /source-version identity mismatch/);
});

test('source queryId must be tied to exact Dataset Impact evidence', () => {
  const bad = fixture();
  bad.datasetConsumption.impact.consumers[0].providerEvidenceRefs = ['DATASET_QUERY_PERFORMANCE:query:other'];
  assert.throws(() => inspect(bad), /Dataset impact lacks exact/);
});

test('BIGINT invocation ID and sourceRevision identity remain lossless strings', () => {
  const bad = fixture();
  bad.dataServiceGolden.usageNormalization.evidence.sourceVersion.identity = '9007199254740994';
  assert.throws(() => inspect(bad), /normalized Usage must preserve exact source revision/);
  bad.dataServiceGolden.usageNormalization.evidence.sourceVersion.identity = '9007199254740995';
  bad.dataServiceGolden.invocationRecord.id = '9007199254740994';
  assert.throws(() => inspect(bad), /exact invocation audit/);
});

test('a wrong Project, managed consumer or failed invocation never produces accepted Usage', () => {
  const bad = fixture();
  bad.dataServiceGolden.invocationRecord.projectId = '43';
  assert.throws(() => inspect(bad), /source Project\/product mismatch/);
  bad.dataServiceGolden.invocationRecord.projectId = '42';
  bad.dataServiceGolden.usageNormalization.evidence.consumerRef.sourceIdentity = '22';
  assert.throws(() => inspect(bad), /managed Consumer identity/);
  bad.dataServiceGolden.usageNormalization.evidence.consumerRef.sourceIdentity = '19';
  bad.dataServiceGolden.invocationRecord.success = false;
  assert.throws(() => inspect(bad), /durable successful audit/);
});

test('empty or unavailable Impact never masquerades as a successful backlink', () => {
  const bad = fixture();
  bad.serviceConsumption.impact.usageState = 'UNAVAILABLE';
  assert.throws(() => inspect(bad), /impact lacks exact persisted/);
});
