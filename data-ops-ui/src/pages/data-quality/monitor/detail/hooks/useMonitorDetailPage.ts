import {
  deleteQualityMonitor,
  getQualityExecutionStatus,
  getQualityMonitorReport,
  getQualityMonitorWorkspace,
  listQualityMonitorOperationLogs,
  runQualityMonitor,
  type MonitorReportView,
  type MonitorWorkspaceView,
  type OperationLogPageView,
  type QualityExecutionStatusView,
} from '@/services/data-quality';
import { history } from '@umijs/max';
import { Modal, message } from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';

import type { WorkspaceTab } from '../model';

const EMPTY_OPERATION_LOG: OperationLogPageView = {
  records: [],
  total: 0,
  current: 1,
  pageSize: 10,
};

const RUN_POLL_INTERVAL_MS = 1000;
const RUN_POLL_MAX_ATTEMPTS = 30;
const TERMINAL_EXECUTION_STATUSES = new Set(['SUCCESS', 'FAILED']);

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message : fallback;

const sleep = (milliseconds: number) =>
  new Promise<void>((resolve) => window.setTimeout(resolve, milliseconds));

const waitForExecution = async (
  executionNo: string,
): Promise<QualityExecutionStatusView | undefined> => {
  for (let attempt = 0; attempt < RUN_POLL_MAX_ATTEMPTS; attempt += 1) {
    const status = await getQualityExecutionStatus(executionNo);
    if (TERMINAL_EXECUTION_STATUSES.has(status.executionStatus)) {
      return status;
    }
    if (attempt < RUN_POLL_MAX_ATTEMPTS - 1) {
      await sleep(RUN_POLL_INTERVAL_MS);
    }
  }
  return undefined;
};

export const useMonitorDetailPage = (monitorId?: string) => {
  const [activeTab, setActiveTab] = useState<WorkspaceTab>('rules');
  const [workspace, setWorkspace] = useState<MonitorWorkspaceView>();
  const [report, setReport] = useState<MonitorReportView>();
  const [reportDate, setReportDate] = useState(
    dayjs().subtract(1, 'day').format('YYYY-MM-DD'),
  );
  const [operationLog, setOperationLog] = useState<OperationLogPageView>(
    EMPTY_OPERATION_LOG,
  );
  const [loading, setLoading] = useState(true);
  const [reportLoading, setReportLoading] = useState(false);
  const [logLoading, setLogLoading] = useState(false);
  const [logOpen, setLogOpen] = useState(false);
  const [running, setRunning] = useState(false);

  const loadWorkspace = useCallback(async () => {
    if (!monitorId) return;
    setLoading(true);
    try {
      setWorkspace(await getQualityMonitorWorkspace(monitorId));
    } catch (error) {
      message.error(errorMessage(error, '质量监控工作台加载失败'));
    } finally {
      setLoading(false);
    }
  }, [monitorId]);

  const loadReport = useCallback(
    async (date: string) => {
      if (!monitorId) return;
      setReportLoading(true);
      try {
        setReport(await getQualityMonitorReport(monitorId, date));
      } catch (error) {
        message.error(errorMessage(error, '质量报告加载失败'));
      } finally {
        setReportLoading(false);
      }
    },
    [monitorId],
  );

  const loadOperationLog = useCallback(
    async (current = operationLog.current, pageSize = operationLog.pageSize) => {
      if (!monitorId) return;
      setLogLoading(true);
      try {
        setOperationLog(
          await listQualityMonitorOperationLogs(monitorId, current, pageSize),
        );
      } catch (error) {
        message.error(errorMessage(error, '操作日志加载失败'));
      } finally {
        setLogLoading(false);
      }
    },
    [monitorId, operationLog.current, operationLog.pageSize],
  );

  useEffect(() => {
    void loadWorkspace();
  }, [loadWorkspace]);

  useEffect(() => {
    if (activeTab === 'report') {
      void loadReport(reportDate);
    }
  }, [activeTab, loadReport, reportDate]);

  const run = useCallback(async () => {
    if (!monitorId) return;
    setRunning(true);
    let executionNo: string;
    try {
      const result = await runQualityMonitor(monitorId);
      executionNo = result.executionNo;
      message.success(`质量检查已提交：${executionNo}`);
    } catch (error) {
      message.error(errorMessage(error, '运行失败'));
      setRunning(false);
      return;
    }

    try {
      const status = await waitForExecution(executionNo);
      await loadWorkspace();
      if (!status) {
        message.warning('质量检查仍在执行，可在运行记录中继续查看进度');
      }
    } catch (error) {
      await loadWorkspace();
      message.warning(errorMessage(error, '质量检查已提交，但状态跟踪失败'));
    } finally {
      setRunning(false);
    }
  }, [loadWorkspace, monitorId]);

  const removeMonitor = useCallback(() => {
    if (!monitorId) return;
    Modal.confirm({
      title: '删除质量监控？',
      content: '删除后将保留历史执行记录，但不再允许继续运行。',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteQualityMonitor(monitorId);
          message.success('质量监控已删除');
          history.push('/data-quality/table-config');
        } catch (error) {
          message.error(errorMessage(error, '删除失败'));
        }
      },
    });
  }, [monitorId]);

  const openLog = useCallback(() => {
    setLogOpen(true);
    void loadOperationLog(1, operationLog.pageSize);
  }, [loadOperationLog, operationLog.pageSize]);

  return {
    activeTab,
    setActiveTab,
    workspace,
    report,
    reportDate,
    setReportDate,
    operationLog,
    loading,
    reportLoading,
    logLoading,
    logOpen,
    setLogOpen,
    running,
    loadWorkspace,
    loadOperationLog,
    run,
    removeMonitor,
    openLog,
  };
};
