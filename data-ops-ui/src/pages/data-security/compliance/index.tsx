import { YakButton, YakEmpty } from '@/components/ui';
import {
  complianceSummary,
  createComplianceRule,
  deleteComplianceRule,
  pageComplianceFindings,
  pageComplianceRules,
  runComplianceRule,
} from '@/services/data-security/api';
import type { ComplianceFinding, ComplianceRule } from '@/services/data-security/types';
import {
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '../shared';

const RULE_TYPES = [
  { label: '敏感字段必须脱敏', value: 'SENSITIVE_MUST_MASKED' },
  { label: '敏感字段必须人工确认', value: 'SENSITIVE_MUST_CONFIRM' },
  { label: '分级覆盖率检查', value: 'CLASSIFY_COVERAGE' },
];
const SEVERITIES = [
  { label: '高', value: 'HIGH' },
  { label: '中', value: 'MEDIUM' },
  { label: '低', value: 'LOW' },
];
const SEVERITY_COLOR: Record<string, string> = { HIGH: 'red', MEDIUM: 'orange', LOW: 'blue' };
const ruleTypeLabel = (v?: string) => RULE_TYPES.find((r) => r.value === v)?.label ?? v ?? '-';
const fmt = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-');

/* ================= 规则 ================= */
const RuleTab = ({ onBatch }: { onBatch: (batchId: string) => void }) => {
  const [form] = Form.useForm();
  const [records, setRecords] = useState<ComplianceRule[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [runningId, setRunningId] = useState<number | null>(null);

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageComplianceRules({ pageNo: p, pageSize: s, keyword: keyword.trim() || undefined });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载合规规则失败');
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, keyword, load]);

  const openCreate = () => { form.resetFields(); form.setFieldsValue({ severity: 'MEDIUM', enabled: true }); setOpen(true); };
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await createComplianceRule(values);
      message.success('规则已创建');
      setOpen(false);
      await load(pageNo, pageSize);
    } catch {
      message.error('保存失败（编码可能已存在或类型非法）');
    } finally {
      setSaving(false);
    }
  };
  const run = (record: ComplianceRule) => {
    setRunningId(record.id);
    Modal.confirm({
      title: '执行合规体检',
      content: `对规则「${record.ruleName}」执行一次体检？`,
      okText: '执行',
      onOk: async () => {
        try {
          const result = await runComplianceRule(record.id);
          message.success(`体检完成：检查 ${result.checked} · 通过 ${result.passed} · 不通过 ${result.failed}`);
          onBatch(result.batchId);
        } catch {
          message.error('体检失败');
        } finally {
          setRunningId(null);
        }
      },
      onCancel: () => setRunningId(null),
    });
  };
  const remove = (record: ComplianceRule) => {
    Modal.confirm({
      title: '删除规则', content: `确定删除「${record.ruleName}」？`, okType: 'danger',
      onOk: async () => {
        try {
          await deleteComplianceRule(record.id);
          await load(pageNo, pageSize);
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const columns: ColumnsType<ComplianceRule> = [
    { title: '编码', dataIndex: 'ruleCode', width: 140 },
    { title: '名称', dataIndex: 'ruleName', width: 180 },
    { title: '类型', dataIndex: 'ruleType', render: (v?: string) => ruleTypeLabel(v) },
    {
      title: '级别', dataIndex: 'severity', width: 80,
      render: (v?: string) => <Tag color={SEVERITY_COLOR[v ?? ''] ?? 'default'}>{v ?? '-'}</Tag>,
    },
    {
      title: '启用', dataIndex: 'enabled', width: 80,
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'default'}>{v === 1 ? '是' : '否'}</Tag>,
    },
    {
      title: '操作', key: 'action', width: 160,
      render: (_, record) => (
        <Space size={4}>
          <Typography.Link onClick={() => run(record)}>{runningId === record.id ? '体检中…' : '体检'}</Typography.Link>
          <Typography.Link type="danger" onClick={() => remove(record)}>删除</Typography.Link>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <Input allowClear placeholder="按编码/名称搜索" className="!w-64" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>新建规则</YakButton>
      </div>
      <Table<ComplianceRule> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading}
        locale={{ emptyText: <YakEmpty compact title="暂无合规规则" description="点击「新建规则」创建体检规则" /> }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }} />
      <Modal title="新建合规规则" open={open} onOk={submit} confirmLoading={saving} onCancel={() => setOpen(false)} destroyOnClose okText="创建">
        <Form form={form} layout="vertical" preserve={false}>
          <div className="grid grid-cols-2 gap-x-3">
            <Form.Item name="code" label="规则编码" rules={[{ required: true, message: '请输入编码' }]}><Input placeholder="如 CHECK_001" /></Form.Item>
            <Form.Item name="name" label="规则名称" rules={[{ required: true, message: '请输入名称' }]}><Input placeholder="如 手机号必须脱敏" /></Form.Item>
          </div>
          <Form.Item name="ruleType" label="规则类型" rules={[{ required: true, message: '请选择类型' }]}>
            <Select options={RULE_TYPES} placeholder="选择体检类型" />
          </Form.Item>
          <div className="grid grid-cols-2 gap-x-3">
            <Form.Item name="severity" label="严重级别"><Select options={SEVERITIES} /></Form.Item>
            <Form.Item name="params" label="参数(JSON)"><Input placeholder="选填，如阈值" /></Form.Item>
          </div>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item name="description" label="描述"><Input.TextArea rows={2} placeholder="选填" /></Form.Item>
        </Form>
      </Modal>
    </>
  );
};

/* ================= 发现明细 ================= */
const FindingTab = ({ batchId }: { batchId: string }) => {
  const [records, setRecords] = useState<ComplianceFinding[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [batchFilter, setBatchFilter] = useState(batchId);
  const [passed, setPassed] = useState<boolean | undefined>();
  const [loading, setLoading] = useState(false);

  useEffect(() => { setBatchFilter(batchId); }, [batchId]);

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageComplianceFindings({ pageNo: p, pageSize: s, batchId: batchFilter.trim() || undefined, passed });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载合规发现失败');
    } finally {
      setLoading(false);
    }
  }, [batchFilter, passed]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, load]);

  const columns: ColumnsType<ComplianceFinding> = [
    { title: '检查时间', dataIndex: 'checkedTime', width: 170, render: fmt },
    { title: '类型', dataIndex: 'ruleType', width: 200, render: (v?: string) => ruleTypeLabel(v) },
    { title: '对象', dataIndex: 'targetName', ellipsis: true, render: (v: string, r) => v || r.targetKey || '-' },
    {
      title: '结果', dataIndex: 'passed', width: 90,
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'red'}>{v === 1 ? '通过' : '不通过'}</Tag>,
    },
    {
      title: '级别', dataIndex: 'severity', width: 80,
      render: (v?: string) => <Tag color={SEVERITY_COLOR[v ?? ''] ?? 'default'}>{v ?? '-'}</Tag>,
    },
    { title: '发现', dataIndex: 'finding', ellipsis: true, render: (v?: string) => v || '-' },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center gap-3">
        <Input allowClear placeholder="按批次号过滤" className="!w-64" value={batchFilter} onChange={(e) => setBatchFilter(e.target.value)} />
        <Select allowClear placeholder="按结果" className="!w-32" value={passed} onChange={setPassed}
          options={[{ label: '通过', value: true }, { label: '不通过', value: false }]} />
      </div>
      <Table<ComplianceFinding> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading}
        locale={{ emptyText: <YakEmpty compact title="暂无合规发现" description="到「合规规则」执行体检后查看结果" /> }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }} />
    </>
  );
};

/* ================= 页面 ================= */
const DataSecurityCompliancePage = () => {
  const [summary, setSummary] = useState<{ batchId?: string; openGaps?: number }>({});
  const [activeBatch, setActiveBatch] = useState('');

  const loadSummary = useCallback(async () => {
    try {
      const s = await complianceSummary();
      setSummary({ batchId: (s.batchId as string) ?? undefined, openGaps: Number(s.openGaps ?? 0) });
      setActiveBatch((prev) => prev || ((s.batchId as string) ?? ''));
    } catch {
      /* 汇总加载失败不阻断 */
    }
  }, []);

  useEffect(() => {
    void loadSummary();
  }, [loadSummary]);

  const openGaps = summary.openGaps ?? 0;

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <PageHeader
        title="合规管理"
        subtitle="配置合规体检规则、执行体检并查看缺口明细"
        extra={
          <div className="flex items-center gap-4 rounded-lg bg-[#f7f8fa] px-4 py-2 text-[13px]">
            <span>最近批次：<span className="font-medium">{summary.batchId || '—'}</span></span>
            <span>未闭环缺口：
              <span className={`ml-1 font-semibold ${openGaps > 0 ? 'text-[#f5222d]' : 'text-[#52c41a]'}`}>{openGaps}</span>
            </span>
          </div>
        }
      />
      <div className="mt-4">
        <Tabs
          items={[
            { key: 'rule', label: '合规规则', children: <RuleTab onBatch={(b) => { setActiveBatch(b); void loadSummary(); }} /> },
            { key: 'finding', label: '发现明细', children: <FindingTab batchId={activeBatch} /> },
          ]}
        />
      </div>
    </div>
  );
};

export default DataSecurityCompliancePage;
