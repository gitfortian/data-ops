import { buildSavePayload, normalizeEditDetail } from './model';

describe('ODS provenance in manual offline task revisions', () => {
  it('persists a complete ODS reference across load/edit/save', () => {
    const data = {
      id: '77',
      basic: { jobName: 'Order ODS sync', jobDesc: '', mode: 'GUIDE_SINGLE' },
      source: {
        dataSourceId: '10', connectorId: 'jdbc', dbType: 'POSTGRE_SQL',
        config: { table: 'public.trade_order_detail' },
      },
      sink: {
        dataSourceId: '20', connectorId: 'jdbc', dbType: 'POSTGRE_SQL',
        config: { table: 'ods.ods_order_detail', autoCreateTable: false },
      },
      odsHandoff: { modelId: 50, versionNo: 2, targetObservationId: 99 },
    };
    const editor = normalizeEditDetail(data, '77');
    expect(editor.odsHandoff).toEqual(data.odsHandoff);
    const payload = buildSavePayload(editor);
    expect('odsHandoff' in payload && payload.odsHandoff).toEqual(data.odsHandoff);
    expect('config' in payload.source && payload.source.config.table).toBe('public.trade_order_detail');
    expect('config' in payload.sink && payload.sink.config.table).toBe('ods.ods_order_detail');
  });

  it('does not invent a reference for legacy tasks or broken stored IDs', () => {
    const legacy = normalizeEditDetail({ basic: { mode: 'GUIDE_SINGLE' } }, '78');
    expect(legacy.odsHandoff).toBeUndefined();
    expect('odsHandoff' in buildSavePayload(legacy)).toBe(false);
    const malformed = normalizeEditDetail({
      basic: { mode: 'GUIDE_SINGLE' },
      odsHandoff: { modelId: 50, versionNo: 0, targetObservationId: 99 },
    }, '79');
    expect(malformed.odsHandoff).toBeUndefined();
  });
});
