import {
  getQualityExecutionLogs,
  getQualityExecutionWorkspace,
  listQualityExecutionWorkspace,
  type ExecutionLogView,
  type ExecutionWorkspaceListItem,
  type ExecutionWorkspaceView,
} from '@/services/data-quality';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

export const useExecutionDetailPage = (executionNo: string) => {
  const [detail, setDetail] = useState<ExecutionWorkspaceView>();
  const [logs, setLogs] = useState<ExecutionLogView>();
  const [historyRecords, setHistoryRecords] = useState<
    ExecutionWorkspaceListItem[]
  >([]);
  const [loading, setLoading] = useState(false);
  const [logsLoading, setLogsLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);

  const loadDetail = useCallback(async () => {
    if (!executionNo) return;
    setLoading(true);
    try {
      setDetail(await getQualityExecutionWorkspace(executionNo));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '执行详情加载失败');
    } finally {
      setLoading(false);
    }
  }, [executionNo]);

  const loadLogs = useCallback(async () => {
    if (!executionNo) return;
    setLogsLoading(true);
    try {
      setLogs(await getQualityExecutionLogs(executionNo));
    } catch (error) {
      setLogs(undefined);
      message.error(error instanceof Error ? error.message : '原始日志加载失败');
    } finally {
      setLogsLoading(false);
    }
  }, [executionNo]);

  const loadHistory = useCallback(async (monitorId: number) => {
    setHistoryLoading(true);
    try {
      const page = await listQualityExecutionWorkspace({
        current: 1,
        pageSize: 50,
        monitorId,
      });
      setHistoryRecords(page.records || []);
    } catch (error) {
      setHistoryRecords([]);
      message.error(
        error instanceof Error ? error.message : '历史运行记录加载失败',
      );
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDetail();
    void loadLogs();
  }, [loadDetail, loadLogs]);

  useEffect(() => {
    if (!detail?.monitorId) {
      setHistoryRecords([]);
      return;
    }
    void loadHistory(detail.monitorId);
  }, [detail?.monitorId, loadHistory]);

  useEffect(() => {
    if (!detail || !['WAITING', 'RUNNING'].includes(detail.executionStatus)) {
      return;
    }
    const timer = window.setInterval(() => {
      void loadDetail();
      void loadLogs();
    }, 3000);
    return () => window.clearInterval(timer);
  }, [detail, loadDetail, loadLogs]);

  const issueRules = useMemo(
    () =>
      detail?.rules.filter((rule) =>
        ['NOT_PASSED', 'ERROR'].includes(rule.checkResult),
      ) || [],
    [detail?.rules],
  );

  const refresh = useCallback(async () => {
    await Promise.all([
      loadDetail(),
      loadLogs(),
      detail?.monitorId ? loadHistory(detail.monitorId) : Promise.resolve(),
    ]);
  }, [detail?.monitorId, loadDetail, loadHistory, loadLogs]);

  return {
    detail,
    logs,
    historyRecords,
    issueRules,
    loading,
    logsLoading,
    historyLoading,
    refreshing: loading || logsLoading || historyLoading,
    refresh,
    loadLogs,
  };
};
