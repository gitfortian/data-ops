import {
  canOpenLineageEvidence,
  lineageEvidenceStatusLabel,
  lineageEvidenceUrl,
  type DevelopmentLineageEvidence,
} from './governanceEvidence';

const evidence = (
  status: DevelopmentLineageEvidence['status'],
  assetKey: string | null = 'sql-task:data-development:7',
): DevelopmentLineageEvidence => ({
  nodeId: '7',
  revisionId: '91',
  revisionNo: 3,
  status,
  lineageAssetKey: assetKey,
  attempts: 1,
});

describe('development governance evidence', () => {
  it('only opens professional lineage context after durable delivery succeeds', () => {
    expect(canOpenLineageEvidence(evidence('PENDING'))).toBe(false);
    expect(canOpenLineageEvidence(evidence('FAILED'))).toBe(false);
    expect(canOpenLineageEvidence(evidence('UNAVAILABLE'))).toBe(false);
    expect(canOpenLineageEvidence(evidence('SUCCEEDED'))).toBe(true);
  });

  it('preserves the development node as return context', () => {
    expect(lineageEvidenceUrl(evidence('SUCCEEDED'), '7')).toBe(
      '/data-analysis/lineage?assetKey=sql-task%3Adata-development%3A7'
        + '&returnTo=%2Fdata-development%3FnodeId%3D7',
    );
  });

  it('keeps unavailable distinct from no lineage', () => {
    expect(lineageEvidenceStatusLabel('UNAVAILABLE')).toBe('Lineage Evidence 不可用');
    expect(lineageEvidenceStatusLabel('NOT_APPLICABLE')).toBe('不适用');
  });
});
