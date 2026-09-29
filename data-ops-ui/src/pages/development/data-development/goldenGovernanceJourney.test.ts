import { datasetAssetDetailUrl, datasetIdFromSearch } from './assetGovernance';
import { developmentNodeIdFromSearch, developmentNodeUrl } from './executions/executionExperience';
import {
  canOpenLineageEvidence,
  lineageEvidenceUrl,
  type DevelopmentLineageEvidence,
} from './governanceEvidence';
import { releaseNodeUrl } from './releases/releaseExperience';

const succeededEvidence: DevelopmentLineageEvidence = {
  nodeId: '42',
  revisionId: '9001',
  revisionNo: 3,
  status: 'SUCCEEDED',
  lineageAssetKey: 'sql-task:data-development:42',
  attempts: 1,
};

describe('F-002 Golden SQL governance journey', () => {
  it('keeps the exact Development Node identity through execution and release backlinks', () => {
    expect(developmentNodeUrl(42)).toBe('/data-development?nodeId=42');
    expect(releaseNodeUrl(42)).toBe('/data-development?nodeId=42');
    expect(developmentNodeIdFromSearch('?nodeId=42')).toBe('42');
  });

  it('opens canonical lineage only after durable evidence succeeds and preserves return context', () => {
    expect(canOpenLineageEvidence(succeededEvidence)).toBe(true);
    const url = lineageEvidenceUrl(succeededEvidence, '42');
    expect(url).toContain('assetKey=sql-task%3Adata-development%3A42');
    expect(url).toContain('returnTo=%2Fdata-development%3FnodeId%3D42');

    expect(canOpenLineageEvidence({ ...succeededEvidence, status: 'FAILED' })).toBe(false);
    expect(canOpenLineageEvidence({ ...succeededEvidence, status: 'UNAVAILABLE' })).toBe(false);
  });

  it('uses stable Dataset and Asset identities for the governance round trip', () => {
    expect(datasetAssetDetailUrl({
      state: 'FOUND',
      sourceType: 'DATASET',
      sourceId: '55',
      assetId: 700,
      assetKey: 'dataset:55',
      assetType: 'DATASET',
      assetStatus: 'ACTIVE',
    })).toBe('/data-asset/detail/700');

    expect(datasetAssetDetailUrl({
      state: 'NOT_INDEXED',
      sourceType: 'DATASET',
      sourceId: '55',
    })).toBeUndefined();

    expect(datasetIdFromSearch('?datasetId=55')).toBe('55');
  });

  it('never turns downstream uncertainty into an empty/negative governance fact', () => {
    expect(canOpenLineageEvidence({
      ...succeededEvidence,
      status: 'UNAVAILABLE',
    })).toBe(false);
    expect(datasetAssetDetailUrl({
      state: 'NOT_INDEXED',
      sourceType: 'DATASET',
      sourceId: '55',
    })).toBeUndefined();
  });
});
