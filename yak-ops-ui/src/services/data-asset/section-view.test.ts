import { toAssetSectionView, unavailableAssetSection } from './section-view';
import type { AssetSectionContract } from './types';
import { sourceObjectPath } from '../../pages/data-asset/constants';

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

  it('carries the originating asset identity into the Modeling detail and supports return', () => {
    expect(sourceObjectPath('MODEL', '49', 8)).toBe('/modeling/models/49?returnAssetId=8');
  });

  it('adds the originating Asset id to specialist actions that return to the catalog', () => {
    const contract: AssetSectionContract = {
      sectionType: 'TECHNICAL_METADATA',
      status: 'OK',
      ownerDomain: 'METADATA',
      summary: { values: {} },
      actions: [
        { label: '进入元数据实体视图', target: '/data-asset/catalog?view=entity', sourceId: 'table:3:db.t' },
        { label: '外部说明', target: 'https://example.com/help', sourceId: 'table:3:db.t' },
        { label: '同前缀无关路径', target: '/data-asset/catalog-other', sourceId: 'table:3:db.t' },
        { label: '无返回处理的页面', target: '/help', sourceId: 'table:3:db.t' },
      ],
      evidence: [],
      provenance: { sourceDomain: 'METADATA', sourceId: 'table:3:db.t', observedAt: '2026-09-23T00:00:00Z' },
      capability: { applicable: true, available: true },
    };

    expect(toAssetSectionView(contract, 8).actions).toEqual([
      { label: '进入元数据实体视图', target: '/data-asset/catalog?view=entity&returnAssetId=8', sourceId: 'table:3:db.t' },
      { label: '外部说明', target: 'https://example.com/help', sourceId: 'table:3:db.t' },
      { label: '同前缀无关路径', target: '/data-asset/catalog-other', sourceId: 'table:3:db.t' },
      { label: '无返回处理的页面', target: '/help', sourceId: 'table:3:db.t' },
    ]);
  });
});
