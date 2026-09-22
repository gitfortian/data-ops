import HttpUtils from '@/utils/HttpUtils';

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
  RealtimeJobChange,
  RealtimeJobPage,
  RealtimeObservability,
  RealtimePageQuery,
  RealtimeRuntimeLog,
  RealtimeStreamHandlers,
  RuntimeCapabilities,
} from './types';

const REALTIME_SYNC_API = '/api/v1/realtime-sync';
const DATA_SOURCE_API = '/api/v1/data-source';
const COMPUTE_ENVIRONMENT_API = '/api/v1/compute-environments';

const queryString = (params: object) => {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).length > 0) {
      search.set(key, String(value));
    }
  });
  const result = search.toString();
  return result ? `?${result}` : '';
};

const needsIdempotencyKey = (action: RealtimeAction) =>
  action === 'start' ||
  action === 'restart-execution' ||
  action === 'apply-published-version';

const createIdempotencyKey = () =>
  globalThis.crypto?.randomUUID?.() ??
  `realtime-${Date.now()}-${Math.random().toString(16).slice(2)}`;

export const listRealtimeSyncTasks = (
  query: RealtimePageQuery,
): Promise<RealtimeJobPage> =>
  HttpUtils.getData<RealtimeJobPage>(
    `${REALTIME_SYNC_API}${queryString(query)}`,
  );

export const getRealtimeSyncTask = (id: number): Promise<RealtimeJob> =>
  HttpUtils.getData<RealtimeJob>(`${REALTIME_SYNC_API}/${id}`);

export const createRealtimeSyncBasicTask = (
  payload: RealtimeBasicDefinitionPayload,
): Promise<number> => HttpUtils.postData<number>(REALTIME_SYNC_API, payload);

export const createRealtimeSyncDraft = (
  payload: RealtimeDefinitionPayload,
): Promise<number> =>
  HttpUtils.postData<number>(`${REALTIME_SYNC_API}/draft`, payload);

export const updateRealtimeSyncTask = (
  id: number,
  payload: RealtimeDefinitionPayload,
): Promise<number> =>
  HttpUtils.putData<number>(`${REALTIME_SYNC_API}/${id}`, payload);

export const validateRealtimeSyncDefinition = (
  spec: CdcPipelineSpec,
  runtimeEnvironmentId: number,
): Promise<DefinitionValidationResult> =>
  HttpUtils.postData<DefinitionValidationResult>(
    `${REALTIME_SYNC_API}/spec/validate`,
    { spec, runtimeEnvironmentId },
  );

export const parseRealtimeSyncYaml = (
  yaml: string,
): Promise<CdcPipelineSpec> =>
  HttpUtils.postData<CdcPipelineSpec>(`${REALTIME_SYNC_API}/yaml/parse`, {
    yaml,
  });

export const renderRealtimeSyncYaml = (
  spec: CdcPipelineSpec,
): Promise<string> =>
  HttpUtils.postData<{ yaml: string }>(
    `${REALTIME_SYNC_API}/yaml/render`,
    { spec },
  ).then((result) => result.yaml);

export const performRealtimeSyncAction = (
  id: number,
  action: RealtimeAction,
): Promise<RealtimeExecution | boolean> =>
  HttpUtils.postData<RealtimeExecution | boolean>(
    `${REALTIME_SYNC_API}/${id}/${action}`,
    {},
    needsIdempotencyKey(action)
      ? {
          headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': createIdempotencyKey(),
          },
        }
      : undefined,
  );

export const deleteRealtimeSyncTask = async (id: number): Promise<void> => {
  await HttpUtils.deleteData<boolean>(`${REALTIME_SYNC_API}/${id}`);
};

export const listRealtimeSyncEvents = (
  id: number,
): Promise<RealtimeEvent[]> =>
  HttpUtils.getData<RealtimeEvent[]>(`${REALTIME_SYNC_API}/${id}/events`);

export const getRealtimeSyncObservability = (
  id: number,
): Promise<RealtimeObservability> =>
  HttpUtils.getData<RealtimeObservability>(
    `${REALTIME_SYNC_API}/${id}/observability`,
  );

export const getRealtimeSyncSubmissionLog = (
  id: number,
  tail = 500,
): Promise<string> =>
  HttpUtils.getData<{ logs: string }>(
    `${REALTIME_SYNC_API}/${id}/logs/submission${queryString({ tail })}`,
  ).then((result) => result.logs);

export const getRealtimeSyncRuntimeLog = (
  id: number,
  maxExceptions = 50,
): Promise<RealtimeRuntimeLog> =>
  HttpUtils.getData<RealtimeRuntimeLog>(
    `${REALTIME_SYNC_API}/${id}/logs/runtime${queryString({ maxExceptions })}`,
  );

export const listRealtimeComputeEnvironments = (): Promise<
  ComputeEnvironmentOption[]
> => HttpUtils.getData<ComputeEnvironmentOption[]>(COMPUTE_ENVIRONMENT_API);

export const getRealtimeRuntimeCapabilities = async (
  environmentId?: number,
): Promise<RuntimeCapabilities> => {
  let resolvedEnvironmentId = environmentId;
  if (!resolvedEnvironmentId) {
    const environments = await listRealtimeComputeEnvironments();
    resolvedEnvironmentId =
      environments.find(
        (item) => item.defaultEnvironment && item.enabled,
      )?.id ?? environments.find((item) => item.enabled)?.id;
  }
  if (!resolvedEnvironmentId) {
    throw new Error('暂无已启用的 Flink CDC 运行环境');
  }
  return HttpUtils.getData<RuntimeCapabilities>(
    `${REALTIME_SYNC_API}/runtime/capabilities${queryString({
      environmentId: resolvedEnvironmentId,
    })}`,
  );
};

export const listRealtimeDataSources = (): Promise<DataSourceOption[]> =>
  HttpUtils.getData<DataSourceOption[]>(`${DATA_SOURCE_API}/option`);

export const listRealtimeCatalogTables = (
  dataSourceId: number,
): Promise<DataSourceCatalogTable[]> =>
  HttpUtils.getData<DataSourceCatalogTable[]>(
    `${DATA_SOURCE_API}/catalog/${dataSourceId}/tables`,
  );

export const listRealtimeCatalogColumns = (
  dataSourceId: number,
  table: DataSourceCatalogTable,
): Promise<DataSourceCatalogColumn[]> =>
  HttpUtils.getData<DataSourceCatalogColumn[]>(
    `${DATA_SOURCE_API}/catalog/${dataSourceId}/columns${queryString({
      database: table.database,
      schema: table.schema,
      table: table.name,
    })}`,
  );

export const subscribeRealtimeSyncChanges = (
  handlers: RealtimeStreamHandlers,
): (() => void) => {
  if (typeof EventSource === 'undefined') {
    handlers.onError?.();
    return () => undefined;
  }

  let source: EventSource;
  try {
    source = new EventSource(`${REALTIME_SYNC_API}/stream`);
  } catch {
    handlers.onError?.();
    return () => undefined;
  }

  source.onopen = () => handlers.onOpen?.();
  source.onerror = () => handlers.onError?.();
  source.addEventListener('realtime', (event) => {
    try {
      handlers.onChange?.(
        JSON.parse((event as MessageEvent<string>).data) as RealtimeJobChange,
      );
    } catch {
      handlers.onInvalidMessage?.();
    }
  });

  return () => source.close();
};
