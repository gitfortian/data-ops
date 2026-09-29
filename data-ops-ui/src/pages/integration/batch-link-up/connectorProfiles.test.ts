import {
  connectorIdForDataSourceType,
  connectorIdForNewDataSourceType,
  defaultOfflineSyncConnectorProfile,
  isAutoCreateTableEnabledForDataSourceType,
  isGuideMultiEnabledForDataSourceType,
  isUpsertEnabledForDataSourceType,
  OFFLINE_SYNC_CONNECTOR_PROFILES,
} from './connectorProfiles';

test('uses native profiles where needed while keeping JDBC datasource expansion explicit', () => {
  expect(OFFLINE_SYNC_CONNECTOR_PROFILES.map((profile) => profile.dbType)).toEqual([
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
    'ELASTICSEARCH7',
    'ELASTICSEARCH8',
    'MONGODB',
    'KINGBASE',
    'DAMENG',
  ]);
  expect(defaultOfflineSyncConnectorProfile('TIDB')).toMatchObject({
    profileId: 'tidb-jdbc',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    pluginName: 'JDBC-TIDB',
  });
  expect(defaultOfflineSyncConnectorProfile('GOLDENDB')).toMatchObject({
    profileId: 'goldendb-jdbc',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    pluginName: 'JDBC-GOLDENDB',
    autoCreateTableEnabled: false,
  });
  expect(defaultOfflineSyncConnectorProfile('GOLDEN-DB')).toMatchObject({
    profileId: 'goldendb-jdbc',
    dbType: 'GOLDENDB',
  });
  expect(defaultOfflineSyncConnectorProfile('GBASE8C')).toMatchObject({
    profileId: 'gbase8c-jdbc',
    dbType: 'GBASE8C',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    pluginName: 'JDBC-GBASE8C',
    upsertEnabled: false,
  });
  expect(defaultOfflineSyncConnectorProfile('GBASE-8A')).toMatchObject({
    profileId: 'gbase8a-jdbc',
    dbType: 'GBASE8A',
    pluginName: 'JDBC-GBASE8A',
    upsertEnabled: false,
  });
  expect(defaultOfflineSyncConnectorProfile('GBASE-8S')).toMatchObject({
    profileId: 'gbase8s-jdbc',
    dbType: 'GBASE8S',
    pluginName: 'JDBC-GBASE8S',
    upsertEnabled: false,
  });
  expect(defaultOfflineSyncConnectorProfile('HANA')).toMatchObject({
    profileId: 'hana-jdbc',
    sourceConnectorId: 'jdbc',
    sinkConnectorId: 'jdbc',
    pluginName: 'JDBC-HANA',
  });
  expect(defaultOfflineSyncConnectorProfile('SAP-HANA')).toMatchObject({
    profileId: 'hana-jdbc',
    dbType: 'HANA',
  });
  expect(defaultOfflineSyncConnectorProfile('DORIS')).toMatchObject({
    profileId: 'doris-native',
    sourceConnectorId: 'doris',
    sinkConnectorId: 'doris',
  });
  expect(defaultOfflineSyncConnectorProfile('STARROCKS')).toMatchObject({
    profileId: 'starrocks-native',
    sourceConnectorId: 'starrocks',
  });
  expect(defaultOfflineSyncConnectorProfile('CLICKHOUSE')).toMatchObject({
    profileId: 'clickhouse-native',
    sourceConnectorId: 'clickhouse',
  });
  for (const dbType of [
    'GOLDENDB', 'GBASE8C', 'GBASE8A', 'GBASE8S', 'HANA', 'YASHAN_DB',
    'HIGHGO', 'IRIS', 'XUGU', 'DUCKDB',
  ]) {
    expect(defaultOfflineSyncConnectorProfile(dbType)).toMatchObject({
      dbType,
      sourceConnectorId: 'jdbc',
      sinkConnectorId: 'jdbc',
    });
  }
  expect(defaultOfflineSyncConnectorProfile('ELASTICSEARCH7')).toMatchObject({
    profileId: 'elasticsearch7-native',
    sourceConnectorId: 'elasticsearch7',
    sinkConnectorId: 'elasticsearch7',
    guideMultiEnabled: false,
  });
  expect(defaultOfflineSyncConnectorProfile('ELASTICSEARCH8')).toMatchObject({
    profileId: 'elasticsearch8-native',
    sourceConnectorId: 'elasticsearch8',
    sinkConnectorId: 'elasticsearch8',
    guideMultiEnabled: false,
  });
});

test('separates legacy inference from new-task profile selection', () => {
  expect(connectorIdForDataSourceType('DORIS')).toBe('jdbc');
  expect(connectorIdForDataSourceType('STARROCKS')).toBe('jdbc');
  expect(connectorIdForDataSourceType('CLICKHOUSE')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('DORIS')).toBe('doris');
  expect(connectorIdForNewDataSourceType('STARROCKS')).toBe('starrocks');
  expect(connectorIdForNewDataSourceType('CLICKHOUSE')).toBe('clickhouse');
  expect(connectorIdForNewDataSourceType('TIDB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('TI-DB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('GOLDENDB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('GOLDEN-DB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('ZTE-GOLDENDB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('GBASE8C')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('GBASE-8C')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('GBASE8A')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('GBASE-8A')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('GBASE8S')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('GBASE-8S')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('HANA')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('SAP-HANA')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('SAPHANA')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('YASHANDB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('HGDB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('XUGUDB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('DUCK_DB')).toBe('jdbc');
  expect(connectorIdForNewDataSourceType('ES7')).toBe('elasticsearch7');
  expect(connectorIdForNewDataSourceType('ELASTICSEARCH_8')).toBe('elasticsearch8');
});

test('resolves datasource aliases from one frontend registry', () => {
  expect(connectorIdForDataSourceType('POSTGRESQL')).toBe('jdbc');
  expect(connectorIdForDataSourceType('TIDB')).toBe('jdbc');
  expect(connectorIdForDataSourceType('GOLDENDB')).toBe('jdbc');
  expect(connectorIdForDataSourceType('GOLDEN-DB')).toBe('jdbc');
  expect(connectorIdForDataSourceType('ZTE-GOLDENDB')).toBe('jdbc');
  expect(connectorIdForDataSourceType('GBASE-8C')).toBe('jdbc');
  expect(connectorIdForDataSourceType('GBASE-8A')).toBe('jdbc');
  expect(connectorIdForDataSourceType('GBASE-8S')).toBe('jdbc');
  expect(connectorIdForDataSourceType('HANA')).toBe('jdbc');
  expect(connectorIdForDataSourceType('SAP-HANA')).toBe('jdbc');
  expect(connectorIdForDataSourceType('SAPHANA')).toBe('jdbc');
  expect(connectorIdForDataSourceType('DB2')).toBe('jdbc');
  expect(connectorIdForDataSourceType('opengauss')).toBe('jdbc');
  expect(connectorIdForDataSourceType('SQLSERVER')).toBe('jdbc');
  expect(connectorIdForDataSourceType('MSSQL')).toBe('jdbc');
  expect(connectorIdForDataSourceType('OCEANBASE')).toBe('jdbc');
  expect(connectorIdForDataSourceType('YASDB')).toBe('jdbc');
  expect(connectorIdForDataSourceType('HIGH_GO')).toBe('jdbc');
  expect(connectorIdForDataSourceType('INTERSYSTEMS_IRIS')).toBe('jdbc');
  expect(connectorIdForDataSourceType('XUGUDB')).toBe('jdbc');
  expect(connectorIdForDataSourceType('DUCK_DB')).toBe('jdbc');
  expect(connectorIdForDataSourceType('HTTP')).toBe('http');
  expect(connectorIdForDataSourceType('custom-api')).toBe('custom_api');
  expect(defaultOfflineSyncConnectorProfile('postgres')).toMatchObject({
    profileId: 'postgresql-jdbc',
    dbType: 'POSTGRE_SQL',
  });
  expect(defaultOfflineSyncConnectorProfile('ZTE-GOLDENDB')).toMatchObject({
    profileId: 'goldendb-jdbc',
    dbType: 'GOLDENDB',
  });
  expect(defaultOfflineSyncConnectorProfile('GBASE-8C')).toMatchObject({
    profileId: 'gbase8c-jdbc',
    dbType: 'GBASE8C',
  });
  expect(defaultOfflineSyncConnectorProfile('SAPHANA')).toMatchObject({
    profileId: 'hana-jdbc',
    dbType: 'HANA',
  });
  expect(defaultOfflineSyncConnectorProfile('ES8')).toMatchObject({
    dbType: 'ELASTICSEARCH8',
    sourceConnectorId: 'elasticsearch8',
  });
});

test('keeps multi-table guide and target DDL/write policy profile driven', () => {
  expect(isGuideMultiEnabledForDataSourceType('MYSQL')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('TIDB')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('GOLDENDB')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('GBASE8C')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('GBASE8A')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('GBASE8S')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('HANA')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('SAP-HANA')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('YASHAN_DB')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('DUCKDB')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('DORIS')).toBe(true);
  expect(isGuideMultiEnabledForDataSourceType('ELASTICSEARCH7')).toBe(false);
  expect(isGuideMultiEnabledForDataSourceType('ES8')).toBe(false);

  expect(isAutoCreateTableEnabledForDataSourceType('MYSQL')).toBe(true);
  expect(isAutoCreateTableEnabledForDataSourceType('TIDB')).toBe(true);
  expect(isAutoCreateTableEnabledForDataSourceType('GOLDENDB')).toBe(false);
  expect(isAutoCreateTableEnabledForDataSourceType('GOLDEN-DB')).toBe(false);
  expect(isAutoCreateTableEnabledForDataSourceType('GBASE8C')).toBe(true);
  expect(isAutoCreateTableEnabledForDataSourceType('GBASE8A')).toBe(true);
  expect(isAutoCreateTableEnabledForDataSourceType('GBASE8S')).toBe(true);
  expect(isAutoCreateTableEnabledForDataSourceType('HANA')).toBe(true);

  expect(isUpsertEnabledForDataSourceType('MYSQL')).toBe(true);
  expect(isUpsertEnabledForDataSourceType('GBASE8C')).toBe(false);
  expect(isUpsertEnabledForDataSourceType('GBASE-8A')).toBe(false);
  expect(isUpsertEnabledForDataSourceType('GBASE-8S')).toBe(false);
});
