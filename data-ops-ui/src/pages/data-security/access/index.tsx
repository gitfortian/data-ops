import { YakButton, YakEmpty } from '@/components/ui';
import {
  createAccessPolicy,
  decideAccess,
  deleteAccessPolicy,
  disableAccessPolicy,
  listActiveSecurityLevels,
  pageAccessPolicies,
  submitAccessPolicyApproval,
  updateAccessPolicy,
} from '@/services/data-security/api';
import type {
  AccessDecision,
  AccessPolicy,
  SecurityLevel,
} from '@/services/data-security/types';
import {
  DatePicker,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../shared';

const { RangePicker } = DatePicker;

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待审批', color: 'gold' },
  APPROVED: { label: '已生效', color: 'green' },
  REJECTED: { label: '已驳回', color: 'red' },
  DISABLED: { label: '已停用', color: 'default' },
};

const SUBJECT_TYPES = [
  { label: '用户 USER', value: 'USER' },
  { label: '角色 ROLE', value: 'ROLE' },
];
const SCOPE_TYPES = [
  { label: '数据源 DATASOURCE', value: 'DATASOURCE' },
  { label: '库 DATABASE', value: 'DATABASE' },
  { label: '表 TABLE', value: 'TABLE' },
  { label: '字段 COLUMN', value: 'COLUMN' },
  { label: '等级 LEVEL', value: 'LEVEL' },
  { label: '全部 ALL', value: 'ALL' },
];
const ACCESS_TYPES = [
  { label: '读取 READ', value: 'READ' },
  { label: '写入 WRITE', value: 'WRITE' },
  { label: '导出 EXPORT', value: 'EXPORT' },
];
const EFFECTS = [
  { label: '允许 ALLOW', value: 'ALLOW' },
  { label: '拒绝 DENY', value: 'DENY' },
];

const fmt = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-');

const DataSecurityAccessPage = () => {
  const [form] = Form.useForm();
  const [decideForm] = Form.useForm();
  const [records, setRecords] = useState<AccessPolicy[]>([]);
  const [levels, setLevels] = useState<SecurityLevel[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<AccessPolicy | null>(null);
  const [saving, setSaving] = useState(false);
  const [decideOpen, setDecideOpen] = useState(false);
  const [deciding, setDeciding] = useState(false);
  const [decision, setDecision] = useState<AccessDecision | null>(null);
  const selectedScope = Form.useWatch('scopeType', form);
  const needsDatasource = ['DATASOURCE', 'DATABASE', 'TABLE', 'COLUMN'].includes(selectedScope);
  const needsDatabase = ['DATABASE', 'TABLE', 'COLUMN'].includes(selectedScope);
  const needsTable = ['TABLE', 'COLUMN'].includes(selectedScope);
  const needsColumn = selectedScope === 'COLUMN';

  const levelOptions = levels.map((l) => ({ label: `${l.levelName}（${l.levelCode}）`, value: l.id }));

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageAccessPolicies({ pageNo: p, pageSize: s, keyword: keyword.trim() || undefined, status });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载访问策略失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, load]);
  useEffect(() => {
    listActiveSecurityLevels().then((ls) => setLevels(ls ?? [])).catch(() => undefined);
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ subjectType: 'ROLE', scopeType: 'TABLE', accessType: 'READ', effect: 'ALLOW', priority: 100 });
    setOpen(true);
  };
  const openEdit = (record: AccessPolicy) => {
    setEditing(record);
    form.setFieldsValue({
      ...record,
      validity: record.validFrom && record.validTo ? [dayjs(record.validFrom), dayjs(record.validTo)] : undefined,
    });
    setOpen(true);
  };
  const submit = async () => {
    const values = await form.validateFields();
    const { validity, ...rest } = values;
    const payload = {
      ...rest,
      validFrom: validity?.[0] ? dayjs(validity[0]).format('YYYY-MM-DDTHH:mm:ss') : undefined,
      validTo: validity?.[1] ? dayjs(validity[1]).format('YYYY-MM-DDTHH:mm:ss') : undefined,
    };
    setSaving(true);
    try {
      const saved = editing
        ? await updateAccessPolicy(editing.id, payload)
        : await createAccessPolicy(payload);
      setOpen(false);
      try {
        await submitAccessPolicyApproval(saved.id);
        message.success('策略申请已提交到审批中心');
      } catch {
        message.warning('策略已保存为待审批状态，请在列表中重试提交审批');
      }
      await load(pageNo, pageSize);
    } catch {
      message.error('保存失败，请检查策略配置');
    } finally {
      setSaving(false);
    }
  };
  const submitApproval = (record: AccessPolicy) => {
    Modal.confirm({
      title: '提交到审批中心',
      content: `将「${record.policyName}」提交到已配置的 ACCESS_GRANT 审批流程？`,
      onOk: async () => {
        try {
          await submitAccessPolicyApproval(record.id);
          message.success('已提交到审批中心，请在审批中心完成审批');
        } catch {
          message.error('提交失败，请检查 ACCESS_GRANT 审批流程配置');
        }
      },
    });
  };
  const disable = (record: AccessPolicy) => {
    Modal.confirm({
      title: '停用策略', content: `确定停用「${record.policyName}」？`,
      onOk: async () => {
        try {
          await disableAccessPolicy(record.id);
          await load(pageNo, pageSize);
        } catch {
          message.error('停用失败');
        }
      },
    });
  };
  const remove = (record: AccessPolicy) => {
    Modal.confirm({
      title: '删除策略', content: `确定删除「${record.policyName}」？`, okType: 'danger',
      onOk: async () => {
        try {
          await deleteAccessPolicy(record.id);
          await load(pageNo, pageSize);
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const runDecide = async () => {
    const values = await decideForm.validateFields();
    setDeciding(true);
    try {
      const result = await decideAccess({
        actor: values.actor,
        roles: values.roles ? String(values.roles).split(',').map((r) => r.trim()).filter(Boolean) : undefined,
        objectKey: values.objectKey,
        action: values.action,
      });
      setDecision(result);
    } catch {
      setDecision(null);
      message.error('裁决失败');
    } finally {
      setDeciding(false);
    }
  };

  const columns: ColumnsType<AccessPolicy> = [
    { title: '策略名称', dataIndex: 'policyName', width: 180 },
    { title: '主体', key: 'subject', width: 160, render: (_, r) => `${r.subjectType === 'USER' ? '用户' : r.subjectType === 'ROLE' ? '角色' : r.subjectType ?? '-'}：${r.subjectKey ?? '-'}` },
    {
      title: '资源范围', key: 'scope', ellipsis: true,
      render: (_, r) => r.scopeType === 'LEVEL'
        ? `等级：${levels.find((l) => l.id === r.levelId)?.levelName ?? '-'}`
        : r.scopeType === 'ALL' ? '全部数据' : [r.datasourceId ? `数据源 ${r.datasourceId}` : null, r.dbName, r.tableName, r.columnName].filter(Boolean).join(' / '),
    },
    { title: '访问动作', dataIndex: 'accessType', width: 90, render: (v?: string) => (v === 'WRITE' ? '写入' : v === 'EXPORT' ? '导出' : v === 'READ' ? '读取' : v || '-') },
    {
      title: '效果', dataIndex: 'effect', width: 80,
      render: (v?: string) => <Tag color={v === 'DENY' ? 'red' : 'blue'}>{v === 'DENY' ? '拒绝' : '允许'}</Tag>,
    },
    { title: '优先级', dataIndex: 'priority', width: 80, render: (v?: number) => v ?? '-' },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (v: string) => { const m = STATUS_META[v] ?? { label: v, color: 'default' }; return <Tag color={m.color}>{m.label}</Tag>; },
    },
    { title: '申请人', dataIndex: 'applicant', width: 110, render: (v?: string) => v || '-' },
    {
      title: '操作', key: 'action', width: 220, fixed: 'right',
      render: (_, record) => (
        <Space size={4} wrap>
          {record.status === 'PENDING' ? (
            <Typography.Link onClick={() => submitApproval(record)}>提交审批</Typography.Link>
          ) : null}
          {record.status === 'APPROVED' ? <Typography.Link onClick={() => disable(record)}>停用</Typography.Link> : null}
          {record.status !== 'PENDING' ? <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link> : null}
          <Typography.Link type="danger" onClick={() => remove(record)}>删除</Typography.Link>
        </Space>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <PageHeader
        title="数据访问策略"
        subtitle="数据级访问策略的申请、审批与裁决试算"
        extra={<YakButton className="!h-9 !rounded-lg" onClick={() => { setDecision(null); setDecideOpen(true); }}>裁决试算</YakButton>}
      />

      <div className="mb-3 mt-4 flex flex-wrap items-center justify-between gap-3">
        <Space wrap>
          <Input allowClear placeholder="按名称/主体搜索" className="!w-60" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
          <Select allowClear placeholder="按状态" className="!w-32" value={status} onChange={setStatus}
            options={Object.entries(STATUS_META).map(([value, v]) => ({ label: v.label, value }))} />
        </Space>
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>新建策略</YakButton>
      </div>

      <Table<AccessPolicy> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading} scroll={{ x: 1200 }}
        locale={{ emptyText: <YakEmpty compact title="暂无访问策略" description="点击「新建策略」提交申请" /> }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }} />

      <Modal title={editing ? '编辑访问策略并重新提交审批' : '新建访问策略申请'} open={open} onOk={submit} confirmLoading={saving} onCancel={() => setOpen(false)} destroyOnClose okText={editing ? '保存并提交审批' : '提交审批'} width={640}>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="policyName" label="策略名称" rules={[{ required: true, message: '请输入名称' }]}><Input placeholder="如 分析师可读客户表" /></Form.Item>
          <div className="grid grid-cols-2 gap-x-3">
            <Form.Item name="subjectType" label="主体类型"><Select options={SUBJECT_TYPES} /></Form.Item>
            <Form.Item name="subjectKey" label="主体标识" rules={[{ required: true, message: '请输入主体标识' }]}><Input placeholder="用户名/角色码/部门码" /></Form.Item>
            <Form.Item name="scopeType" label="范围类型"><Select options={SCOPE_TYPES} /></Form.Item>
            <Form.Item name="accessType" label="操作类型"><Select options={ACCESS_TYPES} /></Form.Item>
            <Form.Item name="datasourceId" label="数据源 ID" rules={[{ required: needsDatasource, message: '此范围必须指定数据源 ID' }]}><InputNumber min={1} className="!w-full" placeholder="物理数据源 ID" /></Form.Item>
            <Form.Item name="dbName" label="库名" rules={[{ required: needsDatabase, message: '此范围必须指定库名' }]}><Input placeholder="物理库名" /></Form.Item>
            <Form.Item name="tableName" label="表名" rules={[{ required: needsTable, message: '此范围必须指定表名' }]}><Input placeholder="物理表名" /></Form.Item>
            <Form.Item name="columnName" label="字段名" rules={[{ required: needsColumn, message: '字段范围必须指定字段名' }]}><Input placeholder="物理字段名" /></Form.Item>
            <Form.Item name="levelId" label="安全等级"><Select allowClear options={levelOptions} placeholder="按等级授权时选择" /></Form.Item>
            <Form.Item name="effect" label="策略效果"><Select options={EFFECTS} /></Form.Item>
            <Form.Item name="priority" label="优先级（越大越优先）"><InputNumber min={0} className="!w-full" /></Form.Item>
          </div>
          <Form.Item name="validity" label="有效期（留空表示长期）">
            <RangePicker showTime className="!w-full" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer title="访问裁决试算" open={decideOpen} onClose={() => setDecideOpen(false)} width={480}>
        <div className="mb-3 rounded-lg bg-[#fffbe6] p-3 text-[12px] text-[#8c6d1f]">试算仅展示当前策略结果，不执行数据访问，也不会写入访问审计。脱敏标记表示策略要求执行，实际是否完成以消费记录为准。</div>
        <Form form={decideForm} layout="vertical" initialValues={{ action: 'READ' }}>
          <Form.Item name="actor" label="访问主体" rules={[{ required: true, message: '请输入主体' }]}><Input placeholder="如 zhangsan" /></Form.Item>
          <Form.Item name="roles" label="角色（逗号分隔）"><Input placeholder="如 analyst,pm" /></Form.Item>
          <Form.Item name="objectKey" label="资源对象" rules={[{ required: true, message: '请输入对象键' }]}><Input placeholder="如 COLUMN:1:ods:user_info:phone" /></Form.Item>
          <Form.Item name="action" label="操作"><Select options={ACCESS_TYPES} /></Form.Item>
          <YakButton type="primary" className="!w-full !rounded-lg !text-white" loading={deciding} onClick={() => void runDecide()}>执行裁决</YakButton>
        </Form>
        {decision ? (
          <div className="mt-5 rounded-xl border border-solid border-[#eceef2] bg-[#f7f8fa] p-4">
            <div className="text-[13px] text-[#667085]">裁决结果</div>
            <div className="mt-2 flex items-center gap-2">
              <Tag color={decision.decision === 'ALLOW' ? 'green' : decision.decision === 'DENY' ? 'red' : 'gold'}>
                {decision.decision === 'ALLOW' ? '放行' : decision.decision === 'DENY' ? '拒绝' : '需审批'}
              </Tag>
              {decision.maskingRequired ? <Tag color="orange">要求脱敏 · {decision.algoCode}</Tag> : <Tag>未配置脱敏指令</Tag>}
            </div>
            {decision.matchedPolicyId ? <div className="mt-2 text-[12px] text-[#667085]">命中策略 #{decision.matchedPolicyId}</div> : null}
          </div>
        ) : null}
      </Drawer>
    </div>
  );
};

export default DataSecurityAccessPage;
