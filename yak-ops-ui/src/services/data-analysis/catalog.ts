import type { AnalysisAsset } from '@/components/analysis/model';
import { fetchAnalyses } from '@/components/analysis/analysis-service';
import {
  listDevelopmentDirectories,
  listDevelopmentNodes,
  listDevelopmentReleases,
  type DevelopmentDirectory,
  type DevelopmentNode,
  type DevelopmentReleaseSummary,
} from '@/services/data-development';
import type {
  DatasetDetailWire,
  DatasetFieldWire,
  DatasetSummaryWire,
  DatasetVersionWire,
} from '@/services/dataset';
import type { ApiResponse } from '@/services/http/response';
import { API_SUCCESS_CODE } from '@/services/http/response';
import HttpUtils from '@/utils/HttpUtils';
import type {
  CatalogDataset,
  CatalogDatasetField,
  CatalogDatasetVersion,
  CatalogDirectory,
  CatalogWorkspace,
} from './types';

const DATASET_API = '/api/v1/datasets';
const RELEASE_PAGE_SIZE = 100;

interface DevelopmentTopology {
  directories: DevelopmentDirectory[];
  nodes: DevelopmentNode[];
  releases: DevelopmentReleaseSummary[];
}

const unwrap = <T,>(response: ApiResponse<T>, fallback: string): T => {
  if (response?.code !== API_SUCCESS_CODE || response.data === undefined) {
    throw new Error(response?.message || response?.msg || fallback);
  }
  return response.data;
};

const toVersion = (value: DatasetVersionWire): CatalogDatasetVersion => {
  const taskBacked = value.sourceType === 'QUERY_REVISION';
  return {
    id: String(value.id),
    versionNo: value.versionNo,
    sourceType: value.sourceType,
    sourceTaskAssetId:
      taskBacked && value.sourceTaskAssetId !== '0'
        ? String(value.sourceTaskAssetId)
        : undefined,
    sourceTaskRevisionId:
      taskBacked && value.sourceTaskRevisionId !== '0'
        ? String(value.sourceTaskRevisionId)
        : undefined,
    sourceTaskRevisionNo:
      taskBacked && value.sourceTaskRevisionNo > 0
        ? value.sourceTaskRevisionNo
        : undefined,
    dataSourceId: value.dataSourceId?.trim() || undefined,
    sql: value.sql?.trim() || undefined,
    createTime: value.createTime,
  };
};

const toField = (value: DatasetFieldWire): CatalogDatasetField => ({
  fieldId: value.fieldId,
  physicalName: value.physicalName,
  displayName: value.displayName || value.physicalName,
  dataType: value.dataType,
  nullable: value.nullable,
  description: value.description || undefined,
  defaultRole: value.defaultRole,
  sortOrder: value.sortOrder,
});

const countAnalysesByDataset = (analyses: AnalysisAsset[]) => {
  const counts = new Map<string, number>();
  analyses.forEach((analysis) => {
    const datasetId = String(analysis.datasetId);
    counts.set(datasetId, (counts.get(datasetId) || 0) + 1);
  });
  return counts;
};

const listAllSqlReleases = async (): Promise<DevelopmentReleaseSummary[]> => {
  const first = unwrap(
    await listDevelopmentReleases({
      pageNo: 1,
      pageSize: RELEASE_PAGE_SIZE,
      status: 'ALL',
      taskType: 'SQL',
    }),
    '查询 SQL 发布资产失败',
  );
  const firstRecords = first.records || [];
  const pageSize = first.pageSize || RELEASE_PAGE_SIZE;
  const pages = Math.ceil((first.total || firstRecords.length) / pageSize);
  if (pages <= 1) return firstRecords;

  const remaining = await Promise.all(
    Array.from({ length: pages - 1 }, (_, index) => index + 2).map(async (pageNo) => {
      const page = unwrap(
        await listDevelopmentReleases({
          pageNo,
          pageSize,
          status: 'ALL',
          taskType: 'SQL',
        }),
        `查询 SQL 发布资产第 ${pageNo} 页失败`,
      );
      return page.records || [];
    }),
  );
  return [firstRecords, ...remaining].flat();
};

const getDevelopmentTopology = async (): Promise<DevelopmentTopology> => {
  const [directoryResponse, nodeResponse, releases] = await Promise.all([
    listDevelopmentDirectories(),
    listDevelopmentNodes(),
    listAllSqlReleases(),
  ]);
  return {
    directories: unwrap(directoryResponse, '查询数据开发目录失败') || [],
    nodes: unwrap(nodeResponse, '查询数据开发节点失败') || [],
    releases,
  };
};

const toCatalogDirectories = (values: DevelopmentDirectory[]): CatalogDirectory[] => values.map((value) => ({
  id: String(value.id),
  parentId: value.parentId == null ? undefined : String(value.parentId),
  name: value.name,
  path: value.path,
}));

const toDataset = (
  detail: DatasetDetailWire,
  counts: Map<string, number>,
  topology?: DevelopmentTopology,
): CatalogDataset => {
  const currentVersion = detail.currentVersion ? toVersion(detail.currentVersion) : undefined;
  const assetId = currentVersion?.sourceType === 'QUERY_REVISION'
    ? currentVersion.sourceTaskAssetId
    : undefined;
  const release = assetId
    ? topology?.releases.find((item) => String(item.assetId) === assetId)
    : undefined;
  const node = release
    ? topology?.nodes.find((item) => String(item.id) === String(release.nodeId))
    : undefined;
  const directoryId = node?.directoryId == null ? undefined : String(node.directoryId);
  const directory = directoryId
    ? topology?.directories.find((item) => String(item.id) === directoryId)
    : undefined;
  const sourceDisplayName = currentVersion?.sourceType === 'SQL_QUERY'
    ? currentVersion.dataSourceId
      ? `Standalone SQL · 数据源 ${currentVersion.dataSourceId}`
      : 'Standalone SQL'
    : release?.taskName;

  return {
    id: String(detail.dataset.id),
    name: detail.dataset.name,
    description: detail.dataset.description || '',
    status: detail.dataset.status,
    currentVersionId: detail.dataset.currentVersionId == null
      ? undefined
      : String(detail.dataset.currentVersionId),
    currentVersion,
    versions: (detail.versions || []).map(toVersion),
    fields: (detail.fields || []).map(toField),
    createTime: detail.dataset.createTime,
    updateTime: detail.dataset.updateTime,
    analysisCount: counts.get(String(detail.dataset.id)) || 0,
    sourceTaskName: sourceDisplayName,
    sourceNodeId: release ? String(release.nodeId) : undefined,
    directoryId,
    directoryPath: directory?.path,
  };
};

export const getCatalogWorkspace = async (): Promise<CatalogWorkspace> => {
  const summaries = unwrap(
    await HttpUtils.get<DatasetSummaryWire[]>(DATASET_API),
    '查询 Dataset 列表失败',
  ) || [];

  const [detailResults, analyses, topology] = await Promise.all([
    Promise.allSettled(
      summaries.map((dataset) => HttpUtils.get<DatasetDetailWire>(`${DATASET_API}/${dataset.id}`)),
    ),
    fetchAnalyses().catch(() => []),
    getDevelopmentTopology().catch(() => undefined),
  ]);
  const counts = countAnalysesByDataset(analyses);

  const details = detailResults.flatMap((result, index) => {
    if (result.status !== 'fulfilled') return [];
    try {
      return [unwrap(result.value, `查询 Dataset ${summaries[index].name} 详情失败`)];
    } catch {
      return [];
    }
  });

  if (!details.length && summaries.length) {
    throw new Error('Dataset 列表已返回，但详情读取失败');
  }

  return {
    datasets: details.map((detail) => toDataset(detail, counts, topology)),
    directories: toCatalogDirectories(topology?.directories || []),
  };
};

export const listCatalogDatasets = async (): Promise<CatalogDataset[]> => (
  await getCatalogWorkspace()
).datasets;

export const onlineCatalogDataset = async (datasetId: string): Promise<void> => {
  unwrap(
    await HttpUtils.post<DatasetDetailWire>(`${DATASET_API}/${datasetId}/online`, {}),
    '上线 Dataset 失败',
  );
};

export const offlineCatalogDataset = async (datasetId: string): Promise<void> => {
  unwrap(
    await HttpUtils.post<DatasetDetailWire>(`${DATASET_API}/${datasetId}/offline`, {}),
    '下线 Dataset 失败',
  );
};
