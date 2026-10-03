import { useNavigate, useSearchParams } from '@umijs/max';
import { Button, Modal, Select, Space, Table, Tag, Tooltip, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';

import { YakEmpty } from '@/components/ui';
import { pageMdmChanges, withdrawMdmChange } from '@/services/mdm/api';
import type { MdmApprovalStatus, MdmChangeRecord } from '@/services/mdm/types';

const PAGE_SIZE = 10;

const fmt = (value?: string | null) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';

const CHANGE_STATUS_META: Record<MdmApprovalStatus, { label: string; color: string }> = {
  PENDING: { label: '审批中', color: 'processing' },
  APPROVED: { label: '已生效', color: 'success' },
  REJECTED: { label: '已拒绝', color: 'error' },
  WITHDRAWN: { label: '已撤回', color: 'default' },
};

const CHANGE_TYPE_LABELS: Record<string, string> = {
  CREATE: '新增',
  UPDATE: '修改',
  DELETE: '删除',
  MERGE: '合并',
};

const STATUS_OPTIONS = (Object.keys(CHANGE_STATUS_META) as MdmApprovalStatus[]).map(
  (value) => ({ value, label: CHANGE_STATUS_META[value].label }),
);

/** 主数据审批列表(R4):变更单真相在 MDM,推进/决策在审批中心待办完成。 */
const MdmApprovalPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [rows, setRows] = useState<MdmChangeRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [status, setStatus] = useState<MdmApprovalStatus | undefined>(() => {
    // 总览的「待审批变更」以 ?status=PENDING 直达：入口已表明意图，不该再让人点一次筛选。
    const param = searchParams.get('status') as MdmApprovalStatus | null;
    return param && param in CHANGE_STATUS_META ? param : undefined;
  });
  const [loading, setLoading] = useState(false);

  const load = useCallback(async (page: number, statusFilter?: MdmApprovalStatus) => {
    setLoading(true);
    try {
      const result = await pageMdmChanges({
        pageNo: page,
        pageSize: PAGE_SIZE,
        status: statusFilter ?? '',
      });
      setRows(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch (error: any) {
      message.error(error?.message ?? '变更申请加载失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(pageNo, status);
  }, [load, pageNo, status]);

  const withdraw = (row: MdmChangeRecord) => {
    Modal.confirm({
      title: `撤回变更申请 #${row.id}`,
      content: `将同时撤销审批中心在途单据；仅发起人可撤回，撤回后申请不再阻塞后续变更。`,
      okText: '确认撤回',
      cancelText: '返回',
      onOk: async () => {
        try {
          await withdrawMdmChange(row.id);
          message.success('已撤回');
          void load(pageNo, status);
        } catch (error: any) {
          message.error(error?.message ?? '撤回失败');
        }
      },
    });
  };

  const columns: ColumnsType<MdmChangeRecord> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '实体',
      dataIndex: 'entityName',
      width: 150,
      ellipsis: true,
      // 实体名由列表接口补齐;实体已删除时回退 #id,避免整列显示无意义的内部编号。
      render: (_, row) =>
        row.entityName ? (
          <Tooltip title={row.entityCode ? `${row.entityName}（${row.entityCode}）` : row.entityName}>
            <span>{row.entityName}</span>
          </Tooltip>
        ) : (
          <Tooltip title="实体已删除或不可见">
            <span className="text-[#667085]">#{row.entityId}</span>
          </Tooltip>
        ),
    },
    {
      title: 'master_id',
      dataIndex: 'masterId',
      width: 230,
      // 不用列级 ellipsis:它会连复制图标一起裁掉,导致图标看着像不存在。
      render: (value: string) => (
        <div className="flex min-w-0 items-center gap-1">
          <Tooltip title={value}>
            <span className="min-w-0 flex-1 truncate font-mono text-[12px]">{value}</span>
          </Tooltip>
          <Typography.Text
            copyable={{ text: value, tooltips: ['复制 master_id', '已复制'] }}
            className="shrink-0 !text-[12px]"
          />
        </div>
      ),
    },
    {
      title: '类型',
      dataIndex: 'changeType',
      width: 80,
      render: (value: string) => CHANGE_TYPE_LABELS[value] ?? value,
    },
    { title: '变更内容', dataIndex: 'changeContent', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'approvalStatus',
      width: 90,
      render: (value: MdmApprovalStatus) => {
        const meta = CHANGE_STATUS_META[value];
        return <Tag color={meta?.color}>{meta?.label ?? value}</Tag>;
      },
    },
    { title: '申请人', dataIndex: 'applicant', width: 100 },
    { title: '审批人', dataIndex: 'approver', width: 100, render: (value) => value || '-' },
    { title: '审批意见', dataIndex: 'approvalComment', width: 140, ellipsis: true, render: (value) => value || '-' },
    { title: '提交时间', width: 150, render: (_, row) => fmt(row.createTime) },
    {
      title: '操作',
      width: 150,
      render: (_, row) => (
        <Space size={0}>
          {row.instanceId ? (
            <Button
              type="link"
              size="small"
              onClick={() => navigate(`/approval/instance/${row.instanceId}`)}
            >
              审批单
            </Button>
          ) : null}
          {row.approvalStatus === 'PENDING' ? (
            <Button type="link" size="small" danger onClick={() => withdraw(row)}>
              撤回
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">主数据变更</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            变更申请在这里查进度、撤回在途申请；通过与拒绝请前往审批中心「待办中心」处理
          </div>
        </div>
        <Select
          allowClear
          placeholder="状态筛选"
          className="!w-40"
          options={STATUS_OPTIONS}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setPageNo(1);
          }}
        />
      </div>
      <Table
        className="mt-3"
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        locale={{ emptyText: <YakEmpty description="暂无变更申请，可在实体详情「记录」页对记录发起变更" /> }}
        pagination={{
          current: pageNo,
          pageSize: PAGE_SIZE,
          total,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page) => setPageNo(page),
        }}
      />
    </div>
  );
};

export default MdmApprovalPage;
