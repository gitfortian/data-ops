import {
  buildLiveSourceOdsPath, buildLiveSourceReturnPath,
  isValidLiveSourceCoordinate, parseLiveSourceReturn, verifiedOdsModelSourceReturn,
} from './liveSourceOdsJourney';

const source = {
  dataSourceId: 18,
  database: 'trade_db',
  schema: 'sales',
  table: 'trade_order_detail',
};

describe('FE-A2 strict live source → ODS → exact live source coordinates', () => {
  it('roundtrips full database/schema/table identity and escapes data safely', () => {
    const forward = buildLiveSourceOdsPath(source)!;
    const forwardParams = new URLSearchParams(forward.split('?')[1]);
    expect(forward.startsWith('/modeling?')).toBe(true);
    expect(forwardParams.get('sourceJourney')).toBe('ods');
    expect(forwardParams.get('origin')).toBe('live-source');
    expect(forwardParams.get('datasourceId')).toBe('18');
    expect(forwardParams.get('schema')).toBe('sales');

    const back = buildLiveSourceReturnPath(source)!;
    expect(back.startsWith('/data-source?')).toBe(true);
    expect(parseLiveSourceReturn(new URLSearchParams(back.split('?')[1]))).toEqual(source);
    expect(parseLiveSourceReturn(new URLSearchParams('sourceId=18&database=trade_db&table=trade_order')))
      .toEqual({ dataSourceId: 18, database: 'trade_db', schema: undefined, table: 'trade_order' });
  });

  it('keeps distinct tables and schemas as distinct business sources', () => {
    expect(buildLiveSourceOdsPath(source)).not.toBe(
      buildLiveSourceOdsPath({ ...source, table: 'trade_order' }),
    );
    expect(buildLiveSourceReturnPath(source)).not.toBe(
      buildLiveSourceReturnPath({ ...source, schema: 'audit' }),
    );
  });

  it('rejects bad IDs and partial/ambiguous coordinates rather than opening a wrong source', () => {
    const bad = [0, -1, 1.5, Number.MAX_SAFE_INTEGER + 1];
    for (const dataSourceId of bad) {
      expect(isValidLiveSourceCoordinate({ ...source, dataSourceId })).toBe(false);
      expect(buildLiveSourceOdsPath({ ...source, dataSourceId })).toBeNull();
    }
    expect(buildLiveSourceReturnPath({ ...source, database: '' })).toBeNull();
    expect(buildLiveSourceOdsPath({ ...source, table: ' bad ' })).toBeNull();
    for (const raw of ['1e3', '-1', '0', '1.5', 'Infinity', '', '9007199254740993']) {
      expect(parseLiveSourceReturn(new URLSearchParams({
        sourceId: raw, database: 'trade_db', table: 'trade_order',
      }))).toBeNull();
    }
    expect(parseLiveSourceReturn(new URLSearchParams('sourceId=18&database=trade_db'))).toBeNull();
  });

  it('never accepts a URL as an arbitrary return location', () => {
    const malicious = {
      ...source, table: '//attacker.example/path?redirect=https://attacker.example',
    };
    const forward = buildLiveSourceOdsPath(malicious);
    if (forward) expect(forward.startsWith('/modeling?')).toBe(true);
    const back = buildLiveSourceReturnPath(malicious);
    if (back) expect(back.startsWith('/data-source?')).toBe(true);
  });
});

describe('FE-A2 existing ODS detail precise verified return', () => {
  const model = {
    id: 701, layerCode: 'ODS', sourceDatasourceId: 18,
    sourceDatabase: 'trade_db', sourceSchema: 'sales',
    sourceTable: 'trade_order_detail',
  };
  const q = new URLSearchParams({
    origin: 'live-source', datasourceId: '18', database: 'trade_db',
    schema: 'sales', table: 'trade_order_detail',
  });
  it('returns only when the persisted ODS model proves the exact source', () => {
    expect(verifiedOdsModelSourceReturn(model, q)).toBe(buildLiveSourceReturnPath(source));
  });
  it('rejects cross source, wrong schema, missing legacy schema and non ODS', () => {
    expect(verifiedOdsModelSourceReturn({ ...model, sourceDatasourceId: 19 }, q)).toBeNull();
    expect(verifiedOdsModelSourceReturn({ ...model, sourceTable: 'trade_order' }, q)).toBeNull();
    expect(verifiedOdsModelSourceReturn({ ...model, sourceSchema: 'audit' }, q)).toBeNull();
    expect(verifiedOdsModelSourceReturn({ ...model, sourceSchema: null }, q)).toBeNull();
    expect(verifiedOdsModelSourceReturn({ ...model, layerCode: 'DWD' }, q)).toBeNull();
    expect(verifiedOdsModelSourceReturn(undefined, q)).toBeNull();
    expect(verifiedOdsModelSourceReturn(model, new URLSearchParams({
      ...Object.fromEntries(q.entries()), origin: 'untrusted', returnTo: 'https://evil.test',
    }))).toBeNull();
  });
});
