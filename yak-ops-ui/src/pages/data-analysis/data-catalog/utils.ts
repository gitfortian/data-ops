import type {
  CatalogDataset,
  CatalogDatasetVersion,
  CatalogDirectory,
} from '@/services/data-analysis';
import {
  DEFAULT_LEFT_WIDTH,
  LEFT_WIDTH_STORAGE_KEY,
  MAX_LEFT_WIDTH,
  MIN_LEFT_WIDTH,
  ROOT_KEY,
  UNGROUPED_KEY,
} from './constants';
import type { CatalogTreeNode } from './types';

export const clampLeftWidth = (value: number) =>
  Math.min(MAX_LEFT_WIDTH, Math.max(MIN_LEFT_WIDTH, value));

export const getInitialLeftWidth = () => {
  if (typeof window === 'undefined') return DEFAULT_LEFT_WIDTH;
  const stored = Number(window.localStorage.getItem(LEFT_WIDTH_STORAGE_KEY));
  return Number.isFinite(stored) && stored > 0
    ? clampLeftWidth(stored)
    : DEFAULT_LEFT_WIDTH;
};

export const formatDateTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .format(date)
    .replaceAll('/', '-');
};

export const getSchemaSummary = (dataset: CatalogDataset) => {
  const dimensions = dataset.fields.filter(
    (field) => field.defaultRole === 'DIMENSION',
  ).length;
  const metrics = dataset.fields.filter(
    (field) => field.defaultRole === 'MEASURE',
  ).length;
  return { fields: dataset.fields.length, dimensions, metrics };
};

export const getDatasetVersionSourceSummary = (
  version: CatalogDatasetVersion,
  sourceTaskName?: string,
) => {
  switch (version.sourceType) {
    case 'QUERY_REVISION':
      return {
        title:
          sourceTaskName
          || (version.sourceTaskAssetId
            ? `TaskAsset #${version.sourceTaskAssetId}`
            : 'SQL TaskAsset'),
        detail: version.sourceTaskRevisionNo
          ? `SQL V${version.sourceTaskRevisionNo}`
          : `Dataset DV${version.versionNo}`,
      };
    case 'SQL_QUERY':
      return {
        title: version.dataSourceId ? `数据源 ${version.dataSourceId}` : 'Standalone SQL',
        detail: `Standalone SQL · DV${version.versionNo}`,
      };
    case 'TABLE':
      return {
        title: '数据表来源',
        detail: '查询运行时尚未接入',
      };
    case 'VIEW':
      return {
        title: '视图来源',
        detail: '查询运行时尚未接入',
      };
  }
};

const getDirectoryAncestors = (
  directoryId: string | undefined,
  directoryMap: Map<string, CatalogDirectory>,
) => {
  const values: CatalogDirectory[] = [];
  const visited = new Set<string>();
  let current = directoryId ? directoryMap.get(directoryId) : undefined;
  while (current && !visited.has(current.id)) {
    visited.add(current.id);
    values.unshift(current);
    current = current.parentId ? directoryMap.get(current.parentId) : undefined;
  }
  return values;
};

export const buildCatalogTree = (
  datasets: CatalogDataset[],
  directories: CatalogDirectory[],
): CatalogTreeNode[] => {
  const directoryMap = new Map(
    directories.map((directory) => [directory.id, directory]),
  );
  const relevantDirectoryIds = new Set<string>();

  datasets.forEach((dataset) => {
    getDirectoryAncestors(dataset.directoryId, directoryMap).forEach((directory) => {
      relevantDirectoryIds.add(directory.id);
    });
  });

  const toDatasetNode = (dataset: CatalogDataset): CatalogTreeNode => ({
    key: `dataset:${dataset.id}`,
    title: dataset.name,
    kind: 'dataset',
    datasetId: dataset.id,
    isLeaf: true,
    searchText: [
      dataset.name,
      dataset.description,
      dataset.sourceTaskName || '',
      dataset.directoryPath || '',
      ...dataset.fields.flatMap((field) => [field.displayName, field.physicalName]),
    ].join(' '),
  });

  const buildDirectory = (directory: CatalogDirectory): CatalogTreeNode => {
    const childDirectories = directories
      .filter(
        (candidate) =>
          candidate.parentId === directory.id && relevantDirectoryIds.has(candidate.id),
      )
      .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
      .map(buildDirectory);
    const directDatasets = datasets
      .filter((dataset) => dataset.directoryId === directory.id)
      .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
      .map(toDatasetNode);
    const children = [...childDirectories, ...directDatasets];
    const datasetCount = children.reduce(
      (total, child) => total + (child.kind === 'dataset' ? 1 : child.datasetCount || 0),
      0,
    );
    return {
      key: `directory:${directory.id}`,
      title: directory.name,
      kind: 'directory',
      directoryId: directory.id,
      datasetCount,
      searchText: `${directory.name} ${directory.path}`,
      children,
    };
  };

  const topDirectories = directories
    .filter(
      (directory) => !directory.parentId && relevantDirectoryIds.has(directory.id),
    )
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
    .map(buildDirectory);
  const rootDatasets = datasets
    .filter((dataset) => dataset.sourceNodeId && !dataset.directoryId)
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
    .map(toDatasetNode);
  const unmappedDatasets = datasets
    .filter((dataset) => !dataset.sourceNodeId)
    .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
    .map(toDatasetNode);

  const children: CatalogTreeNode[] = [...topDirectories, ...rootDatasets];
  if (unmappedDatasets.length) {
    children.push({
      key: UNGROUPED_KEY,
      title: '未分组',
      kind: 'ungrouped',
      datasetCount: unmappedDatasets.length,
      searchText: '未分组',
      children: unmappedDatasets,
    });
  }

  return [{
    key: ROOT_KEY,
    title: '全部数据集',
    kind: 'root',
    datasetCount: datasets.length,
    searchText: '全部数据集',
    children,
  }];
};

export const filterCatalogTree = (
  nodes: CatalogTreeNode[],
  keyword: string,
): CatalogTreeNode[] => {
  const normalized = keyword.trim().toLowerCase();
  if (!normalized) return nodes;

  return nodes.flatMap((node) => {
    const selfMatches = `${node.title} ${node.searchText || ''}`
      .toLowerCase()
      .includes(normalized);
    if (selfMatches) return [node];
    const children = node.children ? filterCatalogTree(node.children, normalized) : [];
    return children.length ? [{ ...node, children }] : [];
  });
};

export const flattenCatalogTree = (nodes: CatalogTreeNode[]) => {
  const map = new Map<string, CatalogTreeNode>();
  const visit = (values: CatalogTreeNode[]) => values.forEach((value) => {
    map.set(String(value.key), value);
    if (value.children) visit(value.children);
  });
  visit(nodes);
  return map;
};

export const getScopeDatasets = (
  selectedNode: CatalogTreeNode | undefined,
  datasets: CatalogDataset[],
  directories: CatalogDirectory[],
) => {
  if (!selectedNode || selectedNode.kind === 'root') return datasets;
  if (selectedNode.kind === 'ungrouped') {
    return datasets.filter((dataset) => !dataset.sourceNodeId);
  }
  if (selectedNode.kind !== 'directory' || !selectedNode.directoryId) return [];

  const includedDirectoryIds = new Set<string>([selectedNode.directoryId]);
  let changed = true;
  while (changed) {
    changed = false;
    directories.forEach((directory) => {
      if (
        directory.parentId &&
        includedDirectoryIds.has(directory.parentId) &&
        !includedDirectoryIds.has(directory.id)
      ) {
        includedDirectoryIds.add(directory.id);
        changed = true;
      }
    });
  }
  return datasets.filter(
    (dataset) => Boolean(dataset.directoryId && includedDirectoryIds.has(dataset.directoryId)),
  );
};
