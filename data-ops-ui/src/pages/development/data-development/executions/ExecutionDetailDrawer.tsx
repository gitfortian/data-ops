import { getDevelopmentTaskExecution } from '@/services/data-development';
import { history, useAccess, useIntl } from '@umijs/max';
import {
  Button,
  Descriptions,
  Drawer,
  Empty,
  Modal,
  Spin,
  Table,
  Tooltip,
  message,
} from 'antd';
import moment from 'moment';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  cancelDevelopmentTaskExecution,
  retryDevelopmentTaskExecution,
} from '../service';
import type {
  DevelopmentId,
  DevelopmentSqlRunOutput,
  DevelopmentTaskExecutionDetail,
  DevelopmentTaskExecutionSummary,
} from '../types';
import {
  developmentNodeUrl,
  executionFailureMessageId,
  executionLogText,
  executionSqlOutput,
  hasExecutionOutput,
  isActiveExecutionStatus,
  isRetryableExecutionStatus,
} from './executionExperience';

interface ExecutionDetailDrawerProps {
  open: boolean;
  record?: DevelopmentTaskExecutionSummary;
  onClose: () => void;
  onChanged?: () => void;
}

const statusClassName: Record<string, string> = {
  PENDING: 'bg-[#f2f4f7] text-[#667085]',
  RUNNING: 'bg-[#eff8ff] text-[#175cd3]',
  SUCCESS: 'bg-[#ecfdf3] text-[#027a48]',
  FAILED: 'bg-[#fef3f2] text-[#b42318]',
  CANCELLED: 'bg-[#f2f4f7] text-[#667085]',
  TIMEOUT: 'bg-[#fff6ed] text-[#c4320a]',
};

const formatDuration = (duration?: number | null) => {
  if (duration === null || duration === undefined) return '-';
  if (duration < 1000) return `${duration} ms`;
  if (duration < 60_000) {
    return `${(duration / 1000).toFixed(duration < 10_000 ? 2 : 1)} s`;
  }
  return `${(duration / 60_000).toFixed(1)} min`;
};

const outputCell = (value: unknown) => {
  if (value === null || value === undefined) return '-';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
};

const ExecutionDetailDrawer = ({
  open,
  record,
  onClose,
  onChanged,
}: ExecutionDetailDrawerProps) => {
  const intl = useIntl();
  const access = useAccess();
  const canExecute = access.hasPermission('data-development:execute');
  const [detail, setDetail] = useState<DevelopmentTaskExecutionDetail>();
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [retryChain, setRetryChain] = useState<DevelopmentTaskExecutionDetail[]>([]);

  const readDetail = useCallback(async (id: DevelopmentId) => {
    const detail = await getDevelopmentTaskExecution(id);
    if (!detail) throw new Error('execution detail unavailable');
    return detail;
  }, []);

  const refreshDetail = useCallback(
    async (id: DevelopmentId, blocking = false) => {
      if (blocking) setLoading(true);
      try {
        const next = await readDetail(id);
        setDetail(next);
        return next;
      } catch {
        if (blocking) {
          message.error(
            intl.formatMessage({ id: 'pages.dataDevelopment.execution.detailFailed' }),
          );
        }
        return undefined;
      } finally {
        if (blocking) setLoading(false);
      }
    },
    [intl, readDetail],
  );

  useEffect(() => {
    if (!open || !record?.id) {
      setDetail(undefined);
      setRetryChain([]);
      return;
    }
    setDetail(undefined);
    setRetryChain([]);
    void refreshDetail(record.id, true);
  }, [open, record?.id, refreshDetail]);

  useEffect(() => {
    if (!open || !detail?.id || !isActiveExecutionStatus(detail.status)) return;
    const id = detail.id;
    const timer = window.setInterval(() => {
      void refreshDetail(id).then((next) => {
        if (next && !isActiveExecutionStatus(next.status)) onChanged?.();
      });
    }, 1500);
    return () => window.clearInterval(timer);
  }, [detail?.id, detail?.status, onChanged, open, refreshDetail]);

  useEffect(() => {
    if (!open || !detail?.id) return;
    let disposed = false;
    const current = detail;
    const loadChain = async () => {
      const seen = new Set<string>([String(current.id)]);
      const chain: DevelopmentTaskExecutionDetail[] = [current];
      let sourceId = current.retryOfExecutionId;
      let hops = 0;
      while (sourceId && hops < 20 && !seen.has(String(sourceId))) {
        seen.add(String(sourceId));
        hops += 1;
        try {
          const source = await readDetail(sourceId);
          chain.unshift(source);
          sourceId = source.retryOfExecutionId;
        } catch {
          break;
        }
      }
      if (!disposed) setRetryChain(chain);
    };
    void loadChain();
    return () => {
      disposed = true;
    };
  }, [detail?.id, open, readDetail]);

  const triggerLabel = (value?: string) => {
    if (value === 'MANUAL') return intl.formatMessage({ id: 'pages.dataDevelopment.execution.manual' });
    if (value === 'WORKFLOW') return intl.formatMessage({ id: 'pages.dataDevelopment.execution.workflow' });
    if (value === 'SCHEDULE') return intl.formatMessage({ id: 'pages.dataDevelopment.execution.schedule' });
    return value || '-';
  };

  const statusLabel = (value?: string) => {
    const normalized = String(value || '').toUpperCase();
    const ids: Record<string, string> = {
      PENDING: 'pages.dataDevelopment.execution.pending',
      RUNNING: 'pages.dataDevelopment.execution.running',
      SUCCESS: 'pages.dataDevelopment.execution.success',
      FAILED: 'pages.dataDevelopment.execution.failed',
      CANCELLED: 'pages.dataDevelopment.execution.cancelled',
      TIMEOUT: 'pages.dataDevelopment.execution.timeout',
    };
    return ids[normalized]
      ? intl.formatMessage({ id: ids[normalized] })
      : normalized || '-';
  };

  const sqlOutput = useMemo<DevelopmentSqlRunOutput | undefined>(
    () => (detail?.taskType === 'SQL' ? executionSqlOutput(detail.output) : undefined),
    [detail?.output, detail?.taskType],
  );
  const logText = useMemo(() => executionLogText(detail?.output), [detail?.output]);
  const failureMessageId = executionFailureMessageId(detail?.failureReason);

  const sqlColumns = useMemo(
    () =>
      (sqlOutput?.columns || []).map((column, index) => ({
        title: column.label || column.name || `Column ${index + 1}`,
        dataIndex: `c${index}`,
        key: `c${index}`,
        ellipsis: true,
        render: (value: unknown) => (
          <Tooltip title={outputCell(value)}>
            <span className="font-mono text-[12px] text-[#344054]">
              {outputCell(value)}
            </span>
          </Tooltip>
        ),
      })),
    [sqlOutput?.columns],
  );

  const sqlRows = useMemo(
    () =>
      (sqlOutput?.rows || []).map((row, rowIndex) => {
        const mapped: Record<string, unknown> = { key: String(rowIndex) };
        row.forEach((value, columnIndex) => {
          mapped[`c${columnIndex}`] = value;
        });
        return mapped;
      }),
    [sqlOutput?.rows],
  );

  const runAction = async (
    action: () => Promise<unknown>,
    successId: string,
  ) => {
    setActionLoading(true);
    try {
      await action();
      message.success(intl.formatMessage({ id: successId }));
      onChanged?.();
    } catch (error) {
      message.error(
        error instanceof Error && error.message
          ? error.message
          : intl.formatMessage({ id: 'pages.dataDevelopment.execution.actionFailed' }),
      );
    } finally {
      setActionLoading(false);
    }
  };

  const confirmCancel = () => {
    if (!detail?.id || !isActiveExecutionStatus(detail.status)) return;
    Modal.confirm({
      title: intl.formatMessage({ id: 'pages.dataDevelopment.execution.cancelConfirmTitle' }),
      content: intl.formatMessage({ id: 'pages.dataDevelopment.execution.cancelConfirmContent' }),
      okText: intl.formatMessage({ id: 'pages.dataDevelopment.execution.cancel' }),
      cancelText: intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' }),
      okButtonProps: { danger: true },
      onOk: () =>
        runAction(async () => {
          const response = await cancelDevelopmentTaskExecution(detail.id);
          if (!response.data) throw new Error('cancel result unavailable');
          setDetail(response.data);
        }, 'pages.dataDevelopment.execution.cancelledSuccess'),
    });
  };

  const confirmRetry = () => {
    if (!detail?.id || !isRetryableExecutionStatus(detail.status)) return;
    const sourceId = detail.id;
    Modal.confirm({
      title: intl.formatMessage({ id: 'pages.dataDevelopment.execution.retryConfirmTitle' }),
      content: intl.formatMessage({ id: 'pages.dataDevelopment.execution.retryConfirmContent' }),
      okText: intl.formatMessage({ id: 'pages.dataDevelopment.execution.retry' }),
      cancelText: intl.formatMessage({ id: 'pages.dataDevelopment.common.cancel' }),
      onOk: () =>
        runAction(async () => {
          const response = await retryDevelopmentTaskExecution(sourceId);
          const submission = response.data;
          if (!submission?.id) throw new Error('retry submission unavailable');
          const next = await readDetail(submission.id);
          setDetail(next);
        }, 'pages.dataDevelopment.execution.retriedSuccess'),
    });
  };

  const renderResult = () => {
    if (!detail) return null;
    if (detail.taskType === 'SQL' && sqlOutput?.kind === 'RESULT_SET') {
      return (
        <div>
          <div className="mb-2 flex items-center gap-3 text-[12px] text-[#667085]">
            <span>
              {intl.formatMessage(
                { id: 'pages.dataDevelopment.execution.returnedRows' },
                { count: sqlOutput.returnedRows ?? sqlRows.length },
              )}
            </span>
            {sqlOutput.dataSourceId ? (
              <span className="font-mono">dataSource={sqlOutput.dataSourceId}</span>
            ) : null}
            {sqlOutput.truncated ? (
              <span className="text-[#b54708]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.truncated' })}
              </span>
            ) : null}
          </div>
          <Table
            size="small"
            bordered
            pagination={false}
            columns={sqlColumns}
            dataSource={sqlRows}
            scroll={{ x: 'max-content', y: 260 }}
          />
        </div>
      );
    }
    if (detail.taskType === 'SQL' && sqlOutput?.kind === 'UPDATE_COUNT') {
      return (
        <div className="rounded-md border border-[#d1fadf] bg-[#ecfdf3] px-3 py-3 text-[13px] text-[#027a48]">
          {intl.formatMessage(
            { id: 'pages.dataDevelopment.execution.affectedRows' },
            { count: sqlOutput.affectedRows ?? 0 },
          )}
        </div>
      );
    }
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={intl.formatMessage({ id: 'pages.dataDevelopment.execution.noResult' })}
      />
    );
  };

  return (
    <Drawer
      title={intl.formatMessage({ id: 'pages.dataDevelopment.execution.detailTitle' })}
      placement="right"
      width={820}
      open={open}
      onClose={onClose}
      extra={
        detail ? (
          <div className="flex items-center gap-2">
            {canExecute && isActiveExecutionStatus(detail.status) ? (
              <Button danger size="small" loading={actionLoading} onClick={confirmCancel}>
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.cancel' })}
              </Button>
            ) : null}
            {canExecute && isRetryableExecutionStatus(detail.status) ? (
              <Button size="small" loading={actionLoading} onClick={confirmRetry}>
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.retry' })}
              </Button>
            ) : null}
          </div>
        ) : null
      }
    >
      {loading ? (
        <div className="flex h-64 items-center justify-center"><Spin size="small" /></div>
      ) : detail ? (
        <div className="space-y-6">
          <Descriptions size="small" column={2} bordered>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.taskName' })}>
              {detail.taskName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.nodeId' })}>
              <Button
                type="link"
                size="small"
                className="!h-auto !p-0"
                onClick={() => history.push(developmentNodeUrl(detail.nodeId))}
              >
                {detail.nodeId}
              </Button>
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.taskType' })}>
              {detail.taskType}
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.status' })}>
              <span className={[
                'inline-flex h-6 items-center rounded-md px-2 text-[12px] font-medium',
                statusClassName[detail.status] || 'bg-[#f2f4f7] text-[#667085]',
              ].join(' ')}>
                {statusLabel(detail.status)}
              </span>
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.execution.trigger' })}>
              {triggerLabel(detail.triggerType)}
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.operator' })}>
              {detail.operatorName || '-'}
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.startTime' })}>
              {detail.startTime ? moment(detail.startTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.execution.endTime' })}>
              {detail.endTime ? moment(detail.endTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.duration' })}>
              {formatDuration(detail.durationMs)}
            </Descriptions.Item>
            <Descriptions.Item label="schemaVersion">
              {detail.schemaVersion}
            </Descriptions.Item>
            <Descriptions.Item label={intl.formatMessage({ id: 'pages.dataDevelopment.common.runtimeExecution' })} span={2}>
              <span className="break-all font-mono text-[12px]">{detail.runtimeExecutionId || '-'}</span>
            </Descriptions.Item>
          </Descriptions>

          {isActiveExecutionStatus(detail.status) ? (
            <div className="rounded-md border border-[#b2ddff] bg-[#eff8ff] px-3 py-2 text-[12px] leading-5 text-[#175cd3]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.state.active' })}
            </div>
          ) : detail.status === 'CANCELLED' ? (
            <div className="rounded-md border border-[#eaecf0] bg-[#f9fafb] px-3 py-2 text-[12px] leading-5 text-[#475467]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.state.cancelled' })}
            </div>
          ) : null}

          <section>
            <div className="mb-2 flex items-center justify-between">
              <div className="text-[13px] font-semibold text-[#344054]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.retryChain' })}
              </div>
              {detail.retryOfExecutionId ? (
                <span className="text-[12px] text-[#667085]">
                  {intl.formatMessage({ id: 'pages.dataDevelopment.execution.retrySource' })} #{detail.retryOfExecutionId}
                </span>
              ) : (
                <span className="text-[12px] text-[#667085]">
                  {intl.formatMessage({ id: 'pages.dataDevelopment.execution.originalExecution' })}
                </span>
              )}
            </div>
            <div className="flex flex-wrap items-center gap-1 rounded-md border border-[#eaecf0] bg-[#fafafa] px-3 py-2">
              {(retryChain.length ? retryChain : [detail]).map((item, index) => (
                <div key={String(item.id)} className="flex items-center gap-1">
                  {index > 0 ? <span className="text-[#d0d5dd]">→</span> : null}
                  <Button
                    type="link"
                    size="small"
                    className={item.id === detail.id ? '!px-1 !font-semibold' : '!px-1'}
                    onClick={() => void refreshDetail(item.id, true)}
                  >
                    #{item.id} · {statusLabel(item.status)}
                  </Button>
                </div>
              ))}
            </div>
          </section>

          {(detail.failureReason || detail.errorMessage) ? (
            <section>
              <div className="mb-2 text-[13px] font-semibold text-[#344054]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.error' })}
              </div>
              {detail.failureReason ? (
                <div className="rounded-md border border-[#fedf89] bg-[#fffaeb] px-3 py-2 text-[12px] leading-5 text-[#93370d]">
                  <div className="font-medium">
                    {failureMessageId
                      ? intl.formatMessage({ id: failureMessageId })
                      : detail.failureReason}
                  </div>
                  <div className="mt-1 font-mono text-[12px] text-[#b54708]">
                    {detail.failureReason}
                  </div>
                </div>
              ) : null}
              {detail.errorMessage ? (
                <pre className="mt-2 max-h-[220px] overflow-auto whitespace-pre-wrap rounded-md bg-[#fef3f2] px-3 py-2 text-[12px] leading-5 text-[#b42318]">
                  {detail.errorMessage}
                </pre>
              ) : null}
            </section>
          ) : null}

          <section>
            <div className="mb-2 text-[13px] font-semibold text-[#344054]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.result' })}
            </div>
            {renderResult()}
          </section>

          <section>
            <div className="mb-2 text-[13px] font-semibold text-[#344054]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.logs' })}
            </div>
            {logText ? (
              <pre className="max-h-[280px] overflow-auto whitespace-pre-wrap rounded-md border border-[#eaecf0] bg-[#101828] p-3 text-[12px] leading-5 text-[#f2f4f7]">{logText}</pre>
            ) : (
              <div className="rounded-md border border-[#eaecf0] bg-[#fafafa] px-3 py-3 text-[12px] text-[#667085]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.noLogs' })}
              </div>
            )}
          </section>

          <section>
            <div className="mb-2 text-[13px] font-semibold text-[#344054]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.rawOutput' })}
            </div>
            {hasExecutionOutput(detail.output) ? (
              <pre className="max-h-[320px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{JSON.stringify(detail.output, null, 2)}</pre>
            ) : (
              <div className="rounded-md border border-[#eaecf0] bg-[#fafafa] px-3 py-3 text-[12px] text-[#667085]">
                {intl.formatMessage({ id: 'pages.dataDevelopment.execution.noOutput' })}
              </div>
            )}
          </section>

          <section>
            <div className="mb-2 text-[13px] font-semibold text-[#344054]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.definitionSnapshot' })}
            </div>
            <div className="mb-2 rounded-md border border-[#b2ddff] bg-[#eff8ff] px-3 py-2 text-[12px] leading-5 text-[#175cd3]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.definitionSnapshotHint' })}
            </div>
            <div className="mb-1 text-[12px] font-medium text-[#667085]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.content' })}
            </div>
            <pre className="max-h-[280px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{detail.content || '-'}</pre>
            <div className="mb-1 mt-3 text-[12px] font-medium text-[#667085]">
              {intl.formatMessage({ id: 'pages.dataDevelopment.execution.config' })}
            </div>
            <pre className="max-h-[220px] overflow-auto rounded-md border border-[#eaecf0] bg-[#fafafa] p-3 text-[12px] leading-5 text-[#344054]">{detail.configJson || '{}'}</pre>
          </section>
        </div>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={intl.formatMessage({ id: 'pages.dataDevelopment.execution.detailEmpty' })}
        />
      )}
    </Drawer>
  );
};

export default ExecutionDetailDrawer;
