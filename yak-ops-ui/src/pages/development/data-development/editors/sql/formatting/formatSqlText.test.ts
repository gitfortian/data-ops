import { formatSqlText } from './formatSqlText';

describe('formatSqlText', () => {
  it('keeps generic formatting behavior for common clauses', () => {
    expect(
      formatSqlText('SELECT * FROM users WHERE active = 1 ORDER BY id'),
    ).toBe('SELECT *\nFROM users\nWHERE active = 1\nORDER BY id');
  });

  it('breaks PostgreSQL conflict and returning clauses using the profile', () => {
    expect(
      formatSqlText(
        'INSERT INTO users (id) VALUES (1) ON CONFLICT (id) DO UPDATE SET id = 1 RETURNING id',
        'POSTGRE_SQL',
      ),
    ).toBe(
      'INSERT INTO users (id)\nVALUES (1)\nON CONFLICT (id)\nDO UPDATE\nSET id = 1\nRETURNING id',
    );
  });

  it('breaks MySQL duplicate-key updates using the profile', () => {
    expect(
      formatSqlText(
        'INSERT INTO users (id) VALUES (1) ON DUPLICATE KEY UPDATE id = 2',
        'MYSQL',
      ),
    ).toBe(
      'INSERT INTO users (id)\nVALUES (1)\nON DUPLICATE KEY UPDATE id = 2',
    );
  });

  it('formats Doris distribution clauses without teaching the formatter Doris branches', () => {
    expect(
      formatSqlText(
        'CREATE TABLE t (id BIGINT) DUPLICATE KEY (id) DISTRIBUTED BY HASH(id) PROPERTIES ("replication_num" = "1")',
        'DORIS',
      ),
    ).toBe(
      'CREATE TABLE t (id BIGINT) DUPLICATE KEY (id)\nDISTRIBUTED BY HASH(id)\nPROPERTIES ("replication_num" = "1")',
    );
  });
});
