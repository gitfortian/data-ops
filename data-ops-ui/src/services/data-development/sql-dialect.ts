export const DEVELOPMENT_SQL_DIALECT_VALUES = [
  'GENERIC',
  'MYSQL',
  'TIDB',
  'GOLDENDB',
  'GBASE8C',
  'GBASE8A',
  'GBASE8S',
  'HANA',
  'ORACLE',
  'POSTGRE_SQL',
  'DB2',
  'OPEN_GAUSS',
  'SQL_SERVER',
  'OCEANBASE',
  'YASHAN_DB',
  'HIGHGO',
  'IRIS',
  'XUGU',
  'DUCKDB',
  'DORIS',
  'STARROCKS',
  'CLICKHOUSE',
  'KINGBASE',
  'DAMENG',
] as const;

export type DevelopmentSqlDialect =
  (typeof DEVELOPMENT_SQL_DIALECT_VALUES)[number];

const SQL_DIALECTS = new Set<string>(DEVELOPMENT_SQL_DIALECT_VALUES);
const SQL_DIALECT_ALIASES: Record<string, DevelopmentSqlDialect> = {
  AUTO: 'GENERIC',
  UNKNOWN: 'GENERIC',
  GENERIC_SQL: 'GENERIC',
  POSTGRES: 'POSTGRE_SQL',
  POSTGRESQL: 'POSTGRE_SQL',
  PGSQL: 'POSTGRE_SQL',
  MARIADB: 'MYSQL',
  GOLDEN_DB: 'GOLDENDB',
  ZTE_GOLDENDB: 'GOLDENDB',
  GBASE_8C: 'GBASE8C',
  GBASE_8A: 'GBASE8A',
  GBASE_8S: 'GBASE8S',
  SAP_HANA: 'HANA',
  SAPHANA: 'HANA',
  OPENGAUSS: 'OPEN_GAUSS',
  SQLSERVER: 'SQL_SERVER',
  MSSQL: 'SQL_SERVER',
  YASHANDB: 'YASHAN_DB',
  YASDB: 'YASHAN_DB',
  HIGH_GO: 'HIGHGO',
  HGDB: 'HIGHGO',
  INTERSYSTEMS_IRIS: 'IRIS',
  XUGUDB: 'XUGU',
  DUCK_DB: 'DUCKDB',
  STAR_ROCKS: 'STARROCKS',
  KINGBASEES: 'KINGBASE',
  DM: 'DAMENG',
};

export const normalizeDevelopmentSqlDialect = (
  value?: unknown,
): DevelopmentSqlDialect => {
  if (typeof value !== 'string' || !value.trim()) return 'GENERIC';

  const normalized = value
    .trim()
    .toUpperCase()
    .replace(/-/g, '_')
    .replace(/\s+/g, '_');
  const aliased = SQL_DIALECT_ALIASES[normalized] || normalized;
  return SQL_DIALECTS.has(aliased)
    ? (aliased as DevelopmentSqlDialect)
    : 'GENERIC';
};

export interface DevelopmentSqlTaskConfig {
  dataSourceId?: string;
  databaseName?: string;
  schemaName?: string;
  dialect: DevelopmentSqlDialect;
  maxRows?: number;
  timeoutSeconds?: number;
}

export const parseDevelopmentSqlTaskConfig = (
  configJson?: string,
): DevelopmentSqlTaskConfig => {
  let raw: Record<string, unknown> = {};
  try {
    const parsed = JSON.parse(configJson || '{}');
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      raw = parsed as Record<string, unknown>;
    }
  } catch {
    raw = {};
  }

  const stringValue = (...keys: string[]) => {
    for (const key of keys) {
      const value = raw[key];
      if (typeof value === 'string' && value.trim()) return value.trim();
    }
    return undefined;
  };

  const numberValue = (key: string) => {
    const value = raw[key];
    return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
  };

  return {
    dataSourceId: stringValue('dataSourceId'),
    databaseName: stringValue('databaseName', 'database', 'catalog'),
    schemaName: stringValue('schemaName', 'schema'),
    dialect: normalizeDevelopmentSqlDialect(
      stringValue('dialect', 'databaseType', 'dbType'),
    ),
    maxRows: numberValue('maxRows'),
    timeoutSeconds: numberValue('timeoutSeconds'),
  };
};
