import {
  formatSqlIdentifierForCompletion,
  getSqlDialectFamily,
  resolveSqlEditorProfile,
} from './sqlEditorProfiles';

describe('sqlEditorProfiles', () => {
  it('groups compatible dialects into shared editor families', () => {
    expect(getSqlDialectFamily('MYSQL')).toBe('MYSQL');
    expect(getSqlDialectFamily('TIDB')).toBe('MYSQL');
    expect(getSqlDialectFamily('DORIS')).toBe('MYSQL');
    expect(getSqlDialectFamily('STARROCKS')).toBe('MYSQL');
    expect(getSqlDialectFamily('POSTGRE_SQL')).toBe('POSTGRESQL');
    expect(getSqlDialectFamily('OPEN_GAUSS')).toBe('POSTGRESQL');
    expect(getSqlDialectFamily('KINGBASE')).toBe('POSTGRESQL');
    expect(getSqlDialectFamily('DAMENG')).toBe('ORACLE');
  });

  it('layers dialect-specific vocabulary over the family profile', () => {
    const tidb = resolveSqlEditorProfile('TIDB');
    const doris = resolveSqlEditorProfile('DORIS');
    const starrocks = resolveSqlEditorProfile('STARROCKS');

    expect(tidb.keywords).toContain('AUTO_RANDOM');
    expect(doris.keywords).toContain('DUPLICATE KEY');
    expect(doris.keywords).toContain('DISTRIBUTED BY');
    expect(starrocks.keywords).toContain('PRIMARY KEY');
    expect(starrocks.formatterClauses).toContain('PROPERTIES');
  });

  it('adds database-specific functions and snippets without losing generic SQL help', () => {
    const postgres = resolveSqlEditorProfile('POSTGRE_SQL');
    const mysql = resolveSqlEditorProfile('MYSQL');
    const oracle = resolveSqlEditorProfile('ORACLE');

    expect(postgres.functions.map((item) => item.name)).toEqual(
      expect.arrayContaining(['COUNT', 'DATE_TRUNC', 'STRING_AGG']),
    );
    expect(mysql.snippets.map((item) => item.prefix)).toContain('insdup');
    expect(oracle.snippets.map((item) => item.prefix)).toContain('merge');
  });

  it('keeps two-part namespace interpretation family-specific', () => {
    expect(resolveSqlEditorProfile('MYSQL').twoPartNamespace).toBe('database');
    expect(resolveSqlEditorProfile('STARROCKS').twoPartNamespace).toBe('database');
    expect(resolveSqlEditorProfile('POSTGRE_SQL').twoPartNamespace).toBe('schema');
    expect(resolveSqlEditorProfile('SQL_SERVER').twoPartNamespace).toBe('schema');
  });

  it('quotes only identifiers that need dialect-specific escaping', () => {
    expect(
      formatSqlIdentifierForCompletion(
        'customer_id',
        resolveSqlEditorProfile('MYSQL'),
      ),
    ).toBe('customer_id');
    expect(
      formatSqlIdentifierForCompletion('order', resolveSqlEditorProfile('MYSQL')),
    ).toBe('`order`');
    expect(
      formatSqlIdentifierForCompletion(
        'order detail',
        resolveSqlEditorProfile('POSTGRE_SQL'),
      ),
    ).toBe('"order detail"');
    expect(
      formatSqlIdentifierForCompletion(
        'order',
        resolveSqlEditorProfile('SQL_SERVER'),
      ),
    ).toBe('[order]');
  });
});
