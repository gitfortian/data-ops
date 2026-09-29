import { Drawer, Table, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';

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

/** 运行历史抽屉:四计数 + 状态 + 耗时(工单 122 验收)。job 为空 = 本项目全部任务。 */
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

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageCollectRuns({
        pageNo,
        pageSize,
        jobId: job?.id,
      });
      setRecords(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch {
      setRecords([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [job?.id, pageNo, pageSize]);

  useEffect(() => {
    if (open) void load();
  }, [open, load]);

  const columns: ColumnsType<CollectRunRecord> = [
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      width: 160,
      render: (value?: string) => formatMetadataTime(value),
    },
    {
      title: '触发',
      key: 'trigger',
      width: 110,
      render: (_, run) => (
        <span>
          {run.triggerType ? TRIGGER_LABELS[run.triggerType] : '-'}
          {/* DRY_RUN 触发已表达"预演",不重复打标;只在非预演触发却带 dryRun 位时补提示。 */}
          {run.dryRun && run.triggerType !== 'DRY_RUN' ? <Tag className="!ml-1">预演</Tag> : null}
        </span>
      ),
    },
    {
      title: '通道',
      dataIndex: 'providerType',
      width: 90,
      render: (value?: keyof typeof PROVIDER_LABELS) => (value ? PROVIDER_LABELS[value] : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: CollectRunRecord['status'], run) => {
        const status = value ?? 'FAILED';
        const tag = <Tag color={RUN_STATUS_COLORS[status]}>{RUN_STATUS_LABELS[status]}</Tag>;
        return run.errorMessage ? (
          <Tooltip title={run.errorMessage}>
            <span>{tag}</span>
          </Tooltip>
        ) : (
          tag
        );
      },
    },
    {
      title: '计数（总 / 新增 / 变更 / 未变）',
      key: 'counts',
      render: (_, run) => (
        <span className="tabular-nums">
          {run.cntTotal ?? 0} / {run.cntNew ?? 0} / {run.cntChanged ?? 0} / {run.cntUnchanged ?? 0}
          {(run.cntGone ?? 0) > 0 ? (
            <span className="ml-2 text-[#b54708]">消失 {run.cntGone}</span>
          ) : null}
          {(run.cntPartialFailed ?? 0) > 0 ? (
            <span className="ml-2 text-[#b42318]">部分失败 {run.cntPartialFailed}</span>
          ) : null}
        </span>
      ),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 90,
      render: (value?: number | null) => formatDuration(value),
    },
    {
      title: '操作人',
      dataIndex: 'createdBy',
      width: 100,
      render: (value?: string) => value || '-',
    },
  ];

  return (
    <Drawer
      open={open}
      width={860}
      placement="right"
      onClose={onClose}
      destroyOnClose
      title={
        <div className="text-[18px] font-semibold leading-7 text-[#101828]">
          运行历史{job ? ` · ${job.jobName}` : ''}
        </div>
      }
      styles={{ header: { padding: '18px 24px', borderBottom: '1px solid #eaecf0' }, body: { padding: 24 } }}
    >
      {job && !job.enabled ? (
        <div className="mb-3 text-[12px] text-[#98a2b3]">该任务当前停用，定时通道不会触发。</div>
      ) : null}
      <Table<CollectRunRecord>
        rowKey="runId"
        size="small"
        columns={columns}
        dataSource={records}
        loading={loading}
        locale={{
          emptyText: <YakEmpty compact title="还没有运行记录" description="点「预演」或「立即运行」后回到这里查看" />,
        }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (nextPageNo, nextPageSize) => {
            setPageNo(nextPageNo);
            setPageSize(nextPageSize);
          },
        }}
      />
    </Drawer>
  );
};

export default RunHistoryDrawer;
