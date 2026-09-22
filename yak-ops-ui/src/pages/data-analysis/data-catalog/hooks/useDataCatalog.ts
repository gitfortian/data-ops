import {
  getCatalogWorkspace,
  offlineCatalogDataset,
  onlineCatalogDataset,
  type CatalogDataset,
} from '@/services/data-analysis';
import { message } from 'antd';
import type { PointerEvent as ReactPointerEvent } from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { LEFT_WIDTH_STORAGE_KEY, ROOT_KEY } from '../constants';
import type {
  CatalogDetailTab,
  CatalogSourceTypeFilter,
  CatalogStatusFilter,
} from '../types';
import {
  buildCatalogTree,
  clampLeftWidth,
  filterCatalogTree,
  flattenCatalogTree,
  getInitialLeftWidth,
  getScopeDatasets,
} from '../utils';

export function useDataCatalog() {
  const [datasets, setDatasets] = useState<Awaited<ReturnType<typeof getCatalogWorkspace>>['datasets']>([]);
  const [directories, setDirectories] = useState<Awaited<ReturnType<typeof getCatalogWorkspace>>['directories']>([]);
  const [selectedKey, setSelectedKey] = useState(ROOT_KEY);
  const [treeKeyword, setTreeKeyword] = useState('');
  const [listKeyword, setListKeyword] = useState('');
  const [status, setStatus] = useState<CatalogStatusFilter>('ALL');
  const [sourceType, setSourceType] = useState<CatalogSourceTypeFilter>('ALL');
  const [detailTab, setDetailTab] = useState<CatalogDetailTab>('fields');
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [statusUpdatingId, setStatusUpdatingId] = useState('');
  const [leftWidth, setLeftWidth] = useState(getInitialLeftWidth);
  const [isLeftCollapsed, setIsLeftCollapsed] = useState(false);

  const loadCatalog = useCallback(async () => {
    setIsLoading(true);
    setLoadError('');
    try {
      const workspace = await getCatalogWorkspace();
      setDatasets(workspace.datasets);
      setDirectories(workspace.directories);
      setSelectedKey((value) =>
        value.startsWith('dataset:') &&
        !workspace.datasets.some((item) => `dataset:${item.id}` === value)
          ? ROOT_KEY
          : value,
      );
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '加载数据目录失败');
      setDatasets([]);
      setDirectories([]);
      setSelectedKey(ROOT_KEY);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadCatalog();
  }, [loadCatalog]);

  const treeData = useMemo(
    () => buildCatalogTree(datasets, directories),
    [datasets, directories],
  );
  const visibleTreeData = useMemo(
    () => filterCatalogTree(treeData, treeKeyword),
    [treeData, treeKeyword],
  );
  const treeNodeMap = useMemo(() => flattenCatalogTree(treeData), [treeData]);
  const selectedNode = treeNodeMap.get(selectedKey) ?? treeNodeMap.get(ROOT_KEY);
  const selectedDataset = selectedNode?.kind === 'dataset'
    ? datasets.find((dataset) => dataset.id === selectedNode.datasetId)
    : undefined;

  const scopeDatasets = useMemo(
    () => getScopeDatasets(selectedNode, datasets, directories),
    [datasets, directories, selectedNode],
  );

  const filteredDatasets = useMemo(() => {
    const normalized = listKeyword.trim().toLowerCase();
    return scopeDatasets.filter((dataset) => {
      if (status !== 'ALL' && dataset.status !== status) return false;
      if (sourceType !== 'ALL' && dataset.currentVersion?.sourceType !== sourceType) return false;
      if (!normalized) return true;
      return [
        dataset.name,
        dataset.description,
        dataset.sourceTaskName || '',
        dataset.directoryPath || '',
        dataset.currentVersion?.sourceTaskAssetId || '',
        ...dataset.fields.flatMap((field) => [
          field.displayName,
          field.physicalName,
          field.description || '',
        ]),
      ].some((value) => value.toLowerCase().includes(normalized));
    });
  }, [listKeyword, scopeDatasets, sourceType, status]);

  const pagedDatasets = useMemo(() => {
    const start = (current - 1) * pageSize;
    return filteredDatasets.slice(start, start + pageSize);
  }, [current, filteredDatasets, pageSize]);

  useEffect(() => {
    const lastPage = Math.max(1, Math.ceil(filteredDatasets.length / pageSize));
    if (current > lastPage) setCurrent(lastPage);
  }, [current, filteredDatasets.length, pageSize]);

  const scopeTitle =
    selectedNode?.kind === 'directory' || selectedNode?.kind === 'ungrouped'
      ? selectedNode.title
      : '全部数据集';

  const updateDatasetStatus = useCallback(async (dataset: CatalogDataset) => {
    setStatusUpdatingId(dataset.id);
    try {
      if (dataset.status === 'ONLINE') {
        await offlineCatalogDataset(dataset.id);
        message.success(`Dataset「${dataset.name}」已下线`);
      } else {
        await onlineCatalogDataset(dataset.id);
        message.success(`Dataset「${dataset.name}」已上线`);
      }
      await loadCatalog();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新 Dataset 状态失败');
    } finally {
      setStatusUpdatingId('');
    }
  }, [loadCatalog]);

  const selectDataset = useCallback((dataset: CatalogDataset) => {
    setSelectedKey(`dataset:${dataset.id}`);
    setDetailTab('fields');
  }, []);

  const selectTreeNode = useCallback((key: string) => {
    setSelectedKey(key);
    setDetailTab('fields');
    setCurrent(1);
  }, []);

  const resetFilters = useCallback(() => {
    setListKeyword('');
    setStatus('ALL');
    setSourceType('ALL');
    setCurrent(1);
  }, []);

  const handleResizeStart = useCallback((event: ReactPointerEvent) => {
    if (isLeftCollapsed) return;
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = leftWidth;
    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const handlePointerMove = (moveEvent: PointerEvent) => {
      setLeftWidth(clampLeftWidth(startWidth + moveEvent.clientX - startX));
    };
    const finish = (upEvent: PointerEvent) => {
      const width = clampLeftWidth(startWidth + upEvent.clientX - startX);
      setLeftWidth(width);
      window.localStorage.setItem(LEFT_WIDTH_STORAGE_KEY, String(width));
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', finish);
      window.removeEventListener('pointercancel', finish);
    };
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', finish);
    window.addEventListener('pointercancel', finish);
  }, [isLeftCollapsed, leftWidth]);

  return {
    datasets,
    directories,
    selectedKey,
    treeKeyword,
    listKeyword,
    status,
    sourceType,
    detailTab,
    current,
    pageSize,
    isLoading,
    loadError,
    statusUpdatingId,
    leftWidth,
    isLeftCollapsed,
    visibleTreeData,
    selectedDataset,
    scopeDatasets,
    filteredDatasets,
    pagedDatasets,
    scopeTitle,
    setTreeKeyword,
    setListKeyword,
    setStatus,
    setSourceType,
    setDetailTab,
    setCurrent,
    setPageSize,
    setIsLeftCollapsed,
    loadCatalog,
    updateDatasetStatus,
    selectDataset,
    selectTreeNode,
    resetFilters,
    handleResizeStart,
  };
}
