import { consumptionEvidenceTarget } from './evidence-navigation';

describe('source-owned Usage Evidence backlinks', () => {
  const dataset = { productType: 'DATASET' as const, sourceIdentity: '88' };
  const service = { productType: 'DATA_SERVICE' as const, sourceIdentity: '37' };

  it('opens the actual Dataset Query ID in source diagnostics', () => {
    expect(consumptionEvidenceTarget(dataset, 'DATASET_QUERY_PERFORMANCE:query:q-2026_10.8'))
      .toEqual({
        href: '/dataset/88?tab=diagnostics&queryId=q-2026_10.8',
        description: '打开 Dataset Query 原始诊断',
      });
  });

  it('keeps Data Service BIGINT Invocation ID as an unrounded string', () => {
    expect(consumptionEvidenceTarget(service,
      'DATA_SERVICE_INVOCATION:invocation:90071992547409933')?.href)
      .toBe('/data-service/api/37?tab=logs&invocationId=90071992547409933');
  });

  it('never builds misleading links from mismatched, missing or invented providers', () => {
    expect(consumptionEvidenceTarget(dataset, 'DATA_SERVICE_INVOCATION:invocation:123')).toBeNull();
    expect(consumptionEvidenceTarget(service, 'DATASET_QUERY_PERFORMANCE:query:q-1')).toBeNull();
    expect(consumptionEvidenceTarget(dataset, 'DATASET_QUERY_PERFORMANCE:query:../secret')).toBeNull();
    expect(consumptionEvidenceTarget(dataset, 'DATASET_QUERY_PERFORMANCE:query:')).toBeNull();
    expect(consumptionEvidenceTarget(service, 'DATA_SERVICE_INVOCATION:invocation:0')).toBeNull();
    expect(consumptionEvidenceTarget({ ...dataset, sourceIdentity: '88/x' },
      'DATASET_QUERY_PERFORMANCE:query:q-1')).toBeNull();
  });
});
