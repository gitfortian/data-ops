import type { CatalogDatasetVersion } from '@/services/data-analysis';
import { getDatasetVersionSourceSummary } from './utils';

describe('Dataset catalog source summary', () => {
  it('renders standalone SQL from its datasource snapshot instead of TaskAsset zero values', () => {
    const version: CatalogDatasetVersion = {
      id: '20',
      versionNo: 2,
      sourceType: 'SQL_QUERY',
      dataSourceId: 'mysql-prod',
    };

    expect(getDatasetVersionSourceSummary(version)).toEqual({
      title: '数据源 mysql-prod',
      detail: 'Standalone SQL · DV2',
    });
  });

  it('keeps immutable TaskRevision wording for QUERY_REVISION datasets', () => {
    const version: CatalogDatasetVersion = {
      id: '30',
      versionNo: 4,
      sourceType: 'QUERY_REVISION',
      sourceTaskAssetId: '10',
      sourceTaskRevisionId: '40',
      sourceTaskRevisionNo: 7,
    };

    expect(getDatasetVersionSourceSummary(version, '订单明细')).toEqual({
      title: '订单明细',
      detail: 'SQL V7',
    });
  });
});
