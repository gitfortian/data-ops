import { message } from 'antd';
import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  getOperationLogOptions,
  pageOperationLogs,
  type OperationLog,
  type OperationLogOptions,
} from '@/services/security/operationLogs';

import { getSystemErrorMessage } from '../../utils';
import {
  DEFAULT_OPERATION_LOG_PAGINATION,
  EMPTY_OPERATION_LOG_OPTIONS,
} from '../constants';
import type {
  OperationLogFilterValues,
  OperationLogPaginationState,
} from '../types';

export function useOperationLogs() {
  const requestSequenceRef = useRef(0);
  const [logs, setLogs] = useState<OperationLog[]>([]);
  const [options, setOptions] =
    useState<OperationLogOptions>(EMPTY_OPERATION_LOG_OPTIONS);
  const [filters, setFilters] =
    useState<OperationLogFilterValues>({});
  const [pagination, setPagination] =
    useState<OperationLogPaginationState>(
      DEFAULT_OPERATION_LOG_PAGINATION,
    );
  const [isLoading, setIsLoading] = useState(false);

  const loadOptions = useCallback(async () => {
    try {
      setOptions(await getOperationLogOptions());
    } catch (error) {
      setOptions(EMPTY_OPERATION_LOG_OPTIONS);
      message.warning(
        getSystemErrorMessage(
          error,
          '操作日志筛选选项加载失败',
        ),
      );
    }
  }, []);

  const loadLogs = useCallback(async () => {
    const sequence = ++requestSequenceRef.current;
    setIsLoading(true);

    try {
      const result = await pageOperationLogs({
        pageNum: pagination.current,
        pageSize: pagination.pageSize,
        ...filters,
      });

      if (sequence !== requestSequenceRef.current) return;

      setLogs(result.records ?? []);
      setPagination((current) => ({
        ...current,
        total: result.total ?? 0,
      }));
    } catch (error) {
      if (sequence !== requestSequenceRef.current) return;

      setLogs([]);
      setPagination((current) => ({ ...current, total: 0 }));
      message.error(
        getSystemErrorMessage(error, '操作日志查询失败'),
      );
    } finally {
      if (sequence === requestSequenceRef.current) {
        setIsLoading(false);
      }
    }
  }, [filters, pagination.current, pagination.pageSize]);

  useEffect(() => {
    void loadOptions();
  }, [loadOptions]);

  useEffect(() => {
    void loadLogs();
  }, [loadLogs]);

  const refreshLogs = useCallback(() => {
    void Promise.all([loadLogs(), loadOptions()]);
  }, [loadLogs, loadOptions]);

  const searchLogs = useCallback(
    (values: OperationLogFilterValues) => {
      setFilters(values);
      setPagination((current) => ({ ...current, current: 1 }));
    },
    [],
  );

  const changePage = useCallback(
    (current: number, pageSize: number) => {
      setPagination((previous) => ({
        ...previous,
        current:
          previous.pageSize === pageSize ? current : 1,
        pageSize,
      }));
    },
    [],
  );

  return {
    logs,
    options,
    isLoading,
    pagination,
    refreshLogs,
    searchLogs,
    changePage,
  };
}
