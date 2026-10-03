import { history } from '@umijs/max';
import { Button, Form, Input, Modal, message, Select, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  generateMdmMasterSql,
  listMdmAttributes,
  listMdmSources,
  pageMdmRecords,
  registerMdmProcessingTask,
  submitMdmChange,
} from '@/services/mdm/api';
import type {
  MdmAttributeRecord,
  MdmRecord,
  MdmRecordStatus,
  MdmSourceRecord,
} from '@/services/mdm/types';

const STATUS_LABELS: Record<MdmRecordStatus, string> = {
  ACTIVE: '生效',
  MERGED: '已合并',
  DELETED: '已删除',
};

const STATUS_COLORS: Record<MdmRecordStatus, string> = {
  ACTIVE: 'green',
  MERGED: 'gold',
  DELETED: 'red',
};

const STATUS_OPTIONS = [
  { label: '全部', value: '' as const },
  { label: '生效', value: 'ACTIVE' },
  { label: '已合并', value: 'MERGED' },
  { label: '已删除', value: 'DELETED' },
];

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

/**
 * 实体详情"记录"Tab(R2):统一主数据表 yak_mdm_record 只读查询。
 * 「生成主数据加工任务」经数据开发注册 SQL 节点草稿并跳转(替代裸复制 SQL,P2-6);
 * 记录搜索匹配 master_id 与属性值(P0-1.4),表格按实体属性动态出列。
 */
const RecordTab = ({ entityId }: { entityId: number }) => {
  const [records, setRecords] = useState<MdmRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<MdmRecordStatus | ''>('');
  const [loading, setLoading] = useState(false);
  const [attributes, setAttributes] = useState<MdmAttributeRecord[]>([]);
  const [sources, setSources] = useState<MdmSourceRecord[]>([]);
  const [registering, setRegistering] = useState(false);
  const [sqlOpen, setSqlOpen] = useState(false);
  const [sqlText, setSqlText] = useState('');
  const [sqlLoading, setSqlLoading] = useState(false);
  const [changeTarget, setChangeTarget] = useState<MdmRecord | null>(null);
  const [changeSubmitting, setChangeSubmitting] = useState(false);
  const [changeForm] = Form.useForm();

  const loadRecords = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageMdmRecords(entityId, {
        pageNo,
        pageSize,
        keyword: keyword.trim() || undefined,
        status,
      });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [entityId, pageNo, pageSize, keyword, status]);

  useEffect(() => {
    void loadRecords();
  }, [loadRecords]);

  useEffect(() => {
    listMdmAttributes(entityId)
      .then((value) => setAttributes(value ?? []))
      .catch(() => setAttributes([]));
    listMdmSources(entityId)
      .then((value) => setSources(value ?? []))
      .catch(() => setSources([]));
  }, [entityId]);

  const datasourceNames = useMemo(() => {
    const map = new Map<number, string>();
    sources.forEach((source) => map.set(source.datasourceId, source.datasourceName));
    return map;
  }, [sources]);

  /** PK 是 master_id 哈希原料，后端拒绝改 PK(R4)，弹窗只出非 PK 属性。 */
  const editableAttributes = useMemo(
    () => attributes.filter((attribute) => attribute.type !== 'PK'),
    [attributes],
  );

  const openChange = (record: MdmRecord) => {
    const current = parseJson(record.attributes);
    changeForm.resetFields();
    editableAttributes.forEach((attribute) => {
      const value = current[attribute.code];
      changeForm.setFieldValue(
        attribute.code,
        value === undefined || value === null ? '' : String(value),
      );
    });
    setChangeTarget(record);
  };

  const submitChange = async () => {
    if (!changeTarget) return;
    const values = await changeForm.validateFields();
    const current = parseJson(changeTarget.attributes);
    const patch: Record<string, unknown> = {};
    editableAttributes.forEach((attribute) => {
      const next = (values[attribute.code] ?? '').trim();
      const previous =
        current[attribute.code] === undefined || current[attribute.code] === null
          ? ''
          : String(current[attribute.code]);
      if (next !== previous) patch[attribute.code] = next;
    });
    if (!Object.keys(patch).length) {
      message.info('未检测到属性变化，无需提交申请');
      return;
    }
    setChangeSubmitting(true);
    try {
      await submitMdmChange({
        entityId,
        masterId: changeTarget.masterId,
        changeType: 'UPDATE',
        changeContent: JSON.stringify(patch),
      });
      message.success('变更申请已提交，等待审批中心处理');
      setChangeTarget(null);
      void loadRecords();
    } catch {
      // 阻断原因(在途变更/记录状态)由全局错误通知透出
    } finally {
      setChangeSubmitting(false);
    }
  };

  const restoreSourceValue = (record: MdmRecord, attributeCode: string) => {
    Modal.confirm({
      title: '恢复来源值',
      content: `提交审批后，${attributeCode} 将在下一次主数据加工时恢复为来源数据。`,
      okText: '提交审批',
      cancelText: '取消',
      onOk: async () => {
        try {
          await submitMdmChange({
            entityId,
            masterId: record.masterId,
            changeType: 'UPDATE',
            changeContent: JSON.stringify({ $useSource: [attributeCode] }),
          });
          message.success('来源恢复申请已提交');
          void loadRecords();
        } catch {
          // The shared request layer displays the approval or concurrency failure.
        }
      },
    });
  };

  const registerTask = async () => {
    setRegistering(true);
    try {
      const receipt = await registerMdmProcessingTask(entityId);
      message.success(
        receipt.nodeCreated
          ? `已创建加工任务「${receipt.taskName}」，跳转数据开发编辑`
          : `已更新既有加工任务草稿「${receipt.taskName}」`,
      );
      history.push(`/data-development/task/${receipt.nodeId}`);
    } catch {
      // 后端阻断原因(如来源未落地/数据开发未启用)由全局错误通知透出
    } finally {
      setRegistering(false);
    }
  };

  const openSql = async () => {
    setSqlOpen(true);
    setSqlLoading(true);
    try {
      setSqlText(await generateMdmMasterSql(entityId));
    } catch {
      setSqlText('');
    } finally {
      setSqlLoading(false);
    }
  };

  const copySql = async () => {
    try {
      await navigator.clipboard.writeText(sqlText);
      message.success('SQL 已复制');
    } catch {
      message.error('复制失败，请手动选择复制');
    }
  };

  const attrColumn = (attribute: MdmAttributeRecord): ColumnsType<MdmRecord>[number] => ({
    title: attribute.name || attribute.code,
    key: `attr-${attribute.code}`,
    width: attribute.type === 'PK' ? 160 : 130,
    ellipsis: true,
    render: (_: unknown, record: MdmRecord) => {
      const value = parseJson(record.attributes)[attribute.code];
      const text = value === undefined || value === null ? '-' : String(value);
      const overridden = Object.prototype.hasOwnProperty.call(
        parseJson(record.attributeOverrides),
        attribute.code,
      );
      return (
        <Tooltip title={text}>
          <span>
            {attribute.type === 'PK' && text !== '-' && <Tag className="!mr-1">PK</Tag>}
            {overridden && <Tag color="gold" className="!mr-1">人工修正</Tag>}
            {text}
          </span>
        </Tooltip>
      );
    },
  });

  const columns: ColumnsType<MdmRecord> = [
    {
      title: 'master_id',
      dataIndex: 'masterId',
      width: 240,
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
    ...attributes.map(attrColumn),
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: MdmRecordStatus) => (
        <Tag color={STATUS_COLORS[value]}>{STATUS_LABELS[value]}</Tag>
      ),
    },
    { title: '版本', dataIndex: 'version', width: 70 },
    {
      title: '来源系统',
      dataIndex: 'sourceIds',
      width: 220,
      ellipsis: true,
      render: (value: string) => {
        const entries = Object.entries(parseJson(value));
        if (!entries.length) return '-';
        const text = entries
          .map(
            ([datasourceId, raw]) =>
              `${datasourceNames.get(Number(datasourceId)) ?? `数据源#${datasourceId}`}: ${String(raw)}`,
          )
          .join('； ');
        return (
          <Tooltip title={text}>
            <span>{text}</span>
          </Tooltip>
        );
      },
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 110,
      fixed: 'right',
      render: (_: unknown, record: MdmRecord) => {
        if (record.status !== 'ACTIVE') return null;
        const overrides = parseJson(record.attributeOverrides);
        const resettable = editableAttributes.filter((attribute) =>
          Object.prototype.hasOwnProperty.call(overrides, attribute.code),
        );
        return (
          <Space size={0} wrap>
            {editableAttributes.length > 0 && (
              <Button type="link" size="small" onClick={() => openChange(record)}>
                变更申请
              </Button>
            )}
            {resettable.map((attribute) => (
              <Button
                key={attribute.code}
                type="link"
                size="small"
                onClick={() => restoreSourceValue(record, attribute.code)}
              >
                恢复来源
              </Button>
            ))}
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <div className="mb-3 rounded-lg bg-[#f6f7f8] px-3 py-2 text-[13px] text-[#667085]">
        统一主数据表（yak_mdm_record）由「主数据加工任务」写入（数据开发对平台库执行落地表
        UPSERT）；审批/清洗产生的「人工修正」字段会在后续加工中保留，可逐字段申请恢复来源值。点击「生成主数据加工任务」自动注册数据开发 SQL
        草稿（数据源已预填为落地目标库），在数据开发中运行后回到本页检索记录。
      </div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <Input.Search
            allowClear
            className="!w-64"
            placeholder="搜索属性值（如名称）/ master_id"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onSearch={() => {
              setPageNo(1);
              void loadRecords();
            }}
          />
          <Select className="!w-28" options={STATUS_OPTIONS} value={status} onChange={setStatus} />
        </div>
        <div className="flex items-center gap-2">
          <YakButton className="!h-8 !rounded-lg !px-3" onClick={() => void openSql()}>
            查看加工 SQL
          </YakButton>
          <YakButton
            type="primary"
            loading={registering}
            className="!h-8 !rounded-lg !px-3 !text-white"
            onClick={() => void registerTask()}
          >
            生成主数据加工任务
          </YakButton>
        </div>
      </div>
      <Table<MdmRecord>
        rowKey="id"
        columns={columns}
        dataSource={records}
        loading={loading}
        size="middle"
        scroll={{ x: 'max-content' }}
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title="暂无主数据记录"
              description="点击「生成主数据加工任务」注册数据开发草稿，运行任务后记录将写入统一主数据表"
            />
          ),
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

      <Modal
        title="主数据加工 SQL（读平台库落地表 → 统一主数据表）"
        open={sqlOpen}
        onCancel={() => setSqlOpen(false)}
        width={760}
        footer={
          <div className="flex justify-end gap-2">
            <YakButton className="!h-8 !rounded-lg !px-3" onClick={copySql}>
              复制 SQL
            </YakButton>
            <YakButton
              type="primary"
              className="!h-8 !rounded-lg !px-3 !text-white"
              onClick={() => {
                setSqlOpen(false);
                void registerTask();
              }}
            >
              注册为加工任务并前往数据开发
            </YakButton>
          </div>
        }
      >
        {sqlLoading ? (
          <div className="py-10 text-center text-[#667085]">生成中…</div>
        ) : (
          <>
            <pre className="max-h-[420px] overflow-auto rounded-lg bg-[#1d1f21] p-4 text-[12px] leading-5 text-[#c9d1d9]">
              {sqlText}
            </pre>
            <div className="mt-2 text-[12px] text-[#667085]">
              master_id = MD5(实体编码 + PK 属性值)：多来源 PK 值一致即统一；source_ids
              记录各系统原始 ID；来源须先生成采集落地任务。
            </div>
          </>
        )}
      </Modal>

      <Modal
        title={
          changeTarget
            ? `变更申请 · ${changeTarget.masterId.slice(0, 12)}…（v${changeTarget.version}）`
            : '变更申请'
        }
        open={!!changeTarget}
        onCancel={() => setChangeTarget(null)}
        onOk={() => void submitChange()}
        okText="提交审批"
        confirmLoading={changeSubmitting}
        width={520}
      >
        <div className="mb-3 rounded-lg bg-[#f6f7f8] px-3 py-2 text-[12px] text-[#667085]">
          仅可改非 PK 属性；提交后生成审批中心单据，两级通过后自动生效并记入版本快照（详情页「变更记录」可查
          diff）。
        </div>
        <Form form={changeForm} layout="vertical">
          {editableAttributes.map((attribute) => (
            <Form.Item
              key={attribute.code}
              name={attribute.code}
              label={`${attribute.name || attribute.code}（${attribute.code}）`}
            >
              <Input allowClear />
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </div>
  );
};

export default RecordTab;
