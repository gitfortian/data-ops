import { Alert, Button, Drawer, Table, Tag, Tooltip } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useRef, useState } from 'react';

import { YakEmpty } from '@/components/ui';
import { pageCollectRuns } from '@/services/metadata/api';
import type { CollectJobRecord, CollectRunRecord } from '@/services/metadata/types';
import {
  formatDuration,
  formatMetadataTime,
  PROVIDER_LABELS,
  RUN_STATUS_COLORS,
  RUN_STATUS_LABELS,
  TRIGGER_LABELS,
} from '../constants';

const countOrUnknown = (value?: number | null) => value == null ? '—' : value;

/** Historical runs remain distinct from the current catalog and the latest job configuration. */
const RunHistoryDrawer = ({
  open,
  job,
  onClose,
}: {
  open: boolean;
  job: CollectJobRecord | null;
  onClose: () => void;
}) => {
  const [records, setRecords] = useState<CollectRunRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [readError, setReadError] = useState(false);
  const requestSeq = useRef(0);

  useEffect(() => {
    setPageNo(1);
  }, [job?.id]);

  const load = useCallback(async () => {
    const ticket = ++requestSeq.current;
    setLoading(true);
    setReadError(false);
    try {
      const result = await pageCollectRuns({ pageNo, pageSize, jobId: job?.id });
      if (ticket !== requestSeq.current) return;
      setRecords(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch {
      if (ticket !== requestSeq.current) return;
      setRecords([]);
      setTotal(0);
      setReadError(true);
    } finally {
      if (ticket === requestSeq.current) setLoading(false);
    }
  }, [job?.id, pageNo, pageSize]);

  useEffect(() => {
    if (open) void load();
    return () => { requestSeq.current += 1; };
  }, [open, load]);

  const columns: ColumnsType<CollectRunRecord> = [
    {
      title: '运行时间',
      dataIndex: 'startedAt',
      width: 174,
      render: (value?: string) => <span className="text-[12px] text-[#64748b]">{formatMetadataTime(value)}</span>,
    },
    {
      title: '结果',
      dataIndex: 'status',
      width: 145,
      render: (value: CollectRunRecord['status'], run) => {
        const content = value ? (
          <Tag color={RUN_STATUS_COLORS[value]}>{RUN_STATUS_LABELS[value]}</Tag>
        ) : <Tag color="warning">未知</Tag>;
        return run.errorMessage ? (
          <Tooltip title={run.errorMessage}><span>{content}</span></Tooltip>
        ) : content;
      },
    },
    {
      title: '执行方式',
      key: 'trigger',
      width: 110,
      render: (_, run) => (
        <div className="text-[12px] text-[#59697e]">
          <div>{run.triggerType ? TRIGGER_LABELS[run.triggerType] : '—'}</div>
          {run.dryRun && run.triggerType !== 'DRY_RUN' ? (
            <Tag className="!mt-1">预演</Tag>
          ) : null}
        </div>
      ),
    },
    {
      title: '范围类型',
      dataIndex: 'providerType',
      width: 96,
      render: (value?: keyof typeof PROVIDER_LABELS) => <span className="text-[12px] text-[#64748b]">{value ? PROVIDER_LABELS[value] : '—'}</span>,
    },
    {
      title: '实体统计',
      key: 'counts',
      width: 260,
      render: (_, run) => (
        <div className="text-[12px] tabular-nums leading-5 text-[#64748b]">
          <div>总 {countOrUnknown(run.cntTotal)} · 新增 {countOrUnknown(run.cntNew)} · 更新 {countOrUnknown(run.cntChanged)} · 未变 {countOrUnknown(run.cntUnchanged)}</div>
          {(run.cntGone ?? 0) > 0 ? <span className="mr-3 text-[#ad743b]">撤销 {run.cntGone}</span> : null}
          {(run.cntPartialFailed ?? 0) > 0 ? <span className="text-[#be5550]">局部失败 {run.cntPartialFailed}</span> : null}
        </div>
      ),
    },
    {
      title: '耗时 / 操作人',
      key: 'duration',
      width: 124,
      render: (_, run) => (
        <div className="text-[12px] leading-5 text-[#64748b]">
          <div>{formatDuration(run.durationMs)}</div>
          <div className="text-[#9ba5b4]">{run.createdBy || '—'}</div>
        </div>
      ),
    },
  ];

  return (
    <Drawer
      open={open}
      width="min(940px, 100vw)"
      placement="right"
      onClose={onClose}
      destroyOnClose
      title={
        <div>
          <div className="text-[19px] font-semibold text-[#243248]">运行历史{job ? ' · ' + job.jobName : ''}</div>
          <div className="mt-1 text-[12px] font-normal text-[#8794a8]">每一条记录代表一次真实 Run 或预演。历史范围与当前任务配置可能不同。</div>
        </div>
      }
      styles={{
        header: { padding: '20px 24px', borderBottom: '1px solid #e9edf3' },
        body: { padding: '22px 24px', background: '#f7f8fb' },
      }}
    >
      {job && !job.enabled ? (
        <div className="mb-4 rounded-xl border border-solid border-[#e6eaf0] bg-white px-4 py-3 text-[12px] text-[#718096]">
          任务当前停用，仅关闭周期调度；已保存的历史 Run 不受影响。
        </div>
      ) : null}
      {readError ? (
        <Alert type="error" showIcon className="!mb-4" message="运行历史暂时无法查询"
          description="失败不代表该任务从未运行，请核查服务或权限后重试。"
          action={<Button size="small" onClick={() => void load()}>重试</Button>} />
      ) : null}
      <section className="overflow-hidden rounded-2xl border border-solid border-[#e6eaf0] bg-white px-4 pb-3 pt-4">
        <div className="mb-3 flex items-center justify-between gap-3">
          <div>
            <div className="text-[14px] font-semibold text-[#2f3e52]">历史执行记录</div>
            <div className="mt-1 text-[12px] text-[#94a0b0]">统计包含数据库、表、列等实体，并非表数量。</div>
          </div>
          <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>刷新</Button>
        </div>
        <Table<CollectRunRecord>
          rowKey="runId"
          size="small"
          columns={columns}
          dataSource={records}
          loading={loading}
          scroll={{ x: 850 }}
          locale={{
            emptyText: readError
              ? <YakEmpty compact title="未能读取历史" description="请先修复查询错误，不能据此判断无记录" />
              : <YakEmpty compact title="还没有运行记录" description="保存任务后可预演或明确触发一次正式采集" />,
          }}
          pagination={{
            current: pageNo,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => '共 ' + count + ' 条',
            onChange: (nextPageNo, nextPageSize) => {
              setPageNo(nextPageNo);
              setPageSize(nextPageSize);
            },
          }}
        />
      </section>
    </Drawer>
  );
};

export default RunHistoryDrawer;
