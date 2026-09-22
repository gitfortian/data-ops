import type {
  OfflineConnectorRole,
  OfflineConnectorRoleRuntime,
  OfflineConnectorRuntimeSnapshot,
} from '@/services/batch-link-up';

import {
  isAutoCreateTableEnabledForDataSourceType,
  isUpsertEnabledForDataSourceType,
} from '../connectorProfiles';
import type { SyncEditorState } from './model';

export const CONNECTOR_CAPABILITY = {
  TABLE_SCHEMA_DISCOVERY: 'TABLE_SCHEMA_DISCOVERY',
  MULTI_TABLE: 'MULTI_TABLE',
  CUSTOM_SQL: 'CUSTOM_SQL',
  PARTITION_SPLIT: 'PARTITION_SPLIT',
  UPSERT: 'UPSERT',
  AUTO_CREATE_TABLE: 'AUTO_CREATE_TABLE',
  DIRTY_DATA_HANDLING: 'DIRTY_DATA_HANDLING',
  TWO_PHASE_COMMIT: 'TWO_PHASE_COMMIT',
} as const;

export type ConnectorCapability =
  (typeof CONNECTOR_CAPABILITY)[keyof typeof CONNECTOR_CAPABILITY];

export interface EndpointCapabilityState {
  connectorId: string;
  role: OfflineConnectorRole;
  runtimeChecked: boolean;
  workerReachable: boolean;
  stale: boolean;
  metadataKnown: boolean;
  available: boolean;
  capabilities: string[];
  runtime?: OfflineConnectorRoleRuntime;
}

const NATIVE_NO_OVERWRITE_SINKS = new Set([
  'doris',
  'starrocks',
  'clickhouse',
]);

const normalizeConnectorId = (value: string) =>
  String(value || '').trim().toLowerCase();

const normalizeCapability = (value: string) =>
  String(value || '').trim().toUpperCase();

export const resolveEndpointCapability = (
  snapshot: OfflineConnectorRuntimeSnapshot | null | undefined,
  connectorId: string,
  role: OfflineConnectorRole,
): EndpointCapabilityState => {
  const normalizedId = normalizeConnectorId(connectorId);
  const runtime = snapshot?.connectors?.find(
    (item) =>
      normalizeConnectorId(item.connectorId) === normalizedId &&
      item.role === role,
  );
  const capabilities = Array.from(
    new Set(
      (runtime?.capabilities || [])
        .map(normalizeCapability)
        .filter(Boolean),
    ),
  ).sort();

  return {
    connectorId: normalizedId,
    role,
    runtimeChecked: Boolean(snapshot),
    workerReachable: snapshot?.reachable === true,
    stale: snapshot?.stale === true,
    metadataKnown: Boolean(runtime),
    available: Boolean(snapshot?.reachable && runtime?.available),
    capabilities,
    runtime,
  };
};

export const hasCapability = (
  state: EndpointCapabilityState,
  capability: ConnectorCapability | string,
) => state.capabilities.includes(normalizeCapability(capability));

/**
 * UI compatibility rule: before runtime metadata is known, keep the historical controls visible.
 * Once the Worker has described a connector role, the capability list becomes authoritative.
 */
export const allowsCapability = (
  state: EndpointCapabilityState,
  capability: ConnectorCapability | string,
) => !state.metadataKnown || hasCapability(state, capability);

/** Overwrite is a control-plane write-mode semantic, not a Link-Up capability enum today. */
export const allowsOverwrite = (state: EndpointCapabilityState) =>
  !NATIVE_NO_OVERWRITE_SINKS.has(normalizeConnectorId(state.connectorId));

export const isDefinitivelyUnavailable = (
  state: EndpointCapabilityState,
) => state.runtimeChecked && state.workerReachable && !state.available;

export const validateEditorCapabilities = (
  editor: SyncEditorState,
  snapshot: OfflineConnectorRuntimeSnapshot | null | undefined,
): string[] => {
  const sourceConfig = editor.source.config || {};
  const sinkConfig = editor.sink.config || {};
  const writeMode = String(sinkConfig.writeMode || '').toLowerCase();
  const upsertEnabled = isUpsertEnabledForDataSourceType(editor.sink.dbType);
  const errors: string[] = [];

  // Datasource-profile policies are control-plane contracts and do not depend on Worker reachability.
  if (
    Boolean(sinkConfig.autoCreateTable) &&
    !isAutoCreateTableEnabledForDataSourceType(editor.sink.dbType)
  ) {
    errors.push(
      `${editor.sink.dbType || '当前目标数据源'} Stage 1 仅支持写入已有表，请关闭自动建表`,
    );
  }
  if (writeMode === 'upsert' && !upsertEnabled) {
    errors.push(
      `${editor.sink.dbType || '当前目标数据源'} 当前离线 Sink 不支持 Upsert/MERGE，请选择 Append 或 Overwrite`,
    );
  }

  if (!snapshot || !snapshot.reachable) {
    return errors;
  }

  const source = resolveEndpointCapability(
    snapshot,
    editor.source.connectorId,
    'SOURCE',
  );
  const sink = resolveEndpointCapability(
    snapshot,
    editor.sink.connectorId,
    'SINK',
  );

  if (!source.available) {
    errors.push(
      `当前 Link-Up Worker 未加载 Source Connector：${editor.source.connectorId || 'UNKNOWN'}`,
    );
  }
  if (!sink.available) {
    errors.push(
      `当前 Link-Up Worker 未加载 Sink Connector：${editor.sink.connectorId || 'UNKNOWN'}`,
    );
  }
  if (errors.some((message) => message.startsWith('当前 Link-Up Worker 未加载'))) {
    return errors;
  }

  if (editor.mode === 'GUIDE_MULTI') {
    if (!hasCapability(source, CONNECTOR_CAPABILITY.MULTI_TABLE)) {
      errors.push(
        `Source Connector ${source.connectorId} 不支持多表同步（MULTI_TABLE）`,
      );
    }
    if (!hasCapability(sink, CONNECTOR_CAPABILITY.MULTI_TABLE)) {
      errors.push(
        `Sink Connector ${sink.connectorId} 不支持多表同步（MULTI_TABLE）`,
      );
    }
  }

  if (
    String(sourceConfig.readMode || '').toLowerCase() === 'sql' &&
    !hasCapability(source, CONNECTOR_CAPABILITY.CUSTOM_SQL)
  ) {
    errors.push(
      `Source Connector ${source.connectorId} 不支持自定义 SQL（CUSTOM_SQL）`,
    );
  }

  if (
    Boolean(sinkConfig.autoCreateTable) &&
    isAutoCreateTableEnabledForDataSourceType(editor.sink.dbType) &&
    !hasCapability(sink, CONNECTOR_CAPABILITY.AUTO_CREATE_TABLE)
  ) {
    errors.push(
      `Sink Connector ${sink.connectorId} 不支持自动建表（AUTO_CREATE_TABLE）`,
    );
  }

  if (
    writeMode === 'upsert' &&
    upsertEnabled &&
    !hasCapability(sink, CONNECTOR_CAPABILITY.UPSERT)
  ) {
    errors.push(
      `Sink Connector ${sink.connectorId} 不支持 Upsert（UPSERT）`,
    );
  }

  if (writeMode === 'overwrite' && !allowsOverwrite(sink)) {
    errors.push(
      `Sink Connector ${sink.connectorId} 当前 Native 实现不支持覆盖写入（OVERWRITE）`,
    );
  }

  if (
    editor.channel.dirtyDataPolicy === 'skip' &&
    !hasCapability(sink, CONNECTOR_CAPABILITY.DIRTY_DATA_HANDLING)
  ) {
    errors.push(
      `Sink Connector ${sink.connectorId} 不支持跳过脏数据（DIRTY_DATA_HANDLING）`,
    );
  }

  return errors;
};
