import {
  ASSET_STATUS_BAR_COLORS,
  formatChangeDiff,
  sourceObjectPath,
} from './constants';

describe('asset source object navigation', () => {
  it('uses stable Dataset identity to return to Data Development provenance', () => {
    expect(sourceObjectPath('DATASET', '55', 101)).toBe(
      '/data-development?datasetId=55&returnAssetId=101',
    );
  });

  it('keeps model source navigation and return context intact', () => {
    expect(sourceObjectPath('MODEL', '8', 101)).toBe(
      '/modeling/models/8?returnAssetId=101',
    );
  });
});

describe('asset change diff summary', () => {
  it('explains records that have no comparison baseline instead of printing an empty object', () => {
    expect(formatChangeDiff('NEW', {})).toBe('首次登记，无对比基线');
    expect(formatChangeDiff('NEW', null)).toBe('首次登记，无对比基线');
    expect(formatChangeDiff('REAPPEARED', {})).toBe('源重新出现，恢复原状态');
  });

  it('renders field-level before/after pairs with Chinese labels', () => {
    expect(formatChangeDiff('META_CHANGED', { name: ['订单表', '订单主表'] })).toBe(
      '名称: 订单表 → 订单主表',
    );
    expect(
      formatChangeDiff('META_CHANGED', {
        layerCode: ['ODS', 'DWD'],
        description: [null, '清洗后的明细'],
      }),
    ).toBe('分层: ODS → DWD；描述: 空 → 清洗后的明细');
  });

  it('translates status enums and timestamps inside gone records', () => {
    const summary = formatChangeDiff('SOURCE_GONE', {
      previousStatus: 'PENDING',
      goneAt: '2026-10-01T19:13:56',
    });
    expect(summary).toContain('消失前状态: 待上架');
    expect(summary).toContain('消失时间: 2026-10-01 19:13');
  });

  it('flags fingerprint-only changes when display fields are unchanged', () => {
    expect(formatChangeDiff('META_CHANGED', {})).toBe('内容指纹变化，展示字段无差异');
  });
});

describe('asset overview status bars', () => {
  it('gives every status a distinct semantic color', () => {
    const colors = Object.values(ASSET_STATUS_BAR_COLORS);
    expect(new Set(colors).size).toBe(colors.length);
    expect(ASSET_STATUS_BAR_COLORS.PUBLISHED).toBe('#52c41a');
    expect(ASSET_STATUS_BAR_COLORS.SOURCE_GONE).toBe('#f5222d');
  });
});
