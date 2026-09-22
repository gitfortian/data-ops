import HttpUtils from '@/utils/HttpUtils';

export type OfflineConnectorRole = 'SOURCE' | 'SINK';

export interface OfflineConnectorRoleRuntime {
  connectorId: string;
  role: OfflineConnectorRole;
  available: boolean;
  schemaVersion?: string;
  schemaFingerprint?: string;
  implementationVersion?: string;
  capabilities: string[];
}

export interface OfflineConnectorRuntimeProfile {
  profileId: string;
  dbType: string;
  displayName?: string;
  pluginName?: string;
  defaultProfile: boolean;
  source: OfflineConnectorRoleRuntime;
  sink: OfflineConnectorRoleRuntime;
}

export interface OfflineConnectorRuntimeSnapshot {
  reachable: boolean;
  stale: boolean;
  syncedAtMillis?: number;
  checkedAtMillis?: number;
  errorMessage?: string;
  /** Every Source/Sink factory actually discovered from the configured Link-Up Worker. */
  connectors: OfflineConnectorRoleRuntime[];
  /** Yak Ops product profiles from the control-plane registry merged with runtime availability. */
  profiles: OfflineConnectorRuntimeProfile[];
}

export interface LinkUpConnectorOptionSchema {
  key: string;
  valueType?: string;
  javaType?: string;
  elementJavaType?: string;
  defaultValue?: unknown;
  allowedValues?: unknown[];
  description?: string;
  fallbackKeys?: string[];
  required?: boolean;
  sensitive?: boolean;
  semanticType?: string;
  scope?: string;
}

export type LinkUpConnectorSchema = Record<string, unknown> & {
  connectorId: string;
  role: OfflineConnectorRole;
  schemaVersion?: string;
  schemaFingerprint?: string;
  implementationClass?: string;
  implementationVersion?: string;
  capabilities?: string[];
  options?: LinkUpConnectorOptionSchema[];
  rules?: unknown[];
};

const CONNECTOR_RUNTIME_API = '/api/v1/job/batch-execution/connectors';

export const getOfflineSyncConnectorRuntime = (): Promise<OfflineConnectorRuntimeSnapshot> =>
  HttpUtils.getData<OfflineConnectorRuntimeSnapshot>(CONNECTOR_RUNTIME_API);

export const getOfflineSyncConnectorSchema = (
  connectorId: string,
  role: OfflineConnectorRole,
): Promise<LinkUpConnectorSchema> =>
  HttpUtils.getData<LinkUpConnectorSchema>(
    `${CONNECTOR_RUNTIME_API}/${encodeURIComponent(connectorId)}/schema?role=${encodeURIComponent(role)}`,
  );
