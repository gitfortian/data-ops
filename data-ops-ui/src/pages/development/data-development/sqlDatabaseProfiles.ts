import {
  normalizeDevelopmentSqlDialect,
  type DevelopmentSqlDialect,
} from '@/services/data-development';

import type { DevelopmentResourceNode } from './types';

export interface DevelopmentSqlDatabaseProfile {
  dialect: DevelopmentSqlDialect;
  label: string;
}

/**
 * SQL authoring databases exposed in PR2.
 *
 * The list intentionally follows the current JDBC-capable relational set, plus Doris and
 * StarRocks. Search/document databases stay outside SQL authoring until they have their own
 * development model.
 */
export const DATA_DEVELOPMENT_SQL_DATABASE_PROFILES: DevelopmentSqlDatabaseProfile[] = [
  { dialect: 'MYSQL', label: 'MySQL' },
  { dialect: 'TIDB', label: 'TiDB' },
  { dialect: 'GOLDENDB', label: 'GoldenDB' },
  { dialect: 'GBASE8C', label: 'GBase 8c' },
  { dialect: 'GBASE8A', label: 'GBase 8a' },
  { dialect: 'GBASE8S', label: 'GBase 8s' },
  { dialect: 'HANA', label: 'SAP HANA' },
  { dialect: 'ORACLE', label: 'Oracle' },
  { dialect: 'POSTGRE_SQL', label: 'PostgreSQL' },
  { dialect: 'DB2', label: 'IBM Db2' },
  { dialect: 'OPEN_GAUSS', label: 'openGauss' },
  { dialect: 'SQL_SERVER', label: 'SQL Server' },
  { dialect: 'OCEANBASE', label: 'OceanBase' },
  { dialect: 'YASHAN_DB', label: 'YashanDB' },
  { dialect: 'HIGHGO', label: 'HighGo' },
  { dialect: 'IRIS', label: 'InterSystems IRIS' },
  { dialect: 'XUGU', label: 'XuguDB' },
  { dialect: 'DUCKDB', label: 'DuckDB' },
  { dialect: 'KINGBASE', label: 'KingbaseES' },
  { dialect: 'DAMENG', label: '达梦' },
  { dialect: 'DORIS', label: 'Doris' },
  { dialect: 'STARROCKS', label: 'StarRocks' },
];

const SQL_DATABASE_PROFILE_MAP = new Map(
  DATA_DEVELOPMENT_SQL_DATABASE_PROFILES.map((profile) => [
    profile.dialect,
    profile,
  ]),
);

export const getDevelopmentSqlDialectLabel = (
  dialect?: DevelopmentSqlDialect | string,
) => {
  const normalized = normalizeDevelopmentSqlDialect(dialect);
  if (normalized === 'GENERIC') return 'SQL';
  return SQL_DATABASE_PROFILE_MAP.get(normalized)?.label || normalized;
};

export const getDevelopmentResourceSqlDialect = (
  node?: DevelopmentResourceNode,
): DevelopmentSqlDialect => {
  if (!node || node.type !== 'SQL') return 'GENERIC';
  const projected = (node as unknown as { sqlDialect?: unknown }).sqlDialect;
  return normalizeDevelopmentSqlDialect(projected);
};

export const sqlDialectMatchesDataSource = (
  dialect: DevelopmentSqlDialect,
  dbType?: string,
) =>
  dialect === 'GENERIC' ||
  normalizeDevelopmentSqlDialect(dbType) === dialect;
