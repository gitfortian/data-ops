import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import { isQueryableDatasetSourceType } from './constants';
import type {
  DatasetField,
  DatasetFieldType,
  DatasetManagementDetail,
  DatasetManagementField,
  DatasetManagementItem,
  DatasetManagementVersion,
  DatasetQueryPayload,
  DatasetQueryPerformance,
  DatasetQueryPerformanceQuery,
  DatasetQueryResult,
  PublishedDataset,
} from './model';
import type {
  DatasetCatalogWire,
  DatasetDetailWire,
  DatasetFieldWire,
  DatasetVersionWire,
} from './types';

const DATASET_API = '/api/v1/datasets';
const DATASET_CATALOG_API = `${DATASET_API}/catalog`;
const DATASET_QUERY_PERFORMANCE_API = `${DATASET_API}/query-performance`;

export interface DatasetRequestOptions {
  /** Allows callers to cancel stale Dataset requests without changing the backend contract. */
  signal?: AbortSignal;
}

export type DatasetQueryOptions = DatasetRequestOptions;

export interface PublishedDatasetBatchResult {
  datasets: PublishedDataset[];
  errors: Record<string, string>;
}

type DatasetMetadataWire = Pick<
  DatasetCatalogWire,
  'dataset' | 'currentVersion' | 'fields'
>;

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const requestOptions = (options?: DatasetRequestOptions) => (
  options?.signal ? { signal: options.signal } : undefined
);

const fieldType = (value: DatasetFieldWire['dataType']): DatasetFieldType => {
  switch (value) {
    case 'STRING': return 'string';
    case 'NUMBER': return 'number';
    case 'DATE': return 'date';
    case 'DATETIME': return 'datetime';
    case 'BOOLEAN': return 'boolean';
    default: return 'unknown';
  }
};

const toField = (field: DatasetFieldWire): DatasetField => ({
  key: field.fieldId,
  label: field.displayName || field.physicalName,
  physicalName: field.physicalName,
  dataType: fieldType(field.dataType),
  role: field.defaultRole === 'MEASURE' ? 'metric' : 'dimension',
  nullable: field.nullable,
  description: field.description || undefined,
});

const datasetVersionSource = (version?: DatasetVersionWire | null) => {
  if (!version) {
    return { id: '', name: '尚未绑定版本' };
  }
  switch (version.sourceType) {
    case 'QUERY_REVISION':
      return {
        id: String(version.sourceTaskAssetId),
        name: `SQL TaskAsset #${version.sourceTaskAssetId} · V${version.sourceTaskRevisionNo}`,
      };
    case 'SQL_QUERY': {
      const dataSourceId = version.dataSourceId?.trim() || '';
      return {
        id: '',
        name: dataSourceId ? `Standalone SQL · 数据源 ${dataSourceId}` : 'Standalone SQL',
      };
    }
    case 'TABLE':
      return { id: '', name: '数据表 Dataset（查询运行时尚未接入）' };
    case 'VIEW':
      return { id: '', name: '视图 Dataset（查询运行时尚未接入）' };
  }
};

const toDataset = (detail: DatasetMetadataWire): PublishedDataset => {
  const version = detail.currentVersion;
  const source = datasetVersionSource(version);
  return {
    id: String(detail.dataset.id),
    name: detail.dataset.name,
    description: detail.dataset.description || '',
    status: detail.dataset.status,
    sourceTaskId: source.id,
    sourceTaskName: source.name,
    currentVersionNo: version?.versionNo,
    updatedAt: detail.dataset.updateTime || detail.dataset.createTime || '',
    fields: (detail.fields || []).map(toField),
  };
};

const optionalSourceId = (value?: string | null) => (
  value && value !== '0' ? String(value) : undefined
);

const toManagementVersion = (
  version?: DatasetVersionWire | null,
): DatasetManagementVersion | undefined => version ? ({
  id: String(version.id),
  datasetId: String(version.datasetId),
  versionNo: version.versionNo,
  sourceType: version.sourceType,
  sourceTaskAssetId: optionalSourceId(version.sourceTaskAssetId),
  sourceTaskRevisionId: optionalSourceId(version.sourceTaskRevisionId),
  sourceTaskRevisionNo: version.sourceTaskRevisionNo > 0
    ? version.sourceTaskRevisionNo
    : undefined,
  dataSourceId: version.dataSourceId?.trim() || undefined,
  sql: version.sql?.trim() || undefined,
  schemaSnapshot: version.schemaSnapshot?.trim() || undefined,
  createTime: version.createTime,
}) : undefined;

const toManagementField = (field: DatasetFieldWire): DatasetManagementField => ({
  fieldId: field.fieldId,
  versionId: String(field.versionId),
  physicalName: field.physicalName,
  displayName: field.displayName || field.physicalName,
  dataType: field.dataType,
  nullable: field.nullable,
  description: field.description || undefined,
  defaultRole: field.defaultRole,
  sortOrder: field.sortOrder,
});

const toManagementItem = (entry: DatasetCatalogWire): DatasetManagementItem => ({
  id: String(entry.dataset.id),
  name: entry.dataset.name,
  description: entry.dataset.description || '',
  status: entry.dataset.status,
  currentVersionId: entry.dataset.currentVersionId == null
    ? undefined
    : String(entry.dataset.currentVersionId),
  currentVersion: toManagementVersion(entry.currentVersion),
  fields: (entry.fields || []).map(toManagementField),
  createTime: entry.dataset.createTime,
  updateTime: entry.dataset.updateTime,
});

const toManagementDetail = (detail: DatasetDetailWire): DatasetManagementDetail => ({
  id: String(detail.dataset.id),
  name: detail.dataset.name,
  description: detail.dataset.description || '',
  status: detail.dataset.status,
  currentVersionId: detail.dataset.currentVersionId == null
    ? undefined
    : String(detail.dataset.currentVersionId),
  currentVersion: toManagementVersion(detail.currentVersion),
  versions: (detail.versions || [])
    .map(toManagementVersion)
    .filter((version): version is DatasetManagementVersion => Boolean(version)),
  fields: (detail.fields || []).map(toManagementField),
  createTime: detail.dataset.createTime,
  updateTime: detail.dataset.updateTime,
});

const catalogUrl = (datasetIds?: string[], onlineOnly = false) => {
  const params: string[] = [];
  if (datasetIds?.length) {
    params.push(`datasetIds=${datasetIds.map(encodeURIComponent).join(',')}`);
  }
  if (onlineOnly) {
    params.push('onlineOnly=true');
  }
  return params.length ? `${DATASET_CATALOG_API}?${params.join('&')}` : DATASET_CATALOG_API;
};

const queryPerformanceUrl = (query: DatasetQueryPerformanceQuery = {}) => {
  const params: string[] = [];
  if (query.datasetIds?.length) {
    params.push(`datasetIds=${query.datasetIds.map(encodeURIComponent).join(',')}`);
  }
  if (query.queryIds?.length) {
    params.push(`queryIds=${query.queryIds.map(encodeURIComponent).join(',')}`);
  }
  if (query.statuses?.length) {
    params.push(`statuses=${query.statuses.map(encodeURIComponent).join(',')}`);
  }
  if (query.minTotalMillis != null) {
    params.push(`minTotalMillis=${Math.max(0, Math.floor(query.minTotalMillis))}`);
  }
  if (query.limit != null) {
    params.push(`limit=${Math.max(1, Math.min(200, Math.floor(query.limit)))}`);
  }
  return params.length
    ? `${DATASET_QUERY_PERFORMANCE_API}?${params.join('&')}`
    : DATASET_QUERY_PERFORMANCE_API;
};

const fetchDatasetCatalog = async (
  datasetIds?: string[],
  options?: DatasetRequestOptions,
  onlineOnly = false,
): Promise<DatasetCatalogWire[]> => unwrap(
  await HttpUtils.get<DatasetCatalogWire[]>(
    catalogUrl(datasetIds, onlineOnly),
    requestOptions(options),
  ),
  '查询 Dataset Catalog 失败',
) || [];

const isPublishedQueryableCatalogEntry = (entry: DatasetCatalogWire) => Boolean(
  entry.dataset.status === 'ONLINE'
  && entry.currentVersion
  && isQueryableDatasetSourceType(entry.currentVersion.sourceType),
);

const catalogEntryError = (datasetId: string, entry?: DatasetCatalogWire) => {
  if (!entry) return `Dataset ${datasetId} 不存在或当前项目不可见`;
  if (entry.dataset.status !== 'ONLINE' || !entry.currentVersion) {
    return `Dataset ${datasetId} 当前未发布`;
  }
  if (!isQueryableDatasetSourceType(entry.currentVersion.sourceType)) {
    return `Dataset ${datasetId} 来源类型 ${entry.currentVersion.sourceType} 尚未接入查询运行时`;
  }
  return '';
};

/** Management list: one catalog request, no per-row detail fan-out. */
export const listDatasetsForManagement = async (
  options?: DatasetRequestOptions,
): Promise<DatasetManagementItem[]> => (
  await fetchDatasetCatalog(undefined, options)
).map(toManagementItem);

/** Load full version history only after entering one Dataset detail. */
export const getDatasetForManagement = async (
  datasetId: string,
  options?: DatasetRequestOptions,
): Promise<DatasetManagementDetail> => toManagementDetail(unwrap(
  await HttpUtils.get<DatasetDetailWire>(
    `${DATASET_API}/${datasetId}`,
    requestOptions(options),
  ),
  '查询 Dataset 详情失败',
));

export const createDatasetVersion = async (
  datasetId: string,
): Promise<DatasetManagementDetail> => toManagementDetail(unwrap(
  await HttpUtils.post<DatasetDetailWire>(`${DATASET_API}/${datasetId}/versions`, {}),
  '发布 Dataset 新版本失败',
));

export const onlineDataset = async (
  datasetId: string,
): Promise<DatasetManagementDetail> => toManagementDetail(unwrap(
  await HttpUtils.post<DatasetDetailWire>(`${DATASET_API}/${datasetId}/online`, {}),
  '上线 Dataset 失败',
));

export const offlineDataset = async (
  datasetId: string,
): Promise<DatasetManagementDetail> => toManagementDetail(unwrap(
  await HttpUtils.post<DatasetDetailWire>(`${DATASET_API}/${datasetId}/offline`, {}),
  '下线 Dataset 失败',
));

export const listDatasetQueryPerformance = async (
  query: DatasetQueryPerformanceQuery = {},
  options?: DatasetRequestOptions,
): Promise<DatasetQueryPerformance[]> => unwrap(
  await HttpUtils.get<DatasetQueryPerformance[]>(
    queryPerformanceUrl(query),
    requestOptions(options),
  ),
  '查询 Dataset 运行诊断失败',
) || [];

export const listPublishedDatasets = async (): Promise<PublishedDataset[]> => (
  await fetchDatasetCatalog(undefined, undefined, true)
)
  .filter(isPublishedQueryableCatalogEntry)
  .map(toDataset);

/** Fetches one currently published Dataset without loading its version history. */
export const getPublishedDataset = async (
  datasetId: string,
  options?: DatasetRequestOptions,
): Promise<PublishedDataset> => {
  const catalog = await fetchDatasetCatalog([datasetId], options);
  const entry = catalog.find((candidate) => String(candidate.dataset.id) === datasetId);
  const error = catalogEntryError(datasetId, entry);
  if (error) {
    throw new Error(error);
  }
  return toDataset(entry!);
};

/**
 * Viewer-oriented tolerant lookup: resolve all requested Dataset metadata in one catalog request,
 * while preserving per-id availability errors for callers that can render partial content.
 */
export const resolvePublishedDatasetsByIds = async (
  datasetIds: string[],
  options?: DatasetRequestOptions,
): Promise<PublishedDatasetBatchResult> => {
  const uniqueIds = [...new Set(datasetIds.filter(Boolean))];
  if (!uniqueIds.length) return { datasets: [], errors: {} };

  const catalog = await fetchDatasetCatalog(uniqueIds, options);
  const entries = new Map(catalog.map((entry) => [String(entry.dataset.id), entry]));
  const datasets: PublishedDataset[] = [];
  const errors: Record<string, string> = {};

  uniqueIds.forEach((datasetId) => {
    const entry = entries.get(datasetId);
    const error = catalogEntryError(datasetId, entry);
    if (error) {
      errors[datasetId] = error;
      return;
    }
    datasets.push(toDataset(entry!));
  });

  return { datasets, errors };
};

/** Compatibility convenience for callers that only need successfully resolved metadata. */
export const getPublishedDatasetsByIds = async (
  datasetIds: string[],
  options?: DatasetRequestOptions,
): Promise<PublishedDataset[]> => {
  const result = await resolvePublishedDatasetsByIds(datasetIds, options);
  if (!result.datasets.length && Object.keys(result.errors).length) {
    throw new Error(Object.values(result.errors)[0] || '读取大屏绑定 Dataset 失败');
  }
  return result.datasets;
};

export const queryDataset = async (
  datasetId: string,
  payload: DatasetQueryPayload,
  options?: DatasetQueryOptions,
): Promise<DatasetQueryResult> => unwrap(
  await HttpUtils.post<DatasetQueryResult>(
    `${DATASET_API}/${datasetId}/query`,
    payload,
    requestOptions(options),
  ),
  'Dataset 查询失败',
);

// Compatibility aliases for the Analysis module while callers migrate to the Dataset service vocabulary.
export const fetchAnalysisDatasets = listPublishedDatasets;
export const queryAnalysisDataset = queryDataset;
