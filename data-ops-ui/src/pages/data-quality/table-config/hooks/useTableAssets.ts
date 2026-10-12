import type { DataSourceRecord } from '@/services/data-source';
import {
  deleteQualityTableAsset,
  listQualityTableAssets,
  listQualityTableCandidates,
  registerQualityTables,
  type TableAssetView,
  type TableCandidateView,
} from '@/services/data-quality';
import { history, useIntl } from '@umijs/max';
import { message } from 'antd';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';

import {
  QUALITY_TABLE_CANDIDATE_PAGE_SIZE,
  QUALITY_TABLE_PAGE_SIZE,
  QUALITY_TABLE_SEARCH_DEBOUNCE,
} from '../constants';
import type { QualityDataSourceNode } from '../types';
import {
  getQualityMonitorCreatePath,
  getQualityMonitorDetailPath,
  qualityTableCandidateKey,
} from '../utils';

interface UseTableAssetsOptions {
  dataSourceId?: number;
  selectedDataSource?: DataSourceRecord;
  selectedSourceNode?: QualityDataSourceNode;
}

export const useTableAssets = ({
  dataSourceId,
  selectedDataSource,
  selectedSourceNode,
}: UseTableAssetsOptions) => {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const assetRequestSequenceRef = useRef(0);
  const candidateRequestSequenceRef = useRef(0);
  const sourceScope = useRef({ id: dataSourceId, generation: 0 });
  if (sourceScope.current.id !== dataSourceId) {
    sourceScope.current = { id: dataSourceId, generation: sourceScope.current.generation + 1 };
  }
  const registrationRequest = useRef(0);
  const registrationPending = useRef(false);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      assetRequestSequenceRef.current += 1;
      candidateRequestSequenceRef.current += 1;
    };
  }, []);
  const [assets, setAssets] = useState<TableAssetView[]>([]);
  const [assetTotal, setAssetTotal] = useState(0);
  const [assetCurrent, setAssetCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [queryKeyword, setQueryKeyword] = useState('');
  const [assetLoading, setAssetLoading] = useState(false);
  const [assetError, setAssetError] = useState('');

  const [registerOpen, setRegisterOpen] = useState(false);
  const [candidates, setCandidates] = useState<TableCandidateView[]>([]);
  const [candidateTotal, setCandidateTotal] = useState(0);
  const [candidateCurrent, setCandidateCurrent] = useState(1);
  const [candidateKeyword, setCandidateKeyword] = useState('');
  const [candidateQueryKeyword, setCandidateQueryKeyword] = useState('');
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [candidateError, setCandidateError] = useState('');
  const [registering, setRegistering] = useState(false);
  const [selectedCandidates, setSelectedCandidates] = useState<
    Map<string, TableCandidateView>
  >(new Map());

  const selectedCandidateKeys = useMemo(
    () => Array.from(selectedCandidates.keys()),
    [selectedCandidates],
  );

  const selectedCandidateRecords = useMemo(
    () => Array.from(selectedCandidates.values()),
    [selectedCandidates],
  );

  const requestAssets = useCallback(
    async (
      targetDataSourceId: number,
      current: number,
      searchKeyword: string,
    ) => {
      if (!active.current || sourceScope.current.id !== targetDataSourceId) return;
      const requestSequence = assetRequestSequenceRef.current + 1;
      assetRequestSequenceRef.current = requestSequence;
      setAssetLoading(true);
      setAssetError('');

      try {
        const result = await listQualityTableAssets({
          current,
          pageSize: QUALITY_TABLE_PAGE_SIZE,
          dataSourceId: targetDataSourceId,
          keyword: searchKeyword || undefined,
        });
        if (!active.current || requestSequence !== assetRequestSequenceRef.current) return;
        setAssets(result?.records || []);
        setAssetTotal(result?.total || 0);
      } catch (error) {
        if (active.current && requestSequence === assetRequestSequenceRef.current) {
          setAssets([]);
          setAssetTotal(0);
          const reason =
            error instanceof Error
              ? error.message
              : intlRef.current.formatMessage({
                  id: 'pages.dataQuality.tableConfig.message.assetsLoadFailed',
                });
          setAssetError(reason);
          message.error(reason);
        }
      } finally {
        if (active.current && requestSequence === assetRequestSequenceRef.current) {
          setAssetLoading(false);
        }
      }
    },
    [],
  );

  const requestCandidates = useCallback(
    async (
      targetDataSourceId: number,
      current: number,
      searchKeyword: string,
    ) => {
      if (!active.current || sourceScope.current.id !== targetDataSourceId) return;
      const requestSequence = candidateRequestSequenceRef.current + 1;
      candidateRequestSequenceRef.current = requestSequence;
      setCandidateLoading(true);
      setCandidateError('');

      try {
        const result = await listQualityTableCandidates({
          dataSourceId: targetDataSourceId,
          current,
          pageSize: QUALITY_TABLE_CANDIDATE_PAGE_SIZE,
          keyword: searchKeyword || undefined,
        });
        if (!active.current || requestSequence !== candidateRequestSequenceRef.current) return;
        setCandidates(result?.records || []);
        setCandidateTotal(result?.total || 0);
      } catch (error) {
        if (active.current && requestSequence === candidateRequestSequenceRef.current) {
          setCandidates([]);
          setCandidateTotal(0);
          const reason =
            error instanceof Error
              ? error.message
              : intlRef.current.formatMessage({
                  id: 'pages.dataQuality.tableConfig.message.candidatesLoadFailed',
                });
          setCandidateError(reason);
          message.error(reason);
        }
      } finally {
        if (active.current && requestSequence === candidateRequestSequenceRef.current) {
          setCandidateLoading(false);
        }
      }
    },
    [],
  );

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setQueryKeyword(keyword.trim());
    }, QUALITY_TABLE_SEARCH_DEBOUNCE);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setCandidateQueryKeyword(candidateKeyword.trim());
    }, QUALITY_TABLE_SEARCH_DEBOUNCE);
    return () => window.clearTimeout(timer);
  }, [candidateKeyword]);

  useEffect(() => {
    if (!dataSourceId) {
      assetRequestSequenceRef.current += 1;
      setAssets([]);
      setAssetTotal(0);
      setAssetLoading(false);
      setAssetError('');
      return;
    }
    void requestAssets(dataSourceId, assetCurrent, queryKeyword);
  }, [assetCurrent, dataSourceId, queryKeyword, requestAssets]);

  useEffect(() => {
    if (!registerOpen || !dataSourceId) return;
    void requestCandidates(
      dataSourceId,
      candidateCurrent,
      candidateQueryKeyword,
    );
  }, [
    candidateCurrent,
    candidateQueryKeyword,
    dataSourceId,
    registerOpen,
    requestCandidates,
  ]);

  const resetForDataSource = useCallback(() => {
    registrationRequest.current += 1;
    registrationPending.current = false;
    setRegistering(false);
    assetRequestSequenceRef.current += 1;
    candidateRequestSequenceRef.current += 1;
    setAssets([]);
    setAssetTotal(0);
    setAssetError('');
    setCandidateError('');
    setCandidateLoading(false);
    setAssetCurrent(1);
    setKeyword('');
    setQueryKeyword('');
    setRegisterOpen(false);
    setCandidates([]);
    setCandidateTotal(0);
    setSelectedCandidates(new Map());
  }, []);

  useLayoutEffect(resetForDataSource, [dataSourceId, resetForDataSource]);

  const openRegisterDrawer = useCallback(() => {
    if (!dataSourceId) {
      message.warning(
        intlRef.current.formatMessage({
          id: 'pages.dataQuality.tableConfig.message.selectSourceFirst',
        }),
      );
      return;
    }
    setSelectedCandidates(new Map());
    setCandidateKeyword('');
    setCandidateQueryKeyword('');
    setCandidateCurrent(1);
    setRegisterOpen(true);
  }, [dataSourceId]);

  const closeRegisterDrawer = useCallback(() => {
    if (registering) return;
    candidateRequestSequenceRef.current += 1;
    setCandidateLoading(false);
    setRegisterOpen(false);
    setSelectedCandidates(new Map());
  }, [registering]);

  const updateCandidateSelection = useCallback(
    (record: TableCandidateView, selected: boolean) => {
      setSelectedCandidates((previous) => {
        const next = new Map(previous);
        const key = qualityTableCandidateKey(record);
        if (selected) next.set(key, record);
        else next.delete(key);
        return next;
      });
    },
    [],
  );

  const updateAllCandidateSelection = useCallback(
    (selected: boolean, changedRows: TableCandidateView[]) => {
      setSelectedCandidates((previous) => {
        const next = new Map(previous);
        changedRows.forEach((record) => {
          const key = qualityTableCandidateKey(record);
          if (selected) next.set(key, record);
          else next.delete(key);
        });
        return next;
      });
    },
    [],
  );

  const clearCandidateSelection = useCallback(
    () => setSelectedCandidates(new Map()),
    [],
  );

  const handleRegister = useCallback(async () => {
    if (!active.current || candidateLoading || candidateError || registrationPending.current) return;
    if (!dataSourceId || !selectedDataSource) return;
    if (!selectedCandidates.size) {
      message.warning(
        intlRef.current.formatMessage({
          id: 'pages.dataQuality.tableConfig.message.selectOneTable',
        }),
      );
      return;
    }

    const request = ++registrationRequest.current;
    const generation = sourceScope.current.generation;
    const isCurrent = () => active.current && request === registrationRequest.current
      && generation === sourceScope.current.generation;
    registrationPending.current = true;
    setRegistering(true);
    try {
      const result = await registerQualityTables({
        dataSourceId,
        dataSourceName:
          selectedDataSource.name || selectedSourceNode?.dataSourceName || '',
        tables: selectedCandidateRecords.map((record) => ({
          databaseName: record.databaseName,
          schemaName: record.schemaName,
          tableName: record.tableName,
          tableType: record.tableType,
          remarks: record.remarks,
        })),
      });
      if (!isCurrent()) return;
      message.success(
        intlRef.current.formatMessage(
          { id: 'pages.dataQuality.tableConfig.message.registered' },
          { count: result.registered },
        ),
      );
      setRegisterOpen(false);
      setSelectedCandidates(new Map());
      setAssetCurrent(1);
      await requestAssets(dataSourceId, 1, queryKeyword);
    } catch (error) {
      if (!isCurrent()) return;
      message.error(
        error instanceof Error
          ? error.message
          : intlRef.current.formatMessage({
              id: 'pages.dataQuality.tableConfig.message.registerFailed',
            }),
      );
    } finally {
      if (isCurrent()) {
        registrationPending.current = false;
        setRegistering(false);
      }
    }
  }, [
    dataSourceId,
    candidateLoading,
    candidateError,
    queryKeyword,
    requestAssets,
    selectedCandidateRecords,
    selectedCandidates.size,
    selectedDataSource,
    selectedSourceNode?.dataSourceName,
  ]);

  const openRuleManagement = useCallback((record: TableAssetView) => {
    const path = getQualityMonitorDetailPath(record);
    if (!path) {
      message.warning(
        intlRef.current.formatMessage({
          id: 'pages.dataQuality.tableConfig.message.noMonitor',
        }),
      );
      return;
    }
    history.push(path);
  }, []);

  /**
   * 取消注册:领域规则是"已被监控使用的数据表不能直接取消注册",
   * 因此监控数 >0 时先提示去删监控,而不是让后端抛错。
   */
  const unregisterTableAsset = useCallback(
    async (record: TableAssetView) => {
      const generation = sourceScope.current.generation;
      const isCurrent = () => active.current && generation === sourceScope.current.generation;
      if (record.monitorCount > 0) {
        message.warning(
          intlRef.current.formatMessage(
            { id: 'pages.dataQuality.tableConfig.unregisterBlocked' },
            { count: record.monitorCount },
          ),
        );
        return;
      }
      try {
        await deleteQualityTableAsset(record.id);
        if (!isCurrent()) return;
        message.success(
          intlRef.current.formatMessage(
            { id: 'pages.dataQuality.tableConfig.message.unregistered' },
            { name: record.tableName },
          ),
        );
        if (dataSourceId) {
          await requestAssets(dataSourceId, assetCurrent, queryKeyword);
        }
      } catch (error) {
        if (!isCurrent()) return;
        message.error(
          error instanceof Error
            ? error.message
            : intlRef.current.formatMessage({
                id: 'pages.dataQuality.tableConfig.message.unregisterFailed',
              }),
        );
      }
    },
    [assetCurrent, dataSourceId, queryKeyword, requestAssets],
  );

  const createMonitor = useCallback((record: TableAssetView) => {
    history.push(getQualityMonitorCreatePath(record));
  }, []);

  return {
    assets,
    assetTotal,
    assetCurrent,
    setAssetCurrent,
    keyword,
    setKeyword,
    queryKeyword,
    assetLoading,
    assetError,
    registerOpen,
    candidates,
    candidateTotal,
    candidateCurrent,
    setCandidateCurrent,
    candidateKeyword,
    setCandidateKeyword,
    candidateLoading,
    candidateError,
    retryCandidates: () => dataSourceId
      ? requestCandidates(dataSourceId, candidateCurrent, candidateQueryKeyword)
      : Promise.resolve(),
    registering,
    selectedCandidates,
    selectedCandidateKeys,
    selectedCandidateRecords,
    requestAssets,
    resetForDataSource,
    openRegisterDrawer,
    closeRegisterDrawer,
    updateCandidateSelection,
    updateAllCandidateSelection,
    clearCandidateSelection,
    handleRegister,
    openRuleManagement,
    unregisterTableAsset,
    createMonitor,
  };
};
