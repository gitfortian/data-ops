export type OfflineSyncConnectorRole = 'SOURCE' | 'SINK';

export interface OfflineSyncConnectorProfile {
  profileId: string;
  dbType: string;
  sourceConnectorId: string;
  sinkConnectorId: string;
  /** Historical UI label retained until the old editor is fully removed. */
  connectorType: string;
  pluginName: string;
  defaultProfile: boolean;
  /** Product-level guide policy. Backend adapters remain the final enforcement boundary. */
  guideMultiEnabled?: boolean;
  /** Product-level target DDL policy layered on top of connector-wide capabilities. */
  autoCreateTableEnabled?: boolean;
  /** Product-level write policy for dialects that do not implement a stable UPSERT/MERGE path. */
  upsertEnabled?: boolean;
}

/** Control-plane default execution profiles for datasource types exposed by Yak Ops. */
export const OFFLINE_SYNC_CONNECTOR_PROFILES: readonly OfflineSyncConnectorProfile[] = [
  {
    profileId: 'mysql-jdbc', dbType: 'MYSQL', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-MYSQL', defaultProfile: true,
  },
  {
    profileId: 'tidb-jdbc', dbType: 'TIDB', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-TIDB', defaultProfile: true,
  },
  {
    profileId: 'goldendb-jdbc', dbType: 'GOLDENDB', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-GOLDENDB', defaultProfile: true,
    autoCreateTableEnabled: false,
  },
  {
    profileId: 'gbase8c-jdbc', dbType: 'GBASE8C', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-GBASE8C', defaultProfile: true,
    upsertEnabled: false,
  },
  {
    profileId: 'gbase8a-jdbc', dbType: 'GBASE8A', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-GBASE8A', defaultProfile: true,
    upsertEnabled: false,
  },
  {
    profileId: 'gbase8s-jdbc', dbType: 'GBASE8S', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-GBASE8S', defaultProfile: true,
    upsertEnabled: false,
  },
  {
    profileId: 'hana-jdbc', dbType: 'HANA', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-HANA', defaultProfile: true,
  },
  {
    profileId: 'oracle-jdbc', dbType: 'ORACLE', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-ORACLE', defaultProfile: true,
  },
  {
    profileId: 'postgresql-jdbc', dbType: 'POSTGRE_SQL', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-POSTGRESQL', defaultProfile: true,
  },
  {
    profileId: 'db2-jdbc', dbType: 'DB2', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-DB2', defaultProfile: true,
  },
  {
    profileId: 'opengauss-jdbc', dbType: 'OPEN_GAUSS', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-OPENGAUSS', defaultProfile: true,
  },
  {
    profileId: 'sqlserver-jdbc', dbType: 'SQL_SERVER', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-SQLSERVER', defaultProfile: true,
  },
  {
    profileId: 'oceanbase-jdbc', dbType: 'OCEANBASE', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-OCEANBASE', defaultProfile: true,
  },
  {
    profileId: 'yashandb-jdbc', dbType: 'YASHAN_DB', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-YASHANDB', defaultProfile: true,
  },
  {
    profileId: 'highgo-jdbc', dbType: 'HIGHGO', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-HIGHGO', defaultProfile: true,
  },
  {
    profileId: 'iris-jdbc', dbType: 'IRIS', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-IRIS', defaultProfile: true,
  },
  {
    profileId: 'xugu-jdbc', dbType: 'XUGU', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-XUGU', defaultProfile: true,
  },
  {
    profileId: 'duckdb-jdbc', dbType: 'DUCKDB', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-DUCKDB', defaultProfile: true,
  },
  {
    profileId: 'doris-native', dbType: 'DORIS', sourceConnectorId: 'doris', sinkConnectorId: 'doris',
    connectorType: 'Doris', pluginName: 'DORIS', defaultProfile: true,
  },
  {
    profileId: 'starrocks-native', dbType: 'STARROCKS', sourceConnectorId: 'starrocks', sinkConnectorId: 'starrocks',
    connectorType: 'StarRocks', pluginName: 'STARROCKS', defaultProfile: true,
  },
  {
    profileId: 'clickhouse-native', dbType: 'CLICKHOUSE', sourceConnectorId: 'clickhouse', sinkConnectorId: 'clickhouse',
    connectorType: 'ClickHouse', pluginName: 'CLICKHOUSE', defaultProfile: true,
  },
  {
    profileId: 'elasticsearch7-native', dbType: 'ELASTICSEARCH7',
    sourceConnectorId: 'elasticsearch7', sinkConnectorId: 'elasticsearch7',
    connectorType: 'Elasticsearch7', pluginName: 'ELASTICSEARCH7', defaultProfile: true,
    guideMultiEnabled: false,
  },
  {
    profileId: 'elasticsearch8-native', dbType: 'ELASTICSEARCH8',
    sourceConnectorId: 'elasticsearch8', sinkConnectorId: 'elasticsearch8',
    connectorType: 'Elasticsearch8', pluginName: 'ELASTICSEARCH8', defaultProfile: true,
    guideMultiEnabled: false,
  },
  {
    profileId: 'mongodb-native', dbType: 'MONGODB',
    sourceConnectorId: 'mongodb', sinkConnectorId: 'mongodb',
    connectorType: 'MongoDB', pluginName: 'MONGODB', defaultProfile: true,
  },
  {
    profileId: 'kingbase-jdbc', dbType: 'KINGBASE', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-KINGBASE', defaultProfile: true,
  },
  {
    profileId: 'dameng-jdbc', dbType: 'DAMENG', sourceConnectorId: 'jdbc', sinkConnectorId: 'jdbc',
    connectorType: 'Jdbc', pluginName: 'JDBC-DAMENG', defaultProfile: true,
  },
] as const;

/**
 * Records without connectorId are legacy definitions. Keep their historical JDBC inference so
 * merely opening/saving an old task cannot silently switch its engine implementation.
 */
const LEGACY_JDBC_TYPES = new Set([
  'JDBC', 'MARIADB', 'DORIS', 'STARROCKS', 'CLICKHOUSE', 'HIVE', 'DM',
]);

const DB_TYPE_ALIASES: Record<string, string> = {
  POSTGRESQL: 'POSTGRE_SQL',
  POSTGRES: 'POSTGRE_SQL',
  TI_DB: 'TIDB',
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
  ELASTICSEARCH_7: 'ELASTICSEARCH7',
  ES7: 'ELASTICSEARCH7',
  ELASTICSEARCH_8: 'ELASTICSEARCH8',
  ES8: 'ELASTICSEARCH8',
  MONGO: 'MONGODB',
  MONGO_DB: 'MONGODB',
};

const normalizeDbType = (value?: string): string => {
  const normalized = String(value || '').trim().toUpperCase().replace(/-/g, '_');
  return DB_TYPE_ALIASES[normalized] || normalized;
};

export const defaultOfflineSyncConnectorProfile = (
  dbType?: string,
): OfflineSyncConnectorProfile | undefined => {
  const normalized = normalizeDbType(dbType);
  return OFFLINE_SYNC_CONNECTOR_PROFILES.find(
    (profile) => profile.defaultProfile && profile.dbType === normalized,
  );
};

export const isGuideMultiEnabledForDataSourceType = (dbType?: string): boolean =>
  defaultOfflineSyncConnectorProfile(dbType)?.guideMultiEnabled !== false;

export const isAutoCreateTableEnabledForDataSourceType = (dbType?: string): boolean =>
  defaultOfflineSyncConnectorProfile(dbType)?.autoCreateTableEnabled !== false;

export const isUpsertEnabledForDataSourceType = (dbType?: string): boolean =>
  defaultOfflineSyncConnectorProfile(dbType)?.upsertEnabled !== false;

/** Compatibility inference used only when a persisted endpoint has no durable connectorId. */
export const connectorIdForDataSourceType = (
  value?: string,
  role: OfflineSyncConnectorRole = 'SOURCE',
): string => {
  const normalized = normalizeDbType(value);
  if (!normalized) return '';
  if (LEGACY_JDBC_TYPES.has(normalized)) return 'jdbc';

  const profile = defaultOfflineSyncConnectorProfile(normalized);
  if (profile) {
    return role === 'SINK' ? profile.sinkConnectorId : profile.sourceConnectorId;
  }
  return normalized.toLowerCase();
};

/** New-task resolver: always uses the current default profile and persists its connectorId. */
export const connectorIdForNewDataSourceType = (
  value?: string,
  role: OfflineSyncConnectorRole = 'SOURCE',
): string => {
  const normalized = normalizeDbType(value);
  if (!normalized) return '';
  const profile = defaultOfflineSyncConnectorProfile(normalized);
  if (profile) {
    return role === 'SINK' ? profile.sinkConnectorId : profile.sourceConnectorId;
  }
  return normalized.toLowerCase();
};
