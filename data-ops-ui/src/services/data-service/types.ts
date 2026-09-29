import type { ApiResponse } from '@/services/http/response';

export type CommonApiResponse<T> = ApiResponse<T>;

export interface DataSourceOption {
  label: string;
  value: string;
  dbType?: string;
}

export interface DataServiceSource {
  sourceType: string;
  sourceRef: string;
  name: string;
  sourceKind: string;
  status: string;
  sourceRevisionId: number;
  sourceRevisionNo: number;
  dataSourceId: number;
  maxRows?: number;
  timeoutSeconds?: number;
  defaultPath: string;
  description?: string;
  updateTime?: string;
}

export interface DataServiceSourcePage {
  records: DataServiceSource[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface DataServiceSourceQuery {
  sourceType: string;
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
}

export interface DataServicePublishPayload {
  sourceType: string;
  sourceRef: string;
  name?: string;
  path?: string;
  maxRows?: number;
  timeoutSeconds?: number;
  enabled?: boolean;
  description?: string;
}

export interface DataServiceUpdatePayload {
  name: string;
  path: string;
  maxRows?: number;
  timeoutSeconds?: number;
  enabled?: boolean;
  description?: string;
}

export type DataServiceAuthMode = 'NONE' | 'API_KEY';
export type DataServiceIpAccessMode = 'NONE' | 'ALLOWLIST' | 'DENYLIST';
export type DataServiceIpAccessRuleType = 'ALLOWLIST' | 'DENYLIST';

export type DataServiceSchemaType =
  | 'STRING'
  | 'INTEGER'
  | 'NUMBER'
  | 'BOOLEAN'
  | 'DATE'
  | 'DATETIME'
  | 'OBJECT';

export interface DataServiceApi {
  id: number;
  name: string;
  path: string;
  runtimePath: string;
  dataSourceId: number;
  sql: string;
  parameterNames: string[];
  maxRows: number;
  timeoutSeconds: number;
  enabled: boolean;
  authMode: DataServiceAuthMode;
  description?: string;
  sourceType?: string;
  sourceRef?: string;
  sourceRevisionId?: number;
  sourceRevisionNo?: number;
  createTime?: string;
  updateTime?: string;
}

export interface DataServiceRuntimeConfig {
  cacheEnabled: boolean;
  cacheTtlSeconds: number;
  cacheMaxEntries: number;
  circuitBreakerEnabled: boolean;
  failureThreshold: number;
  recoverySeconds: number;
}

export type DataServiceRuntimeMetricsScope =
  | 'LOCAL'
  | 'CLUSTER_INVOCATION_LOCAL_RESILIENCE';

export interface DataServiceRuntimeStatus extends DataServiceRuntimeConfig {
  apiId: number;
  cacheEntries: number;
  circuitState: 'DISABLED' | 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  circuitOpenUntil?: string | null;
  totalCalls: number;
  successCalls: number;
  failureCalls: number;
  cacheHits: number;
  circuitRejected: number;
  successRate: number;
  cacheHitRate: number;
  averageDurationMs: number;
  p95DurationMs: number;
  lastSuccessAt?: string | null;
  lastFailureAt?: string | null;
  metricsScope?: DataServiceRuntimeMetricsScope;
}

export interface DataServiceParameterDoc {
  name: string;
  type: Exclude<DataServiceSchemaType, 'OBJECT'>;
  required: boolean;
  description?: string | null;
  example?: string | null;
}

export interface DataServiceResponseFieldDoc {
  name: string;
  type: DataServiceSchemaType;
  nullable: boolean;
  description?: string | null;
  example?: string | null;
}

export interface DataServiceDocumentation {
  apiId: number;
  name: string;
  runtimePath: string;
  authMode: DataServiceAuthMode;
  description?: string | null;
  documented: boolean;
  schemaStale: boolean;
  parameters: DataServiceParameterDoc[];
  responseFields: DataServiceResponseFieldDoc[];
  updateTime?: string | null;
}

export interface DataServiceDocumentationInput {
  parameters: DataServiceParameterDoc[];
  responseFields: DataServiceResponseFieldDoc[];
}

export interface DataServiceApiKey {
  id: number;
  apiId: number;
  name: string;
  keyPrefix: string;
  enabled: boolean;
  rateLimitPerMinute: number;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  createTime?: string;
  updateTime?: string;
}

export interface DataServiceApiKeyInput {
  name: string;
  rateLimitPerMinute?: number;
  expiresAt?: string | null;
}

export interface DataServiceApiKeyUpdate extends DataServiceApiKeyInput {
  expiresAtSet: boolean;
}

export interface CreatedDataServiceApiKey {
  key: DataServiceApiKey;
  secret: string;
}

export interface DataServiceIpAccessRule {
  id: number;
  apiId: number;
  ruleType: DataServiceIpAccessRuleType;
  networkCidr: string;
  description?: string | null;
  enabled: boolean;
  expiresAt?: string | null;
  createTime?: string;
  updateTime?: string;
}

export interface DataServiceIpAccessPolicy {
  mode: DataServiceIpAccessMode;
  rules: DataServiceIpAccessRule[];
}

export interface DataServiceIpAccessRuleInput {
  ruleType: DataServiceIpAccessRuleType;
  networkCidr: string;
  description?: string | null;
  enabled?: boolean;
  expiresAt?: string | null;
}

export interface DataServiceQueryResult {
  columns: string[];
  rows: Record<string, unknown>[];
  truncated: boolean;
  rowCount: number;
  durationMs: number;
}

export interface DataServiceCallLog {
  id: number;
  apiId: number;
  serviceName: string;
  servicePath: string;
  callerType: 'LEGACY' | 'PUBLIC' | 'API_KEY' | 'CONSOLE';
  apiKeyId?: number | null;
  apiKeyName?: string | null;
  apiKeyPrefix?: string | null;
  paramsJson?: string;
  success: boolean;
  durationMs: number;
  rowCount: number;
  errorMessage?: string;
  createTime?: string;
}
