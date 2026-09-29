import {
  datasetAssetDetailUrl,
  datasetAssetGovernanceLabel,
  datasetIdFromSearch,
  datasetVersionNoFromSearch,
  type AssetSourceLookup,
} from './assetGovernance';

const found: AssetSourceLookup = {
  state: 'FOUND',
  sourceType: 'DATASET',
  sourceId: '55',
  assetId: 101,
  assetKey: 'dataset:55',
  assetType: 'DATASET',
  assetStatus: 'PUBLISHED',
};

describe('dataset asset governance projection', () => {
  it('opens the exact Asset Registry record only when lookup found it', () => {
    expect(datasetAssetDetailUrl(found)).toBe('/data-asset/detail/101');
    expect(datasetAssetDetailUrl({
      state: 'NOT_INDEXED',
      sourceType: 'DATASET',
      sourceId: '55',
    })).toBeUndefined();
  });

  it('keeps the stable Dataset identity in the Asset-to-Development deep link', () => {
    expect(datasetIdFromSearch('?datasetId=55')).toBe('55');
    expect(datasetIdFromSearch('?nodeId=7')).toBeUndefined();
    expect(datasetVersionNoFromSearch('?datasetId=55&datasetVersionNo=3')).toBe(3);
    expect(datasetVersionNoFromSearch('?datasetVersionNo=0')).toBeUndefined();
    expect(datasetVersionNoFromSearch('?datasetVersionNo=abc')).toBeUndefined();
  });

  it('keeps not-indexed, permission and unavailable distinct', () => {
    expect(datasetAssetGovernanceLabel('NOT_INDEXED')).toBe('Asset · 等待 Registry 对账');
    expect(datasetAssetGovernanceLabel('PERMISSION_DENIED')).toBe('Asset · 无治理读取权限');
    expect(datasetAssetGovernanceLabel('UNAVAILABLE')).toBe('Asset · Governance 不可用');
  });
});
