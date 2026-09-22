import { useNavigate } from '@umijs/max';
import { Modal, Select, Space, Table, Tag, Tooltip, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState } from 'react';

import { YakEmpty } from '@/components/ui';
import {
  listMdmAttributes,
  listMdmChangeHistory,
  listMdmRecordVersions,
  pageMdmRecords,
} from '@/services/mdm/api';
import type {
  MdmApprovalStatus,
  MdmAttributeRecord,
  MdmChangeRecord,
  MdmRecord,
  MdmRecordVersionRecord,
} from '@/services/mdm/types';

const fmt = (value?: string | null) =>
  value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';

const parseJson = (raw?: string | null): Record<string, unknown> => {
  if (!raw) return {};
  try {
    const value = JSON.parse(raw);
    return value && typeof value === 'object' && !Array.isArray(value)
      ? (value as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
};

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

const DIFF_KIND_META: Record<string, { label: string; color: string }> = {
  新增: { label: '新增', color: 'green' },
  修改: { label: '修改', color: 'orange' },
  删除: { label: '删除', color: 'red' },
};

interface DiffRow {
  key: string;
  before: string;
  after: string;
  kind: string;
}

const display = (value: unknown) =>
  value === undefined ? '' : value === null ? 'null' : String(value);

/** v(n-1) → v(n) 属性级 diff：只列变化行。 */
const buildDiff = (before: Record<string, unknown>, after: Record<string, unknown>): DiffRow[] => {
  const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])];
  const rows: DiffRow[] = [];
  keys.forEach((key) => {
    const hasBefore = key in before;
    const hasAfter = key in after;
    const kind = !hasBefore ? '新增' : !hasAfter ? '删除' : display(before[key]) !== display(after[key]) ? '修改' : null;
    if (kind) {
      rows.push({ key, before: display(before[key]), after: display(after[key]), kind });
    }
  });
  return rows;
};

/**
 * 实体详情「变更记录」Tab(R4)：按记录查看版本快照序列与变更申请流水。
 * 快照为审批生效时落的全量属性(yak_mdm_record_version)，支持 v(n-1)/v(n) diff。
 */
const ChangeHistoryTab = ({ entityId }: { entityId: number }) => {
  const navigate = useNavigate();
  const [records, setRecords] = useState<MdmRecord[]>([]);
  const [attributes, setAttributes] = useState<MdmAttributeRecord[]>([]);
  const [masterId, setMasterId] = useState<string | undefined>();
  const [versions, setVersions] = useState<MdmRecordVersionRecord[]>([]);
  const [changes, setChanges] = useState<MdmChangeRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [diffPair, setDiffPair] = useState<{ current: MdmRecordVersionRecord; previous?: MdmRecordVersionRecord } | null>(null);

  useEffect(() => {
    pageMdmRecords(entityId, { pageNo: 1, pageSize: 200, status: 'ACTIVE' })
      .then((result) => setRecords(result.bizData ?? []))
      .catch(() => setRecords([]));
    listMdmAttributes(entityId)
      .then((value) => setAttributes(value ?? []))
      .catch(() => setAttributes([]));
  }, [entityId]);

  /** 下拉优先展示第一个非 PK 属性值(如客户姓名)，master_id 是哈希串不利可读。 */
  const displayAttrCode = useMemo(() => {
    const first = attributes.find((attribute) => attribute.type !== 'PK');
    return first?.code;
  }, [attributes]);

  const options = useMemo(
    () =>
      records.map((record) => {
        const readable = displayAttrCode
          ? display(parseJson(record.attributes)[displayAttrCode])
          : '';
        return {
          value: record.masterId,
          label: readable
            ? `${readable} · ${record.masterId.slice(0, 8)}…（v${record.version}）`
            : `${record.masterId.slice(0, 12)}…（v${record.version}）`,
        };
      }),
    [records, displayAttrCode],
  );

  const loadHistory = useCallback(async (id: string) => {
    setLoading(true);
    try {
      const [versionRows, changeRows] = await Promise.all([
        listMdmRecordVersions(entityId, id),
        listMdmChangeHistory(entityId, id),
      ]);
      setVersions(versionRows ?? []);
      setChanges(changeRows ?? []);
    } catch (error: any) {
      message.error(error?.message ?? '变更记录加载失败');
      setVersions([]);
      setChanges([]);
    } finally {
      setLoading(false);
    }
  }, [entityId]);

  useEffect(() => {
    if (masterId) void loadHistory(masterId);
  }, [masterId, loadHistory]);

  const versionByNumber = useMemo(
    () => new Map(versions.map((version) => [version.version, version])),
    [versions],
  );

  const versionColumns: ColumnsType<MdmRecordVersionRecord> = [
    {
      title: '版本',
      dataIndex: 'version',
      width: 80,
      render: (value: number) => <Tag color="blue">v{value}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ACTIVE' ? 'green' : value === 'DELETED' ? 'red' : 'gold'}>
          {value === 'ACTIVE' ? '生效' : value === 'DELETED' ? '已删除' : value}
        </Tag>
      ),
    },
    {
      title: '来源',
      dataIndex: 'changeId',
      width: 120,
      render: (value?: number) => (value ? <Tag color="purple">变更单 #{value}</Tag> : <Tag>基线快照</Tag>),
    },
    { title: '操作人', dataIndex: 'operator', width: 110, render: (value) => value || '-' },
    { title: '时间', width: 150, render: (_, row) => fmt(row.createTime) },
    {
      title: '属性快照',
      dataIndex: 'attributes',
      ellipsis: true,
      render: (value: string) => (
        <Tooltip title={value}>
          <span className="text-[12px] text-[#667085]">{value}</span>
        </Tooltip>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      render: (_, row) => {
        const previous = versionByNumber.get(row.version - 1);
        return previous ? (
          <Space size={0}>
            <button
              type="button"
              className="cursor-pointer border-0 bg-transparent p-0 text-[13px] text-[#1677ff]"
              onClick={() => setDiffPair({ current: row, previous })}
            >
              对比 v{row.version - 1}
            </button>
          </Space>
        ) : null;
      },
    },
  ];

  const changeColumns: ColumnsType<MdmChangeRecord> = [
    { title: 'ID', dataIndex: 'id', width: 70 },
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
    { title: '提交时间', width: 150, render: (_, row) => fmt(row.createTime) },
    {
      title: '',
      key: 'instance',
      width: 90,
      render: (_, row) =>
        row.instanceId ? (
          <button
            type="button"
            className="cursor-pointer border-0 bg-transparent p-0 text-[13px] text-[#1677ff]"
            onClick={() => navigate(`/approval/instance/${row.instanceId}`)}
          >
            审批单
          </button>
        ) : null,
    },
  ];

  const diffRows = diffPair
    ? buildDiff(parseJson(diffPair.previous?.attributes), parseJson(diffPair.current.attributes))
    : [];

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <span className="text-[13px] text-[#667085]">选择记录</span>
        <Select
          showSearch
          allowClear
          className="!w-[420px]"
          placeholder={records.length ? '按属性值/master_id 搜索记录' : '暂无生效记录'}
          options={options}
          value={masterId}
          optionFilterProp="label"
          onChange={(value?: string) => setMasterId(value)}
        />
        {masterId && records.length ? (
          <span className="text-[12px] text-[#98a2b3]">
            共 {records.length} 条生效记录（下拉取前 200 条）
          </span>
        ) : null}
      </div>

      {!masterId ? (
        <YakEmpty
          compact
          title="请选择记录"
          description="选择后展示该记录的全量版本快照（含 v(n-1)/v(n) diff）与变更申请流水"
        />
      ) : (
        <Space direction="vertical" size={16} className="w-full">
          <div>
            <div className="mb-2 text-[14px] font-semibold">版本快照</div>
            <Table<MdmRecordVersionRecord>
              rowKey="id"
              size="small"
              loading={loading}
              columns={versionColumns}
              dataSource={[...versions].sort((a, b) => b.version - a.version)}
              pagination={false}
              locale={{
                emptyText: (
                  <YakEmpty
                    compact
                    title="暂无版本快照"
                    description="首次变更审批通过后生成基线 + 新版快照；纯加工写入的记录不产生快照"
                  />
                ),
              }}
            />
          </div>
          <div>
            <div className="mb-2 text-[14px] font-semibold">变更申请流水</div>
            <Table<MdmChangeRecord>
              rowKey="id"
              size="small"
              loading={loading}
              columns={changeColumns}
              dataSource={changes}
              pagination={false}
              locale={{ emptyText: <YakEmpty compact title="暂无变更申请" description="在「记录」Tab 点「变更申请」发起" /> }}
            />
          </div>
        </Space>
      )}

      <Modal
        title={
          diffPair
            ? `版本对比：v${diffPair.previous?.version ?? '-'} → v${diffPair.current.version}`
            : '版本对比'
        }
        open={!!diffPair}
        onCancel={() => setDiffPair(null)}
        footer={null}
        width={680}
      >
        <Table<DiffRow>
          rowKey="key"
          size="small"
          columns={[
            { title: '属性', dataIndex: 'key' },
            {
              title: `变更前${diffPair?.previous ? `（v${diffPair.previous.version}）` : '（无快照）'}`,
              dataIndex: 'before',
              render: (value: string) => value || <span className="text-[#98a2b3]">-</span>,
            },
            {
              title: `变更后（v${diffPair?.current.version ?? ''}）`,
              dataIndex: 'after',
              render: (value: string) => value || <span className="text-[#98a2b3]">-</span>,
            },
            {
              title: '变化',
              dataIndex: 'kind',
              width: 80,
              render: (value: string) => (
                <Tag color={DIFF_KIND_META[value]?.color}>{DIFF_KIND_META[value]?.label ?? value}</Tag>
              ),
            },
          ]}
          dataSource={diffRows}
          pagination={false}
          locale={{ emptyText: <YakEmpty compact title="两版本属性一致" /> }}
        />
      </Modal>
    </div>
  );
};

export default ChangeHistoryTab;
