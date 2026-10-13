import { Alert, Button, Drawer, Table, Tag, Tooltip } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useRef, useState } from 'react';

import RunResultModal from './RunResultModal';
import '../index.less';
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

const countOrUnknown = (value?: number | null) => (value == null ? '—' : value);

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
  const [selectedRun, setSelectedRun] = useState<CollectRunRecord | null>(null);
  const [records, setRecords] = useState<CollectRunRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [readError, setReadError] = useState(false);
  const requestSeq = useRef(0);

  useEffect(() => {
    setPageNo(1);
    setSelectedRun(null);
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
    return () => {
      requestSeq.current += 1;
    };
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
        ) : (
          <Tag color="warning">未知</Tag>
        );
        return run.errorMessage ? (
          <Tooltip title={run.errorMessage}>
            <span>{content}</span>
          </Tooltip>
        ) : (
          content
        );
      },
    },
    {
      title: '执行方式',
      key: 'trigger',
      width: 110,
      render: (_, run) => (
        <div className="text-[12px] text-[#59697e]">
          <div>{run.triggerType ? TRIGGER_LABELS[run.triggerType] : '—'}</div>
          {run.dryRun && run.triggerType !== 'DRY_RUN' ? <Tag className="!mt-1">采集预览</Tag> : null}
        </div>
      ),
    },
    {
      title: '范围类型',
      dataIndex: 'providerType',
      width: 96,
      render: (value?: keyof typeof PROVIDER_LABELS) => (
        <span className="text-[12px] text-[#64748b]">{value ? PROVIDER_LABELS[value] : '—'}</span>
      ),
    },
    {
      title: '元数据统计',
      key: 'counts',
      width: 260,
      render: (_, run) => (
        <div className="text-[12px] tabular-nums leading-5 text-[#64748b]">
          <div>
            总 {countOrUnknown(run.cntTotal)} · 新增 {countOrUnknown(run.cntNew)} · 更新{' '}
            {countOrUnknown(run.cntChanged)} · 未变 {countOrUnknown(run.cntUnchanged)}
          </div>
          {(run.cntGone ?? 0) > 0 ? <span className="mr-3 text-[#ad743b]">撤销 {run.cntGone}</span> : null}
          {(run.cntPartialFailed ?? 0) > 0 ? (
            <span className="text-[#be5550]">局部失败 {run.cntPartialFailed}</span>
          ) : null}
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
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_, run) => (
        <Button type="link" onClick={() => setSelectedRun(run)}>
          查看
        </Button>
      ),
    },
  ];

  return (
    <Drawer
      className="collect-history"
      open={open}
      width="min(940px, 100vw)"
      placement="right"
      onClose={onClose}
      destroyOnClose
      title={
        <div>
          <div className="text-[19px] font-semibold text-[#243248]">运行历史{job ? ' · ' + job.jobName : ''}</div>
        </div>
      }
      styles={{
        header: { padding: '20px 24px', borderBottom: '1px solid #e9edf3' },
        body: { padding: '22px 24px', background: '#fff' },
      }}
    >
      {readError ? (
        <Alert
          type="error"
          showIcon
          className="!mb-4"
          message="运行历史暂时无法查询"
          action={
            <Button size="small" onClick={() => void load()}>
              重试
            </Button>
          }
        />
      ) : null}
      <section>
        <div className="collect-history-toolbar">
          <span>{readError ? '—' : `${total} 条运行记录`}</span>
          <Button size="small" icon={<ReloadOutlined />} loading={loading} onClick={() => void load()}>
            刷新
          </Button>
        </div>
        <Table<CollectRunRecord>
          rowKey="runId"
          size="small"
          columns={columns}
          dataSource={records}
          loading={loading}
          scroll={{ x: 850 }}
          locale={{
            emptyText: readError ? (
              <YakEmpty compact title="未能读取历史" />
            ) : (
              <YakEmpty compact title="还没有运行记录" />
            ),
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
      <RunResultModal
        run={open ? selectedRun : null}
        title={'运行结果' + (job ? ' · ' + job.jobName : '')}
        onClose={() => setSelectedRun(null)}
      />
    </Drawer>
  );
};

export default RunHistoryDrawer;
