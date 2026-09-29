import { Button, Input, InputNumber, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import React from 'react';
import { agentAuditApi } from '@/services/agent';
import type { QueryAudit } from '../types';
import { useAgentStyles } from './pageStyles';

const statusTag = (status: string) => {
  const map: Record<string, { color: string; text: string }> = {
    SUCCESS: { color: 'green', text: '成功' },
    FAILED: { color: 'red', text: '失败' },
    REJECTED: { color: 'orange', text: '已拒绝' },
  };
  const conf = map[status] ?? { color: 'default', text: status };
  return <Tag color={conf.color}>{conf.text}</Tag>;
};

const formatElapsed = (value?: number | null) => {
  if (value === null || value === undefined) {
    return '-';
  }
  return value >= 1000 ? `${(value / 1000).toFixed(2)}s` : `${value}ms`;
};

const columns: ColumnsType<QueryAudit> = [
  {
    title: '时间',
    dataIndex: 'createTime',
    width: 170,
    render: (value?: string) => value?.replace('T', ' ').slice(0, 23) ?? '-',
  },
  {
    title: '会话',
    dataIndex: 'sessionId',
    width: 150,
    ellipsis: true,
    render: (value: string) => <Typography.Text copyable={{ text: value }}>{value}</Typography.Text>,
  },
  { title: '数据集ID', dataIndex: 'datasetId', width: 90 },
  {
    title: '状态',
    dataIndex: 'status',
    width: 90,
    render: (status: string) => statusTag(status),
  },
  {
    title: '行数',
    dataIndex: 'returnedRows',
    width: 90,
    render: (value?: number, row?: QueryAudit) =>
      value === null || value === undefined ? '-' : `${value}${row?.truncated ? ' (截断)' : ''}`,
  },
  {
    title: '耗时',
    dataIndex: 'elapsedMillis',
    width: 100,
    render: (value?: number) => formatElapsed(value),
  },
  {
    title: 'queryId',
    dataIndex: 'queryId',
    width: 140,
    ellipsis: true,
    render: (value?: string) => value ?? '-',
  },
  {
    title: '错误信息',
    dataIndex: 'errorMessage',
    ellipsis: true,
    render: (value?: string) =>
      value ? (
        <Typography.Text type="danger" ellipsis={{ tooltip: value }}>
          {value}
        </Typography.Text>
      ) : (
        '-'
      ),
  },
];

const AuditTable: React.FC = () => {
  const { styles } = useAgentStyles();
  const [rows, setRows] = React.useState<QueryAudit[]>([]);
  const [total, setTotal] = React.useState(0);
  const [pageNo, setPageNo] = React.useState(1);
  const [pageSize, setPageSize] = React.useState(10);
  const [loading, setLoading] = React.useState(false);
  const [filterSessionId, setFilterSessionId] = React.useState('');
  const [filterDatasetId, setFilterDatasetId] = React.useState<number | null>(null);

  const load = React.useCallback(
    async (page: number, size: number) => {
      setLoading(true);
      try {
        const data = await agentAuditApi.page({
          pageNo: page,
          pageSize: size,
          sessionId: filterSessionId || undefined,
          datasetId: filterDatasetId ?? undefined,
        });
        setRows(data.bizData ?? []);
        setTotal(data.pagination?.total ?? 0);
      } finally {
        setLoading(false);
      }
    },
    [filterSessionId, filterDatasetId],
  );

  React.useEffect(() => {
    load(pageNo, pageSize);
  }, [pageNo, pageSize, load]);

  return (
    <>
      <div className={styles.filterRow}>
        <Input
          allowClear
          placeholder="按会话ID过滤"
          style={{ width: 240 }}
          value={filterSessionId}
          onChange={(event) => setFilterSessionId(event.target.value)}
          onPressEnter={() => {
            setPageNo(1);
            load(1, pageSize);
          }}
        />
        <InputNumber
          placeholder="数据集ID"
          style={{ width: 140 }}
          min={1}
          value={filterDatasetId}
          onChange={(value) => setFilterDatasetId(value)}
        />
        <Button
          type="primary"
          onClick={() => {
            setPageNo(1);
            load(1, pageSize);
          }}
        >
          查询
        </Button>
      </div>
      <Table<QueryAudit>
        size="small"
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 960 }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          onChange: (page, size) => {
            setPageNo(page);
            setPageSize(size);
          },
        }}
      />
    </>
  );
};

export default AuditTable;
