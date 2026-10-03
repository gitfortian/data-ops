import { YakEmpty } from '@/components/ui';
import { PageHeader, StatCard } from '@/components/ui/PagePresentation';
import { listDataSourceOptions } from '@/services/data-source/api';
import {
  getSqlExecutionDetail,
  getSqlExecutionSummary,
  pageSqlExecutions,
} from '@/services/sql-execution/api';
import type {
  SqlExecutionAuditQuery,
  SqlExecutionAuditRecord,
  SqlExecutionAuditSummary,
  SqlStatementAuditRecord,
} from '@/services/sql-execution/types';
import {
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Input,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';

const { RangePicker } = DatePicker;

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待执行', color: 'default' },
  RUNNING: { label: '执行中', color: 'blue' },
  CANCELLING: { label: '取消中', color: 'gold' },
  SUCCEEDED: { label: '成功', color: 'green' },
  FAILED: { label: '失败', color: 'red' },
  CANCELLED: { label: '已取消', color: 'orange' },
  TIMED_OUT: { label: '超时', color: 'volcano' },
};

const fmt = (value?: string) =>
  value ? String(value).replace('T', ' ').slice(0, 19) : '-';
const statusTag = (value?: string) => {
  const meta = value ? STATUS_META[value] : undefined;
  return <Tag color={meta?.color ?? 'default'}>{meta?.label ?? value ?? '-'}</Tag>;
};
const initialDataSourceId = () =>
  new URLSearchParams(window.location.search).get('dataSourceId') ?? '';

const SqlExecutionAuditPage = () => {
  const [records, setRecords] = useState<SqlExecutionAuditRecord[]>([]);
  const [summary, setSummary] = useState<SqlExecutionAuditSummary>();
  const [dataSourceOptions, setDataSourceOptions] = useState<
    { label: string; value: string }[]
  >([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [executionId, setExecutionId] = useState('');
  const [dataSourceId, setDataSourceId] = useState(initialDataSourceId);
  const [caller, setCaller] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [range, setRange] = useState<[dayjs.Dayjs, dayjs.Dayjs] | null>(null);
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<{
    execution: SqlExecutionAuditRecord;
    statements: SqlStatementAuditRecord[];
  } | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const filters = useCallback((): SqlExecutionAuditQuery => ({
    executionId: executionId.trim() || undefined,
    dataSourceId: dataSourceId.trim() || undefined,
    caller: caller.trim() || undefined,
    status,
    startedFrom: range?.[0] ? range[0].format('YYYY-MM-DDTHH:mm:ss') : undefined,
    startedTo: range?.[1] ? range[1].format('YYYY-MM-DDTHH:mm:ss') : undefined,
  }), [executionId, dataSourceId, caller, status, range]);

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageSqlExecutions({ ...filters(), pageNo: p, pageSize: s });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载 SQL 执行记录失败');
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, load]);

  useEffect(() => {
    setPageNo(1);
  }, [filters]);

  useEffect(() => {
    getSqlExecutionSummary(filters())
      .then(setSummary)
      .catch(() => undefined);
  }, [filters]);

  useEffect(() => {
    listDataSourceOptions()
      .then((options) =>
        setDataSourceOptions(
          (options ?? []) as { label: string; value: string }[],
        ),
      )
      .catch(() => undefined);
  }, []);

  const openDetail = async (id: string) => {
    setDetailLoading(true);
    try {
      setDetail(await getSqlExecutionDetail(id));
    } catch {
      message.error('加载执行详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  const columns: ColumnsType<SqlExecutionAuditRecord> = [
    { title: '开始时间', dataIndex: 'startedAt', width: 165, render: fmt },
    {
      title: '执行 ID',
      dataIndex: 'executionId',
      width: 210,
      ellipsis: true,
      render: (value: string) => (
        <Button type="link" className="!px-0" size="small" onClick={() => void openDetail(value)}>
          {value}
        </Button>
      ),
    },
    { title: '数据源', dataIndex: 'dataSourceId', width: 100, render: (v?: string) => v || '-' },
    { title: '调用方', dataIndex: 'caller', width: 130, ellipsis: true, render: (v?: string) => v || '-' },
    { title: '操作人', dataIndex: 'operatorName', width: 110, render: (v?: string) => v || '-' },
    {
      title: '语句',
      key: 'statements',
      width: 100,
      render: (_, r) => `${r.succeededStatementCount}/${r.statementCount}`,
    },
    {
      title: '行数(返回/影响)',
      key: 'rows',
      width: 130,
      render: (_, r) => `${r.returnedRows} / ${r.affectedRows}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 96,
      render: (v: string) => statusTag(v),
    },
    {
      title: '耗时',
      dataIndex: 'durationMs',
      width: 90,
      render: (v: number) => `${v} ms`,
      sorter: (a, b) => a.durationMs - b.durationMs,
    },
  ];

  const statementColumns: ColumnsType<SqlStatementAuditRecord> = [
    { title: '#', dataIndex: 'statementIndex', width: 44 },
    { title: '类型', dataIndex: 'statementType', width: 96, render: (v?: string) => v || '-' },
    { title: '状态', dataIndex: 'status', width: 90, render: (v: string) => statusTag(v) },
    { title: '耗时', dataIndex: 'durationMs', width: 88, render: (v: number) => `${v} ms` },
    {
      title: '行数',
      key: 'rows',
      width: 100,
      render: (_, r) => `${r.returnedRows} / ${r.affectedRows}`,
    },
    {
      title: 'SQL',
      dataIndex: 'sqlPreview',
      render: (v: string, r) => (
        <div className="flex flex-col gap-1">
          <code className="whitespace-pre-wrap break-all rounded bg-[#f6f7fa] px-2 py-1 text-[12px]">
            {v || r.sqlFingerprint || '-'}
          </code>
          {r.truncated ? <Tag color="gold">已截断</Tag> : null}
          {r.errorMessage ? (
            <span className="text-[12px] text-[#d92d20]">{r.errorMessage}</span>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <PageHeader
        title="SQL 执行审计"
        subtitle="数据源上执行过的 SQL 一本账：执行/语句两级留痕、耗时与失败原因"
      />

      {summary ? (
        <div className="mt-4 grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
          <StatCard label="执行总数" value={summary.total} />
          <StatCard label="成功率" value={`${Math.round(summary.successRate * 100)}%`} hint={`成功 ${summary.succeeded} / 失败 ${summary.failed}`} />
          <StatCard label="取消/超时" value={`${summary.cancelled} / ${summary.timedOut}`} accent="#f79009" />
          <StatCard label="平均耗时" value={`${Math.round(summary.avgDurationMs)} ms`} hint={`P95 ${summary.p95DurationMs} ms · 最大 ${summary.maxDurationMs} ms`} />
          <StatCard label="返回行数" value={summary.returnedRows} />
          <StatCard label="影响行数" value={summary.affectedRows} />
        </div>
      ) : null}
      {summary?.statementTypes?.length ? (
        <Space size={6} wrap className="mt-3">
          {summary.statementTypes.map((item) => (
            <Tag key={item.statementType}>{item.statementType}: {item.count}</Tag>
          ))}
        </Space>
      ) : null}

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Input allowClear placeholder="按执行 ID 搜索" className="!w-52" value={executionId} onChange={(e) => setExecutionId(e.target.value)} />
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="按数据源"
          className="!w-44"
          value={dataSourceId || undefined}
          onChange={(value?: string) => setDataSourceId(value ?? '')}
          options={dataSourceOptions}
        />
        <Input allowClear placeholder="按调用方搜索" className="!w-40" value={caller} onChange={(e) => setCaller(e.target.value)} />
        <Select
          allowClear
          placeholder="按状态"
          className="!w-32"
          value={status}
          onChange={setStatus}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({ label: meta.label, value }))}
        />
        <RangePicker showTime value={range} onChange={(v) => setRange(v as [dayjs.Dayjs, dayjs.Dayjs] | null)} />
      </div>

      <Table<SqlExecutionAuditRecord>
        rowKey="executionId"
        size="middle"
        className="mt-4"
        columns={columns}
        dataSource={records}
        loading={loading}
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title="暂无 SQL 执行记录"
              description="数据集/开发任务等经数据源执行 SQL 后自动留痕"
            />
          ),
        }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (p, s) => {
            setPageNo(p);
            setPageSize(s);
          },
        }}
      />

      <Drawer
        width={880}
        open={Boolean(detail) || detailLoading}
        title={detail?.execution.executionId ?? '执行详情'}
        onClose={() => setDetail(null)}
      >
        <Spin spinning={detailLoading}>
          {detail ? (
            <>
              <Descriptions size="small" column={2} bordered>
                <Descriptions.Item label="数据源">{detail.execution.dataSourceId || '-'}</Descriptions.Item>
                <Descriptions.Item label="状态">{statusTag(detail.execution.status)}</Descriptions.Item>
                <Descriptions.Item label="调用方">{detail.execution.caller || '-'}</Descriptions.Item>
                <Descriptions.Item label="引用">{detail.execution.callerReference || '-'}</Descriptions.Item>
                <Descriptions.Item label="操作人">{detail.execution.operatorName || '-'}</Descriptions.Item>
                <Descriptions.Item label="事务模式">{detail.execution.transactionMode || '-'}</Descriptions.Item>
                <Descriptions.Item label="开始">{fmt(detail.execution.startedAt)}</Descriptions.Item>
                <Descriptions.Item label="结束">{fmt(detail.execution.finishedAt)}</Descriptions.Item>
                <Descriptions.Item label="耗时">{detail.execution.durationMs} ms</Descriptions.Item>
                <Descriptions.Item label="语句成功/总数">
                  {detail.execution.succeededStatementCount}/{detail.execution.statementCount}
                </Descriptions.Item>
                {detail.execution.errorMessage ? (
                  <Descriptions.Item label="错误" span={2}>
                    <span className="text-[#d92d20]">{detail.execution.errorMessage}</span>
                  </Descriptions.Item>
                ) : null}
              </Descriptions>
              <Table<SqlStatementAuditRecord>
                rowKey="statementId"
                size="small"
                className="mt-4"
                columns={statementColumns}
                dataSource={detail.statements}
                pagination={false}
              />
            </>
          ) : null}
        </Spin>
      </Drawer>
    </div>
  );
};

export default SqlExecutionAuditPage;
