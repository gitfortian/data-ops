import type { TreeProps } from 'antd';
import { useCallback, useEffect } from 'react';

import { useDataSourceTree } from './useDataSourceTree';
import { useTableAssets } from './useTableAssets';

export const useQualityTableRegistryPage = () => {
  const source = useDataSourceTree();
  const table = useTableAssets({
    dataSourceId: source.dataSourceId,
    selectedDataSource: source.selectedDataSource,
    selectedSourceNode: source.selectedSourceNode,
  });

  useEffect(() => {
    void source.loadSourceTree();
  }, [source.loadSourceTree]);

  const selectDataSource: TreeProps['onSelect'] = useCallback(
    (keys) => {
      const key = String(keys[0] || '');
      const selected = source.selectNode(key);
      if (selected) table.resetForDataSource();
    },
    [source.selectNode, table.resetForDataSource],
  );

  const refresh = useCallback(async () => {
    const selected = await source.loadSourceTree(source.selectedNodeKey);
    if (!selected) return;
    await table.requestAssets(
      selected.dataSourceId,
      table.assetCurrent,
      table.queryKeyword,
    );
  }, [
    source.loadSourceTree,
    source.selectedNodeKey,
    table.assetCurrent,
    table.queryKeyword,
    table.requestAssets,
  ]);

  const changeAssetKeyword = useCallback(
    (keyword: string) => {
      table.setKeyword(keyword);
      table.setAssetCurrent(1);
    },
    [table.setAssetCurrent, table.setKeyword],
  );

  const changeCandidateKeyword = useCallback(
    (keyword: string) => {
      table.setCandidateKeyword(keyword);
      table.setCandidateCurrent(1);
    },
    [table.setCandidateCurrent, table.setCandidateKeyword],
  );

  return {
    source,
    table,
    selectDataSource,
    refresh,
    changeAssetKeyword,
    changeCandidateKeyword,
    refreshing: table.assetLoading || source.treeLoading,
  };
};
