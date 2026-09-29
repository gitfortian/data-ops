import type { OfflineConnectorRuntimeSnapshot } from '@/services/batch-link-up';

import {
  allowsCapability,
  allowsOverwrite,
  CONNECTOR_CAPABILITY,
  resolveEndpointCapability,
  validateEditorCapabilities,
} from './capabilities';
import type { SyncEditorState } from './model';

const editor = (): SyncEditorState => ({
  id: '1',
  mode: 'GUIDE_SINGLE',
  basic: { jobName: 'demo', jobDesc: '', mode: 'GUIDE_SINGLE' },
  source: {
    connectorId: 'jdbc',
    pluginName: 'JDBC-MYSQL',
    dbType: 'MYSQL',
    dataSourceId: '1',
    config: { readMode: 'table', table: 'a.orders' },
  },
  sink: {
    connectorId: 'jdbc',
    pluginName: 'JDBC-MYSQL',
    dbType: 'MYSQL',
    dataSourceId: '2',
    config: { table: 'b.orders', writeMode: 'append' },
  },
  channel: {
    parallelism: 1,
    speedLimitEnabled: false,
    recordsPerSecond: 10000,
    dirtyDataPolicy: 'stop',
    dirtyDataLimit: 0,
  },
  mapping: { columns: [] },
  schedule: { cron: '', enabled: false, retryOnFailure: false },
  notification: {
    enabled: true,
    triggers: ['FINAL_FAILURE'],
    recipientType: 'PROJECT_OWNER',
    recipientUserIds: [],
    inAppEnabled: true,
    alertEnabled: false,
    alertChannelIds: [],
  },
});

const snapshot = (
  sourceCapabilities: string[],
  sinkCapabilities: string[],
  connectorId = 'jdbc',
): OfflineConnectorRuntimeSnapshot => ({
  reachable: true,
  stale: false,
  connectors: [
    {
      connectorId,
      role: 'SOURCE',
      available: true,
      capabilities: sourceCapabilities,
    },
    {
      connectorId,
      role: 'SINK',
      available: true,
      capabilities: sinkCapabilities,
    },
  ],
  profiles: [],
});

describe('offline connector capabilities', () => {
  it('keeps legacy controls visible before runtime metadata is known', () => {
    const state = resolveEndpointCapability(null, 'jdbc', 'SOURCE');

    expect(state.metadataKnown).toBe(false);
    expect(allowsCapability(state, CONNECTOR_CAPABILITY.CUSTOM_SQL)).toBe(true);
  });

  it('accepts a configuration covered by the live connector capabilities', () => {
    const value = editor();
    value.mode = 'GUIDE_MULTI';
    value.basic.mode = 'GUIDE_MULTI';
    value.sink.config = {
      database: 'ods',
      writeMode: 'upsert',
      primaryKey: 'id',
      autoCreateTable: true,
    };
    value.channel.dirtyDataPolicy = 'skip';

    const errors = validateEditorCapabilities(
      value,
      snapshot(
        ['MULTI_TABLE', 'CUSTOM_SQL'],
        ['MULTI_TABLE', 'UPSERT', 'AUTO_CREATE_TABLE', 'DIRTY_DATA_HANDLING'],
      ),
    );

    expect(errors).toEqual([]);
  });

  it('keeps GoldenDB Stage 1 on existing target tables even when JDBC advertises auto create', () => {
    const value = editor();
    value.sink.dbType = 'GOLDENDB';
    value.sink.pluginName = 'JDBC-GOLDENDB';
    value.sink.config = {
      targetTableName: 'orders',
      autoCreateTable: true,
      writeMode: 'append',
    };

    const errors = validateEditorCapabilities(
      value,
      snapshot(
        ['TABLE_SCHEMA_DISCOVERY'],
        ['AUTO_CREATE_TABLE', 'UPSERT'],
      ),
    );

    expect(errors).toContain(
      'GOLDENDB Stage 1 仅支持写入已有表，请关闭自动建表',
    );
  });

  it('allows GBase automatic table creation but blocks unsupported upsert independently of Worker state', () => {
    const value = editor();
    value.sink.dbType = 'GBASE8C';
    value.sink.pluginName = 'JDBC-GBASE8C';
    value.sink.config = {
      targetTableName: 'orders',
      autoCreateTable: true,
      writeMode: 'upsert',
      primaryKey: 'id',
    };

    expect(validateEditorCapabilities(value, null)).toEqual([
      'GBASE8C 当前离线 Sink 不支持 Upsert/MERGE，请选择 Append 或 Overwrite',
    ]);
    expect(
      validateEditorCapabilities(
        value,
        snapshot(
          ['TABLE_SCHEMA_DISCOVERY'],
          ['AUTO_CREATE_TABLE', 'UPSERT'],
        ),
      ),
    ).toEqual([
      'GBASE8C 当前离线 Sink 不支持 Upsert/MERGE，请选择 Append 或 Overwrite',
    ]);
  });

  it('requires both connector roles to advertise MULTI_TABLE', () => {
    const value = editor();
    value.mode = 'GUIDE_MULTI';
    value.basic.mode = 'GUIDE_MULTI';

    const errors = validateEditorCapabilities(
      value,
      snapshot(['MULTI_TABLE'], ['UPSERT']),
    );

    expect(errors).toContain(
      'Sink Connector jdbc 不支持多表同步（MULTI_TABLE）',
    );
  });

  it('rejects active SQL, auto-create, upsert and dirty-data features when absent', () => {
    const value = editor();
    value.source.config = { readMode: 'sql', sql: 'select 1' };
    value.sink.config = {
      autoCreateTable: true,
      targetTableName: 'orders',
      writeMode: 'upsert',
      primaryKey: 'id',
    };
    value.channel.dirtyDataPolicy = 'skip';

    const errors = validateEditorCapabilities(value, snapshot([], []));

    expect(errors).toEqual(
      expect.arrayContaining([
        'Source Connector jdbc 不支持自定义 SQL（CUSTOM_SQL）',
        'Sink Connector jdbc 不支持自动建表（AUTO_CREATE_TABLE）',
        'Sink Connector jdbc 不支持 Upsert（UPSERT）',
        'Sink Connector jdbc 不支持跳过脏数据（DIRTY_DATA_HANDLING）',
      ]),
    );
  });

  it('treats the current native sinks as no-overwrite implementations', () => {
    const value = editor();
    value.source.connectorId = 'clickhouse';
    value.sink.connectorId = 'clickhouse';
    value.sink.config.writeMode = 'overwrite';
    const runtime = snapshot(['TABLE_SCHEMA_DISCOVERY'], [], 'clickhouse');

    expect(
      allowsOverwrite(resolveEndpointCapability(runtime, 'clickhouse', 'SINK')),
    ).toBe(false);
    expect(validateEditorCapabilities(value, runtime)).toContain(
      'Sink Connector clickhouse 当前 Native 实现不支持覆盖写入（OVERWRITE）',
    );
  });

  it('does not treat an unreachable stale snapshot as authoritative', () => {
    const value = editor();
    value.source.config = { readMode: 'sql', sql: 'select 1' };
    const stale = snapshot([], []);
    stale.reachable = false;
    stale.stale = true;

    expect(validateEditorCapabilities(value, stale)).toEqual([]);
  });
});
