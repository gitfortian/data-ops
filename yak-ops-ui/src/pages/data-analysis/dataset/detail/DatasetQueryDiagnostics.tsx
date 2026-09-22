import { ReloadOutlined } from '@ant-design/icons';
import YakOpsEmpty from '@/components/YakOpsEmpty';
import { YakButton } from '@/components/ui';
import {
  listDatasetQueryPerformance,
  type DatasetQueryPerformance,
  type DatasetQueryStatus,
} from '@/services/dataset';
import {
  Alert,
  InputNumber,
  Select,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { DETAIL_TABLE_CLASS } from './components/DatasetDetailPrimitives';

interface DatasetQueryDiagnosticsProps {
  datasetId: string;
}

type StatusFilter = 'ALL' | DatasetQueryStatus;

const STATUS_META: Record<DatasetQueryStatus, { label: string; color: string }> = {
  SUCCESS: { label: '成功', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'default' },
  FAILED: { label: '失败', color: 'error' },
  TIMEOUT: { label: '超时', color: 'warning' },
};

const formatDateTime = (value?: string) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const shortHash = (value?: string | null) =>
  value ? value.slice(0, 12) : '-';

export default function DatasetQueryDiagnostics({
  datasetId,
}: DatasetQueryDiagnosticsProps) {
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [minTotalMillis, setMinTotalMillis] = useState(0);
  const [limit, setLimit] = useState(100);
  const [records, setRecords] = useState<DatasetQueryPerformance[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setRecords(
        await listDatasetQueryPerformance({
          datasetIds: [datasetId],
          statuses: status === 'ALL' ? undefined : [status],
          minTotalMillis: minTotalMillis > 0 ? minTotalMillis : undefined,
          limit,
        }),
      );
    } catch (loadError) {
      setRecords([]);
      setError(
        loadError instanceof Error
          ? loadError.message
          : '加载 Dataset 运行诊断失败',
      );
    } finally {
      setLoading(false);
    }
  }, [datasetId, limit, minTotalMillis, status]);

  useEffect(() => {
    void load();
  }, [load]);

  const summary = useMemo(() => {
    const failures = records.filter(
      (record) => record.status === 'FAILED',
    ).length;
    const timeouts = records.filter(
      (record) => record.status === 'TIMEOUT',
    ).length;
    const rejected = records.filter(
      (record) => record.status === 'REJECTED',
    ).length;
    const slow = records.filter((record) => record.totalMillis >= 3_000).length;
    return { failures, timeouts, rejected, slow };
  }, [records]);

  const columns: ColumnsType<DatasetQueryPerformance> = [
    {
      title: '开始时间',
      dataIndex: 'startedAt',
      width: 175,
      render: (value?: string) => (
        <span className="text-[12px] text-[#667085]">
          {formatDateTime(value)}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 92,
      render: (value: DatasetQueryStatus) => (
        <Tag bordered={false} color={STATUS_META[value].color}>
          {STATUS_META[value].label}
        </Tag>
      ),
    },
    {
      title: 'Query ID',
      dataIndex: 'queryId',
      width: 170,
      ellipsis: true,
      render: (value: string) => (
        <Tooltip title={value}>
          <span className="font-mono text-[12px] text-[#344054]">
            {value}
          </span>
        </Tooltip>
      ),
    },
    {
      title: '版本',
      dataIndex: 'datasetVersionNo',
      width: 84,
      render: (value?: number | null) => (value ? `DV${value}` : '-'),
    },
    {
      title: '总耗时',
      dataIndex: 'totalMillis',
      width: 100,
      sorter: (left, right) => left.totalMillis - right.totalMillis,
      render: (value: number) => (
        <span
          className={
            value >= 3_000
              ? 'font-medium text-[#d92d20]'
              : 'text-[#344054]'
          }
        >
          {value} ms
        </span>
      ),
    },
    {
      title: '阶段耗时',
      width: 230,
      render: (_, record) => (
        <Tooltip
          title={`prepare ${record.prepareMillis} / wait ${record.waitMillis} / execute ${record.executeMillis} / transfer ${record.transferMillis} ms`}
        >
          <span className="text-[12px] text-[#667085]">
            P {record.prepareMillis} · W {record.waitMillis} · E{' '}
            {record.executeMillis} · T {record.transferMillis}
          </span>
        </Tooltip>
      ),
    },
    {
      title: '结果',
      width: 105,
      render: (_, record) =>
        record.status === 'SUCCESS'
          ? `${record.returnedRows} 行${record.truncated ? ' · 截断' : ''}`
          : '-',
    },
    {
      title: '失败阶段 / 原因',
      width: 270,
      render: (_, record) =>
        record.status === 'SUCCESS' ? (
          <span className="text-[#98a2b3]">-</span>
        ) : (
          <Tooltip
            title={
              record.errorMessage ||
              record.errorType ||
              record.failureStage ||
              '-'
            }
          >
            <div className="max-w-[250px]">
              <div className="truncate text-[12px] font-medium text-[#344054]">
                {record.failureStage || 'UNKNOWN'}
              </div>
              <div className="mt-0.5 truncate text-[12px] text-[#667085]">
                {record.errorMessage || record.errorType || '-'}
              </div>
            </div>
          </Tooltip>
        ),
    },
    {
      title: 'SQL 指纹',
      dataIndex: 'sqlHash',
      width: 120,
      render: (value?: string | null) => (
        <Tooltip title={value || '暂无 SQL 指纹'}>
          <span className="font-mono text-[12px] text-[#667085]">
            {shortHash(value)}
          </span>
        </Tooltip>
      ),
    },
    {
      title: '脱敏 SQL',
      dataIndex: 'sql',
      width: 280,
      ellipsis: true,
      render: (value?: string | null) =>
        value ? (
          <Tooltip
            title={
              <pre className="m-0 max-w-[680px] whitespace-pre-wrap text-[12px]">
                {value}
              </pre>
            }
          >
            <span className="font-mono text-[12px] text-[#667085]">
              {value}
            </span>
          </Tooltip>
        ) : (
          '-'
        ),
    },
  ];

  return (
    <div className="space-y-4">
      <Alert
        type="info"
        showIcon
        message="诊断记录跨实例持久化；SQL 预览由后端移除字面量和注释后再保存，仅用于查询结构定位。"
      />

      <div className="flex flex-wrap items-end gap-3 rounded-md bg-[#f7f7f8] p-4">
        <div>
          <div className="mb-1.5 text-[12px] text-[#667085]">终态</div>
          <Select<StatusFilter>
            value={status}
            variant="filled"
            className="w-[140px]"
            options={[
              { value: 'ALL', label: '全部状态' },
              { value: 'SUCCESS', label: '成功' },
              { value: 'REJECTED', label: '已拒绝' },
              { value: 'FAILED', label: '失败' },
              { value: 'TIMEOUT', label: '超时' },
            ]}
            onChange={setStatus}
          />
        </div>

        <div>
          <div className="mb-1.5 text-[12px] text-[#667085]">
            最小总耗时
          </div>
          <Select
            value={minTotalMillis}
            variant="filled"
            className="w-[150px]"
            options={[
              { value: 0, label: '不限' },
              { value: 500, label: '≥ 500 ms' },
              { value: 1_000, label: '≥ 1 s' },
              { value: 3_000, label: '≥ 3 s' },
              { value: 5_000, label: '≥ 5 s' },
              { value: 10_000, label: '≥ 10 s' },
            ]}
            onChange={setMinTotalMillis}
          />
        </div>

        <div>
          <div className="mb-1.5 text-[12px] text-[#667085]">
            最近记录数
          </div>
          <InputNumber
            min={1}
            max={200}
            value={limit}
            variant="filled"
            className="w-[120px]"
            onChange={(value) => setLimit(value || 100)}
          />
        </div>

        <Tooltip title="刷新诊断">
          <YakButton
            iconOnly
            icon={<ReloadOutlined />}
            loading={loading}
            onClick={() => void load()}
          />
        </Tooltip>

        <div className="ml-auto flex flex-wrap gap-4 text-[12px] text-[#667085]">
          <span>失败 {summary.failures}</span>
          <span>超时 {summary.timeouts}</span>
          <span>拒绝 {summary.rejected}</span>
          <span>≥3s {summary.slow}</span>
        </div>
      </div>

      {error ? (
        <Alert
          type="error"
          showIcon
          message="运行诊断加载失败"
          description={error}
        />
      ) : null}

      <Table<DatasetQueryPerformance>
        rowKey="queryId"
        size="small"
        loading={loading}
        pagination={false}
        columns={columns}
        dataSource={records}
        scroll={{ x: 1680, y: 520 }}
        className={DETAIL_TABLE_CLASS}
        locale={{
          emptyText: (
            <div className="flex min-h-[260px] items-center justify-center">
              <YakOpsEmpty
                width={170}
                height={112}
                title="暂无运行诊断"
                description="执行 Dataset 查询后，成功、拒绝、失败和超时都会在这里形成终态证据。"
                showCaption
              />
            </div>
          ),
        }}
      />
    </div>
  );
}
