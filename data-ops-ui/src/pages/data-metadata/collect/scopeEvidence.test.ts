import { currentSourceCatalogPath, readFrozenHarvestScope } from './scopeEvidence';

describe('historical Metadata Run scope evidence', () => {
  it('rejects missing, broken or untrusted source identity', () => {
    expect(readFrozenHarvestScope()).toBeNull();
    expect(readFrozenHarvestScope('{oops')).toBeNull();
    expect(readFrozenHarvestScope('{}')).toBeNull();
    expect(readFrozenHarvestScope('{"dataSourceId":0}')).toBeNull();
    expect(readFrozenHarvestScope('{"dataSourceId":"4"}')).toBeNull();
    expect(readFrozenHarvestScope('{"dataSourceId":4.5}')).toBeNull();
  });

  it('preserves the Run frozen scope without inventing members', () => {
    const scope = readFrozenHarvestScope(JSON.stringify({
      dataSourceId: 18, databaseName: 'trade_db', schemaName: 'public',
      tablePattern: 'trade_order_detail', collectColumns: true,
    }));
    expect(scope).toEqual({
      dataSourceId: 18, databaseName: 'trade_db', schemaName: 'public',
      tablePattern: 'trade_order_detail', collectColumns: true,
    });
    expect(currentSourceCatalogPath(scope!))
      .toBe('/data-asset/catalog?view=entity&datasourceId=18&databaseName=trade_db');
  });

  it('does not confuse a SQL LIKE table pattern with a current directory search filter', () => {
    const scope = readFrozenHarvestScope(JSON.stringify({
      dataSourceId: 42, tablePattern: 'order_%',
    }));
    expect(currentSourceCatalogPath(scope!)).toBe('/data-asset/catalog?view=entity&datasourceId=42');
  });
});
