import type { DynamicFormJdbcUrlLinkage } from '../../types';
import { buildJdbcUrlFromTemplate, parseJdbcUrlByTemplate } from './utils';

const MYSQL_LINKAGE: DynamicFormJdbcUrlLinkage = {
  template: 'jdbc:mysql://{host}:{port}/{database}',
  preserveSuffix: true,
};

const ORACLE_LINKAGE: DynamicFormJdbcUrlLinkage = {
  template: 'jdbc:oracle:thin:@//{host}:{port}/{database}',
};

describe('JDBC URL linkage codec', () => {
  it('builds URL from structured connection fields', () => {
    expect(
      buildJdbcUrlFromTemplate(MYSQL_LINKAGE, {
        host: 'db.internal',
        port: 3306,
        database: 'yak',
      }),
    ).toBe('jdbc:mysql://db.internal:3306/yak');
  });

  it('parses URL back into host, port and database', () => {
    expect(
      parseJdbcUrlByTemplate(
        MYSQL_LINKAGE,
        'jdbc:mysql://db.internal:3307/yak_test',
      ),
    ).toEqual({
      host: 'db.internal',
      port: 3307,
      database: 'yak_test',
    });
  });

  it('preserves query or property suffix when rebuilding URL', () => {
    const parsed = parseJdbcUrlByTemplate(
      MYSQL_LINKAGE,
      'jdbc:mysql://db.internal:3306/yak?useSSL=false&serverTimezone=UTC',
    );

    expect(parsed?.suffix).toBe('?useSSL=false&serverTimezone=UTC');
    expect(
      buildJdbcUrlFromTemplate(MYSQL_LINKAGE, {
        host: 'db-new.internal',
        port: 3306,
        database: 'yak',
        suffix: parsed?.suffix,
      }),
    ).toBe(
      'jdbc:mysql://db-new.internal:3306/yak?useSSL=false&serverTimezone=UTC',
    );
  });

  it('supports Oracle thin URL templates', () => {
    const url = buildJdbcUrlFromTemplate(ORACLE_LINKAGE, {
      host: 'oracle.internal',
      port: 1521,
      database: 'ORCL',
    });
    expect(url).toBe('jdbc:oracle:thin:@//oracle.internal:1521/ORCL');
    expect(parseJdbcUrlByTemplate(ORACLE_LINKAGE, url)).toEqual({
      host: 'oracle.internal',
      port: 1521,
      database: 'ORCL',
    });
  });

  it('normalizes IPv6 host brackets', () => {
    const url = buildJdbcUrlFromTemplate(MYSQL_LINKAGE, {
      host: '2001:db8::10',
      port: 3306,
      database: 'yak',
    });
    expect(url).toBe('jdbc:mysql://[2001:db8::10]:3306/yak');
    expect(parseJdbcUrlByTemplate(MYSQL_LINKAGE, url)?.host).toBe(
      '2001:db8::10',
    );
  });

  it('keeps unsupported custom URL editable without false parsing', () => {
    expect(
      parseJdbcUrlByTemplate(
        MYSQL_LINKAGE,
        'jdbc:mysql:loadbalance://db-a,db-b/yak',
      ),
    ).toBeUndefined();
  });
});
