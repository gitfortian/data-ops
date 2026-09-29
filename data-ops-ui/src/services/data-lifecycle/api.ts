import HttpUtils from '@/utils/HttpUtils';
import type {
  DispatchOutcome,
  DispatchRecord,
  DispatchRecordQueryParams,
  LayerTemplate,
  LifecyclePageResult,
  ModelTtlResolution,
  MonitorModelQueryParams,
  MonitorModelView,
  MonitorSummary,
  PolicyPublishResult,
  PolicyQueryParams,
  PolicyRecord,
  PolicyUpsertParams,
  PolicyVersionDetail,
  PolicyVersionSummary,
  PreviewBatch,
  StorageStats,
  TableStorage,
  TrendPoint,
} from './types';

const LIFECYCLE_API_PREFIX = '/api/v1/lifecycle';

type RawPage<T> = {
  bizData?: T[];
  records?: T[];
  total?: number;
  pages?: number;
  pageNo?: number;
  pageSize?: number;
  pagination?: { pageNo?: number; pageSize?: number; total?: number; pages?: number };
};

const toPageResult = <T>(raw: RawPage<T>, pageSize: number): LifecyclePageResult<T> => {
  const pagination = raw?.pagination ?? {};
  return {
    records: raw?.bizData ?? raw?.records ?? [],
    total: pagination.total ?? raw?.total ?? 0,
    pages: pagination.pages ?? raw?.pages ?? 0,
    pageNo: pagination.pageNo ?? raw?.pageNo ?? 1,
    pageSize: pagination.pageSize ?? raw?.pageSize ?? pageSize,
  };
};

// ---------- 策略 ----------

export const pagePolicies = async (params: PolicyQueryParams): Promise<LifecyclePageResult<PolicyRecord>> =>
  toPageResult(
    await HttpUtils.postData<RawPage<PolicyRecord>>(`${LIFECYCLE_API_PREFIX}/policies/page`, params),
    params.pageSize,
  );

export const getPolicy = (id: number) =>
  HttpUtils.getData<PolicyRecord>(`${LIFECYCLE_API_PREFIX}/policies/${id}`);

export const createPolicy = (params: PolicyUpsertParams) =>
  HttpUtils.postData<PolicyRecord>(`${LIFECYCLE_API_PREFIX}/policies`, params);

export const updatePolicy = (id: number, params: PolicyUpsertParams) =>
  HttpUtils.putData<PolicyRecord>(`${LIFECYCLE_API_PREFIX}/policies/${id}`, params);

export const deletePolicy = (id: number) =>
  HttpUtils.deleteData<boolean>(`${LIFECYCLE_API_PREFIX}/policies/${id}`);

export const changePolicyStatus = (id: number, status: 'ENABLED' | 'DISABLED') =>
  HttpUtils.postData<PolicyRecord>(`${LIFECYCLE_API_PREFIX}/policies/${id}/status`, { status });

// ---------- 策略发布/版本(W1-1 多版本契约 C3) ----------

export const publishPolicy = (id: number) =>
  HttpUtils.postData<PolicyPublishResult>(`${LIFECYCLE_API_PREFIX}/policies/${id}/publish`);

export const offlinePolicy = (id: number) =>
  HttpUtils.postData<boolean>(`${LIFECYCLE_API_PREFIX}/policies/${id}/offline`);

export const listPolicyVersions = (id: number) =>
  HttpUtils.getData<PolicyVersionSummary[]>(`${LIFECYCLE_API_PREFIX}/policies/${id}/versions`);

export const getPolicyVersion = (id: number, versionNo: number) =>
  HttpUtils.getData<PolicyVersionDetail>(
    `${LIFECYCLE_API_PREFIX}/policies/${id}/versions/${versionNo}`,
  );

export const rollbackPolicyVersion = (id: number, versionNo: number) =>
  HttpUtils.postData<PolicyPublishResult>(
    `${LIFECYCLE_API_PREFIX}/policies/${id}/versions/${versionNo}/rollback`,
  );

/** 一键初始化分层默认策略,返回新建条数。 */
export const initializeLayerDefaults = () =>
  HttpUtils.postData<number>(`${LIFECYCLE_API_PREFIX}/policies/initialize-layer-defaults`);

export const listLayerTemplates = () =>
  HttpUtils.getData<LayerTemplate[]>(`${LIFECYCLE_API_PREFIX}/policies/layer-template`);

// ---------- 模型生命周期 ----------

export const getModelLifecycle = (modelId: number) =>
  HttpUtils.getData<ModelTtlResolution>(`${LIFECYCLE_API_PREFIX}/models/${modelId}/lifecycle`);

export const bindModelPolicy = (modelId: number, policyId: number) =>
  HttpUtils.putData<ModelTtlResolution>(`${LIFECYCLE_API_PREFIX}/models/${modelId}/lifecycle/binding`, {
    policyId,
  });

export const unbindModelPolicy = (modelId: number) =>
  HttpUtils.deleteData<ModelTtlResolution>(`${LIFECYCLE_API_PREFIX}/models/${modelId}/lifecycle/binding`);

// ---------- 预览 + 下发 ----------

export const previewDispatch = (modelIds: number[]) =>
  HttpUtils.postData<PreviewBatch>(`${LIFECYCLE_API_PREFIX}/models/lifecycle/preview`, { modelIds });

export const confirmDispatch = (modelIds: number[], confirmToken: string) =>
  HttpUtils.postData<DispatchOutcome[]>(`${LIFECYCLE_API_PREFIX}/models/lifecycle/dispatch`, {
    modelIds,
    confirmToken,
  });

// ---------- 下发记录 ----------

export const pageDispatchRecords = async (
  params: DispatchRecordQueryParams,
): Promise<LifecyclePageResult<DispatchRecord>> =>
  toPageResult(
    await HttpUtils.postData<RawPage<DispatchRecord>>(
      `${LIFECYCLE_API_PREFIX}/dispatch-records/page`,
      params,
    ),
    params.pageSize,
  );

export const retryDispatchRecord = (recordId: number) =>
  HttpUtils.postData<DispatchOutcome>(
    `${LIFECYCLE_API_PREFIX}/dispatch-records/${recordId}/retry`,
  );

// ---------- 监控 ----------

export const getMonitorSummary = () =>
  HttpUtils.getData<MonitorSummary>(`${LIFECYCLE_API_PREFIX}/monitor/summary`);

export const pageMonitorModels = async (
  params: MonitorModelQueryParams,
): Promise<LifecyclePageResult<MonitorModelView>> =>
  toPageResult(
    await HttpUtils.postData<RawPage<MonitorModelView>>(
      `${LIFECYCLE_API_PREFIX}/monitor/models/page`,
      params,
    ),
    params.pageSize,
  );

export const redispatchModel = (modelId: number) =>
  HttpUtils.postData<DispatchOutcome>(
    `${LIFECYCLE_API_PREFIX}/monitor/models/${modelId}/redispatch`,
  );

// ---------- 存储统计 ----------

export const getStorageStats = () =>
  HttpUtils.getData<StorageStats>(`${LIFECYCLE_API_PREFIX}/storage/stats`);

/**
 * 单表存储量：只读每日快照，绝不在读侧现拉 SHOW DATA。
 *
 * <p>{@code databaseName} 必填是硬要求——该表唯一键不含库名（plan §9 T16），
 * 同一数据源多库同名表会互相覆盖，少一个过滤条件就会拿到别的库的量。
 * 返回 null = 这张表还没有快照，与"读失败"不是一回事。
 */
export const getTableStorage = (params: {
  datasourceId?: number | null;
  databaseName: string;
  tableName: string;
}) => {
  const search = new URLSearchParams({ databaseName: params.databaseName, tableName: params.tableName });
  if (params.datasourceId != null) search.set('datasourceId', String(params.datasourceId));
  return HttpUtils.getData<TableStorage | null>(`${LIFECYCLE_API_PREFIX}/storage/table?${search}`);
};

export const getStorageTrend = (days = 30) =>
  HttpUtils.getData<TrendPoint[]>(
    `${LIFECYCLE_API_PREFIX}/storage/trend?${new URLSearchParams({ days: String(days) })}`,
  );

export const getStoragePrice = () =>
  HttpUtils.getData<{ pricePerGbMonth?: string | null }>(
    `${LIFECYCLE_API_PREFIX}/storage/setting`,
  );

export const updateStoragePrice = (pricePerGbMonth: string) =>
  HttpUtils.putData<string>(`${LIFECYCLE_API_PREFIX}/storage/setting`, { pricePerGbMonth });
