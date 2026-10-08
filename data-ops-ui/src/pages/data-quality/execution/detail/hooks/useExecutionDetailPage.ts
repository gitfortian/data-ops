import {
  getQualityExecutionLogs,
  getQualityExecutionWorkspace,
  listQualityExecutionWorkspace,
  type ExecutionLogView,
  type ExecutionWorkspaceListItem,
  type ExecutionWorkspaceView,
} from '@/services/data-quality';
import { message } from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

export const useExecutionDetailPage = (executionNo: string) => {
  const active = useRef(true);
  const requests = useRef({ detail: 0, logs: 0, history: 0 });
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
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
    const request = ++requests.current.detail;
    const isCurrent = () => active.current && request === requests.current.detail;
    setLoading(true);
    try {
      const result = await getQualityExecutionWorkspace(executionNo);
      if (isCurrent()) setDetail(result.executionNo === executionNo ? result : undefined);
    } catch (error) {
      if (isCurrent()) {
        setDetail(undefined); setHistoryRecords([]);
        message.error(error instanceof Error ? error.message : '执行详情加载失败');
      }
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [executionNo]);

  const loadLogs = useCallback(async () => {
    if (!executionNo) return;
    const request = ++requests.current.logs;
    const isCurrent = () => active.current && request === requests.current.logs;
    setLogsLoading(true);
    try {
      const result = await getQualityExecutionLogs(executionNo);
      if (isCurrent()) setLogs(result);
    } catch (error) {
      if (isCurrent()) {
        setLogs(undefined);
        message.error(error instanceof Error ? error.message : '原始日志加载失败');
      }
    } finally {
      if (isCurrent()) setLogsLoading(false);
    }
  }, [executionNo]);

  const loadHistory = useCallback(async (monitorId: number) => {
    const request = ++requests.current.history;
    const isCurrent = () => active.current && request === requests.current.history;
    setHistoryRecords([]); setHistoryLoading(true);
    try {
      const page = await listQualityExecutionWorkspace({
        current: 1,
        pageSize: 50,
        monitorId,
      });
      if (isCurrent()) setHistoryRecords(page.records || []);
    } catch (error) {
      if (!isCurrent()) return;
      setHistoryRecords([]);
      message.error(
        error instanceof Error ? error.message : '历史运行记录加载失败',
      );
    } finally {
      if (isCurrent()) setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDetail();
    void loadLogs();
  }, [loadDetail, loadLogs]);

  useEffect(() => {
    if (!detail?.monitorId) {
      requests.current.history++;
      setHistoryRecords([]); setHistoryLoading(false);
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
