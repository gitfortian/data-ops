import { useModel, useNavigate } from '@umijs/max';
import { Badge, Button, Input, Modal, Select, Space, Table, Tabs, Tag, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';

import { ApprovalStatusTag, STEP_STATUS_META } from '@/components/ApprovalStatusTag';
import { YakEmpty } from '@/components/ui';
import { cancelInstance, getTodoCount, pageHandled, pageMine, pageTodo } from '@/services/approval/api';
import type {
  ApprovalHandledRow,
  ApprovalInstance,
  ApprovalTodoRow,
} from '@/services/approval/types';

const fmt = (value?: string | null) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';

const PAGE_SIZE = 10;


/** 我的待办:step PENDING 即轮到本人,行内直达审批详情。 */
const TodoTab = ({ onCountChange }: { onCountChange: (count: number) => void }) => {
  const navigate = useNavigate();
  const [rows, setRows] = useState<ApprovalTodoRow[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (page: number) => {
    setLoading(true);
    try {
      const result = await pageTodo(page, PAGE_SIZE);
      setRows(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
      onCountChange(result.pagination?.total ?? 0);
    } catch (error: any) {
      message.error(error?.message ?? '待办加载失败');
    } finally {
      setLoading(false);
    }
  }, [onCountChange]);

  useEffect(() => {
    void load(pageNo);
  }, [load, pageNo]);

  const columns: ColumnsType<ApprovalTodoRow> = [
    { title: '审批标题', dataIndex: ['instance', 'title'], ellipsis: true },
    { title: '流程', dataIndex: ['instance', 'flowName'], width: 140 },
    { title: '业务对象', width: 150, render: (_, row) => `${row.instance.bizType} / ${row.instance.bizId}` },
    { title: '发起人', dataIndex: ['instance', 'applicant'], width: 110 },
    { title: '级次', dataIndex: 'levelNo', width: 80, render: (level: number) => `第 ${level} 级` },
    { title: '发起时间', width: 160, render: (_, row) => fmt(row.instance.createTime) },
    {
      title: '操作',
      width: 90,
      render: (_, row) => (
        <Button type="link" size="small" onClick={() => navigate(`/approval/instance/${row.instance.id}`)}>
          审批
        </Button>
      ),
    },
  ];

  return (
    <Table
      rowKey="stepId"
      size="middle"
      loading={loading}
      columns={columns}
      dataSource={rows}
      locale={{ emptyText: <YakEmpty description="暂无待办，去喝杯咖啡吧" /> }}
      pagination={{
        current: pageNo,
        pageSize: PAGE_SIZE,
        total,
        showTotal: (count) => `共 ${count} 条`,
        onChange: (page) => setPageNo(page),
      }}
    />
  );
};

const MINE_STATUS_OPTIONS = [
  { value: 'PENDING', label: '审批中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'CANCELED', label: '已撤销' },
];

/** 我发起的:可撤销在途单(仅发起人,后端 49005 兜底)。 */
const MineTab = () => {
  const navigate = useNavigate();
  const [rows, setRows] = useState<ApprovalInstance[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (page: number, statusFilter?: string) => {
    setLoading(true);
    try {
      const result = await pageMine(page, PAGE_SIZE, statusFilter);
      setRows(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch (error: any) {
      message.error(error?.message ?? '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(pageNo, status);
  }, [load, pageNo, status]);

  const cancel = (instance: ApprovalInstance) => {
    let reason = '';
    Modal.confirm({
      title: `撤销「${instance.title}」`,
      content: (
        <Input.TextArea
          rows={3}
          placeholder="撤销原因（可选，会记入审批记录）"
          onChange={(event) => {
            reason = event.target.value;
          }}
        />
      ),
      okText: '确认撤销',
      cancelText: '返回',
      onOk: async () => {
        try {
          await cancelInstance(instance.id, reason || undefined);
          message.success('已撤销');
          void load(pageNo, status);
        } catch (error: any) {
          message.error(error?.message ?? '撤销失败');
        }
      },
    });
  };

  const columns: ColumnsType<ApprovalInstance> = [
    { title: '审批标题', dataIndex: 'title', ellipsis: true },
    { title: '流程', dataIndex: 'flowName', width: 140 },
    { title: '状态', dataIndex: 'status', width: 100, render: (value) => <ApprovalStatusTag status={value} /> },
    {
      title: '当前级次',
      width: 100,
      render: (_, row) => (row.status === 'PENDING' ? `第 ${row.currentLevel} 级` : '-'),
    },
    { title: '发起时间', width: 160, render: (_, row) => fmt(row.createTime) },
    { title: '结束时间', width: 160, render: (_, row) => fmt(row.finishTime) },
    {
      title: '操作',
      width: 130,
      render: (_, row) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => navigate(`/approval/instance/${row.id}`)}>
            查看
          </Button>
          {row.status === 'PENDING' ? (
            <Button type="link" size="small" danger onClick={() => cancel(row)}>
              撤销
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="mb-3 flex justify-end">
        <Select
          allowClear
          placeholder="状态筛选"
          className="!w-40"
          options={MINE_STATUS_OPTIONS}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setPageNo(1);
          }}
        />
      </div>
      <Table
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        locale={{ emptyText: <YakEmpty description="还没有发起过审批" /> }}
        pagination={{
          current: pageNo,
          pageSize: PAGE_SIZE,
          total,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page) => setPageNo(page),
        }}
      />
    </>
  );
};

/** 我审批过的:step 终态行 + 单据最新状态。 */
const HandledTab = () => {
  const navigate = useNavigate();
  const [rows, setRows] = useState<ApprovalHandledRow[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (page: number) => {
    setLoading(true);
    try {
      const result = await pageHandled(page, PAGE_SIZE);
      setRows(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch (error: any) {
      message.error(error?.message ?? '加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(pageNo);
  }, [load, pageNo]);

  const columns: ColumnsType<ApprovalHandledRow> = [
    { title: '审批标题', dataIndex: ['instance', 'title'], ellipsis: true },
    { title: '流程', dataIndex: ['instance', 'flowName'], width: 140 },
    {
      title: '我的处理',
      dataIndex: 'stepStatus',
      width: 110,
      render: (value: keyof typeof STEP_STATUS_META) => {
        const meta = STEP_STATUS_META[value];
        return <Tag color={meta?.color}>{meta?.label ?? value}</Tag>;
      },
    },
    { title: '审批意见', dataIndex: 'comment', ellipsis: true, render: (value) => value || '-' },
    { title: '处理时间', width: 160, render: (_, row) => fmt(row.handledTime) },
    {
      title: '单据状态',
      width: 100,
      render: (_, row) => <ApprovalStatusTag status={row.instance.status} />,
    },
    {
      title: '操作',
      width: 90,
      render: (_, row) => (
        <Button type="link" size="small" onClick={() => navigate(`/approval/instance/${row.instance.id}`)}>
          查看
        </Button>
      ),
    },
  ];

  return (
    <Table
      rowKey="stepId"
      size="middle"
      loading={loading}
      columns={columns}
      dataSource={rows}
      locale={{ emptyText: <YakEmpty description="还没有审批过的单据" /> }}
      pagination={{
        current: pageNo,
        pageSize: PAGE_SIZE,
        total,
        showTotal: (count) => `共 ${count} 条`,
        onChange: (page) => setPageNo(page),
      }}
    />
  );
};

const TodoCenterPage = () => {
  const { initialState } = useModel('@@initialState');
  const canRead = (initialState?.currentUser?.permissionCodes ?? []).some(
    (code) => code === 'data-approval:read' || code === 'security:root',
  );
  const [todoCount, setTodoCount] = useState(0);

  useEffect(() => {
    if (!canRead) return;
    getTodoCount()
      .then((count) => setTodoCount(count ?? 0))
      .catch(() => undefined);
  }, [canRead]);

  if (!canRead) {
    return <YakEmpty description="无权限：需要 data-approval:read 权限或审批中心菜单授权" />;
  }

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">我的待办</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            我的待办、我发起的与我审批过的审批单
          </div>
        </div>
      </div>
      <Tabs
        className="mt-3"
        items={[
          {
            key: 'todo',
            label: (
              <Badge count={todoCount} size="small" offset={[8, -2]}>
                <span>我的待办</span>
              </Badge>
            ),
            children: <TodoTab onCountChange={setTodoCount} />,
          },
          { key: 'mine', label: '我发起的', children: <MineTab /> },
          { key: 'handled', label: '我审批过的', children: <HandledTab /> },
        ]}
      />
    </div>
  );
};

export default TodoCenterPage;
