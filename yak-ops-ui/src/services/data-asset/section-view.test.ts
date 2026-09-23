import { toAssetSectionView, unavailableAssetSection } from './section-view';
import type { AssetSectionContract } from './types';

describe('asset section view adapter', () => {
  it('keeps contract ownership, evidence and capability alongside its summary', () => {
    const contract: AssetSectionContract = {
      sectionType: 'TECHNICAL_METADATA',
      status: 'OK',
      ownerDomain: 'METADATA',
      summary: { values: { name: 'orders', columns: [{ name: 'id' }] } },
      actions: [{ label: '打开目录', target: '/catalog', sourceId: 'table:1' }],
      evidence: [{ sourceDomain: 'METADATA', referenceId: 'table:1', observedAt: '2026-09-23T00:00:00Z' }],
      provenance: { sourceDomain: 'METADATA', sourceId: 'table:1', observedAt: '2026-09-23T00:00:00Z' },
      capability: { applicable: true, available: true },
    };

    expect(toAssetSectionView(contract)).toMatchObject({
      status: 'OK',
      ownerDomain: 'METADATA',
      data: { name: 'orders', columns: [{ name: 'id' }] },
      evidence: [{ sourceDomain: 'METADATA', referenceId: 'table:1' }],
      provenance: { sourceId: 'table:1' },
      capability: { applicable: true, available: true },
      actions: [{ target: '/catalog' }],
    });
  });

  it('represents a failed section request as unavailable instead of omitting it', () => {
    expect(unavailableAssetSection()).toMatchObject({
      status: 'UNAVAILABLE',
      note: expect.any(String),
    });
  });
});
