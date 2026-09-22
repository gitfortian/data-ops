import HttpUtils from '@/utils/HttpUtils';

import type {
  AffectedMetricRecord,
  DependencyChange,
  ImpactReport,
  LineageGraphView,
  MetricCreatePayload,
  MetricDependencyRecord,
  MetricPageParams,
  MetricPageResult,
  MetricRecord,
  MetricStats,
  MetricTagRecord,
  MetricUpdatePayload,
  MetricUsageRecord,
  MetricVersionRecord,
  UsageSummary,
} from './types';

const METRIC_API_PREFIX = '/api/v1/metrics';

// ── CRUD ──

export const pageMetrics = async (params: MetricPageParams): Promise<MetricPageResult> => {
  // Backend PagingData uses { bizData, pagination: { total, pages, pageNo, pageSize } }
  // which differs from the flat MetricPageResult shape expected by the frontend.
  const raw = await HttpUtils.postData<any>(`${METRIC_API_PREFIX}/page`, params);
  const pagination = raw?.pagination ?? {};
  return {
    records: raw?.bizData ?? raw?.records ?? [],
    total: pagination.total ?? raw?.total ?? 0,
    pages: pagination.pages ?? raw?.pages ?? 0,
    pageNo: pagination.pageNo ?? raw?.pageNo ?? 1,
    pageSize: pagination.pageSize ?? raw?.pageSize ?? params.pageSize,
  };
};

export const getMetric = (id: number): Promise<MetricRecord> =>
  HttpUtils.getData<MetricRecord>(`${METRIC_API_PREFIX}/${id}`);

export const createMetric = (payload: MetricCreatePayload): Promise<MetricRecord> =>
  HttpUtils.postData<MetricRecord>(METRIC_API_PREFIX, payload, { skipErrorHandler: true });

export const updateMetric = (id: number, payload: MetricUpdatePayload): Promise<MetricRecord> =>
  HttpUtils.putData<MetricRecord>(`${METRIC_API_PREFIX}/${id}`, payload, { skipErrorHandler: true });

export const changeMetricStatus = (id: number, status: string): Promise<MetricRecord> =>
  HttpUtils.postData<MetricRecord>(`${METRIC_API_PREFIX}/${id}/status`, { status });

export const deleteMetric = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${METRIC_API_PREFIX}/${id}`);

export const getMetricStats = (): Promise<MetricStats> =>
  HttpUtils.getData<MetricStats>(`${METRIC_API_PREFIX}/stats`);

// ── Tags ──

export const listMetricTags = (): Promise<MetricTagRecord[]> =>
  HttpUtils.getData<MetricTagRecord[]>(`${METRIC_API_PREFIX}/tags`);

export const createMetricTag = (tagName: string): Promise<MetricTagRecord> =>
  HttpUtils.postData<MetricTagRecord>(`${METRIC_API_PREFIX}/tags`, { tagName });

export const updateMetricTag = (id: number, tagName: string, sortOrder?: number): Promise<boolean> =>
  HttpUtils.putData<boolean>(`${METRIC_API_PREFIX}/tags/${id}`, { tagName, sortOrder });

export const deleteMetricTag = (id: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${METRIC_API_PREFIX}/tags/${id}`);

export const assignMetricTags = (metricId: number, tagIds: number[]): Promise<boolean> =>
  HttpUtils.postData<boolean>(`${METRIC_API_PREFIX}/tags/assign/${metricId}`, { tagIds });

export const removeMetricTag = (metricId: number, tagId: number): Promise<boolean> =>
  HttpUtils.deleteData<boolean>(`${METRIC_API_PREFIX}/tags/remove/${metricId}/${tagId}`);

export const getMetricTags = (metricId: number): Promise<number[]> =>
  HttpUtils.getData<number[]>(`${METRIC_API_PREFIX}/tags/metric/${metricId}`);

// ── Versions ──

export const listMetricVersions = (id: number): Promise<MetricVersionRecord[]> =>
  HttpUtils.getData<MetricVersionRecord[]>(`${METRIC_API_PREFIX}/${id}/versions`);

export const getMetricVersion = (id: number, version: number): Promise<MetricVersionRecord> =>
  HttpUtils.getData<MetricVersionRecord>(`${METRIC_API_PREFIX}/${id}/versions/${version}`);

// ── Lineage ──

export const getMetricLineage = (id: number): Promise<MetricDependencyRecord[]> =>
  HttpUtils.getData<MetricDependencyRecord[]>(`${METRIC_API_PREFIX}/${id}/lineage`);

export const getMetricGraph = (
  id: number,
  direction?: string,
  depth?: number,
): Promise<LineageGraphView | null> => {
  const params = new URLSearchParams();
  if (direction) params.set('direction', direction);
  if (depth) params.set('depth', String(depth));
  return HttpUtils.getData<LineageGraphView | null>(
    `${METRIC_API_PREFIX}/${id}/graph?${params.toString()}`,
  );
};

export const getMetricLineageAssetId = (id: number): Promise<number | null> =>
  HttpUtils.getData<number | null>(`${METRIC_API_PREFIX}/${id}/lineage/asset-id`);

// ── Usage ──

export const getMetricUsageSummary = (id: number): Promise<UsageSummary> =>
  HttpUtils.getData<UsageSummary>(`${METRIC_API_PREFIX}/${id}/usage/summary`);

export const getMetricUsageList = (id: number): Promise<MetricUsageRecord[]> =>
  HttpUtils.getData<MetricUsageRecord[]>(`${METRIC_API_PREFIX}/${id}/usage`);

// ── Impact ──

export const getMetricImpact = (metricId: number): Promise<ImpactReport> =>
  HttpUtils.getData<ImpactReport>(`${METRIC_API_PREFIX}/impact/${metricId}/upstream-changes`);

/** 反向影响分析:上游对象(MODEL/METRIC/CALIBER/UNIT)被哪些指标引用。 */
export const getAffectedMetrics = (dependencyType: string, dependencyId: number): Promise<AffectedMetricRecord[]> =>
  HttpUtils.getData<AffectedMetricRecord[]>(
    `${METRIC_API_PREFIX}/impact/affected?dependencyType=${encodeURIComponent(dependencyType)}&dependencyId=${dependencyId}`,
  );
