import {
  mergeSqlTaskContextConfig,
  resolveSqlEffectiveDatabase,
  resolveSqlEffectiveSchema,
  uniqueSqlContextValues,
} from './sqlTaskContextConfig';

describe('SQL authoring context config', () => {
  it('updates controlled context fields without dropping runtime options', () => {
    const merged = JSON.parse(
      mergeSqlTaskContextConfig(
        JSON.stringify({
          dataSourceId: 'old-source',
          database: 'legacy_db',
          schema: 'legacy_schema',
          dbType: 'postgres',
          maxRows: 500,
          timeoutSeconds: 30,
          customRuntimeFlag: true,
        }),
        {
          dataSourceId: 'source-2',
          database: 'warehouse',
          schema: 'analytics',
          dialect: 'POSTGRE_SQL',
        },
      ),
    );

    expect(merged).toEqual({
      dataSourceId: 'source-2',
      databaseName: 'warehouse',
      schemaName: 'analytics',
      dialect: 'POSTGRE_SQL',
      maxRows: 500,
      timeoutSeconds: 30,
      customRuntimeFlag: true,
    });
  });

  it('removes stale context aliases when an explicit context value is cleared', () => {
    const merged = JSON.parse(
      mergeSqlTaskContextConfig(
        JSON.stringify({
          dataSourceId: 'source-1',
          databaseName: 'db1',
          catalog: 'db2',
          schemaName: 'public',
          databaseType: 'MYSQL',
          maxRows: 100,
        }),
        { dialect: 'MYSQL' },
      ),
    );

    expect(merged).toEqual({ dialect: 'MYSQL', maxRows: 100 });
  });

  it('keeps connection defaults presentation-only until the user selects them', () => {
    expect(resolveSqlEffectiveDatabase(undefined, 'default_db')).toBe('default_db');
    expect(resolveSqlEffectiveDatabase('draft_db', 'default_db')).toBe('draft_db');
    expect(resolveSqlEffectiveSchema(undefined, 'public')).toBe('public');
    expect(resolveSqlEffectiveSchema('draft_schema', 'public')).toBe('draft_schema');
  });

  it('deduplicates catalog options while retaining current/default values', () => {
    expect(uniqueSqlContextValues(['db1', 'db2', 'db1', undefined, ' db2 ', 'db3'])).toEqual([
      'db1',
      'db2',
      'db3',
    ]);
  });
});
