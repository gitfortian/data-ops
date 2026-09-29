import type { ApiResponse } from '@/services/http/response';
import { request } from '@umijs/max';

import type {
  CdcPipelineSpec,
  ComputeEnvironmentOption,
  DataSourceCatalogColumn,
  DataSourceCatalogTable,
  DataSourceOption,
  DefinitionValidationResult,
  RealtimeAction,
  RealtimeBasicDefinitionPayload,
  RealtimeDefinitionPayload,
  RealtimeEvent,
  RealtimeExecution,
  RealtimeJob,
  RealtimeJobPage,
  RealtimeObservability,
  RealtimePageQuery,
  RealtimeRuntimeLog,
  RuntimeCapabilities,
} from './types';

const PREFIX = '/api/v1/realtime-sync';
const DATA_SOURCE_PREFIX = '/api/v1/data-source';

const validateDefinition = (
  spec: CdcPipelineSpec,
  runtimeEnvironmentId: number,
) =>
  request<ApiResponse<DefinitionValidationResult>>(`${PREFIX}/spec/validate`, {
    method: 'POST',
    data: { spec, runtimeEnvironmentId },
  });

const environments = () =>
  request<ApiResponse<ComputeEnvironmentOption[]>>(
    '/api/v1/compute-environments',
  );

const capabilities = async (environmentId?: number) => {
  let resolvedEnvironmentId = environmentId;
  if (!resolvedEnvironmentId) {
    const response = await environments();
    const rows = response.data || [];
    resolvedEnvironmentId =
      rows.find((item) => item.defaultEnvironment && item.enabled)?.id ??
      rows.find((item) => item.enabled)?.id;
  }
  if (!resolvedEnvironmentId) {
    throw new Error('暂无已启用的 Flink CDC 运行环境');
  }
  return request<ApiResponse<RuntimeCapabilities>>(
    `${PREFIX}/runtime/capabilities`,
    { params: { environmentId: resolvedEnvironmentId } },
  );
};

const needsIdempotencyKey = (action: RealtimeAction) =>
  ['start', 'restart-execution', 'apply-published-version'].includes(action);

export const realtimeApi = {
  page: (params: RealtimePageQuery) =>
    request<ApiResponse<RealtimeJobPage>>(PREFIX, { params }),
  detail: (id: number) => request<ApiResponse<RealtimeJob>>(`${PREFIX}/${id}`),
  createBasic: (payload: RealtimeBasicDefinitionPayload) =>
    request<ApiResponse<number>>(PREFIX, { method: 'POST', data: payload }),
  create: (payload: RealtimeDefinitionPayload) =>
    request<ApiResponse<number>>(`${PREFIX}/draft`, {
      method: 'POST',
      data: payload,
    }),
  update: (id: number, payload: RealtimeDefinitionPayload) =>
    request<ApiResponse<number>>(`${PREFIX}/${id}`, {
      method: 'PUT',
      data: payload,
    }),
  validateDefinition,
  parseYaml: (yaml: string) =>
    request<ApiResponse<CdcPipelineSpec>>(`${PREFIX}/yaml/parse`, {
      method: 'POST',
      data: { yaml },
    }),
  renderYaml: (spec: CdcPipelineSpec) =>
    request<ApiResponse<{ yaml: string }>>(`${PREFIX}/yaml/render`, {
      method: 'POST',
      data: { spec },
    }),
  action: (id: number, action: RealtimeAction) =>
    request<ApiResponse<RealtimeExecution | boolean>>(
      `${PREFIX}/${id}/${action}`,
      {
        method: 'POST',
        headers: needsIdempotencyKey(action)
          ? { 'Idempotency-Key': crypto.randomUUID() }
          : undefined,
      },
    ),
  remove: (id: number) =>
    request<ApiResponse<boolean>>(`${PREFIX}/${id}`, { method: 'DELETE' }),
  events: (id: number) =>
    request<ApiResponse<RealtimeEvent[]>>(`${PREFIX}/${id}/events`),
  observability: (id: number) =>
    request<ApiResponse<RealtimeObservability>>(
      `${PREFIX}/${id}/observability`,
    ),
  submissionLog: (id: number, tail = 500) =>
    request<ApiResponse<{ logs: string }>>(
      `${PREFIX}/${id}/logs/submission`,
      { params: { tail } },
    ),
  runtimeLog: (id: number, maxExceptions = 50) =>
    request<ApiResponse<RealtimeRuntimeLog>>(`${PREFIX}/${id}/logs/runtime`, {
      params: { maxExceptions },
    }),
  capabilities,
  environments,
  dataSources: () =>
    request<ApiResponse<DataSourceOption[]>>(`${DATA_SOURCE_PREFIX}/option`),
  catalogTables: (dataSourceId: number) =>
    request<ApiResponse<DataSourceCatalogTable[]>>(
      `${DATA_SOURCE_PREFIX}/catalog/${dataSourceId}/tables`,
    ),
  catalogColumns: (
    dataSourceId: number,
    table: DataSourceCatalogTable,
  ) =>
    request<ApiResponse<DataSourceCatalogColumn[]>>(
      `${DATA_SOURCE_PREFIX}/catalog/${dataSourceId}/columns`,
      {
        params: {
          database: table.database || undefined,
          schema: table.schema || undefined,
          table: table.name,
        },
      },
    ),
};

export type { RealtimeAction, RealtimePageQuery } from './types';
