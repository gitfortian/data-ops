import { YakButton, YakEmpty } from '@/components/ui';
import {
  createMaskingAlgorithm,
  createMaskingPolicy,
  deleteMaskingAlgorithm,
  deleteMaskingPolicy,
  listActiveSecurityLevels,
  listAllDataCategories,
  listSupportedAlgorithms,
  listMaskingAlgorithms,
  pageMaskingPolicies,
  previewMasking,
} from '@/services/data-security/api';
import type {
  DataCategory,
  MaskingAlgorithm,
  MaskingPolicy,
  SecurityLevel,
} from '@/services/data-security/types';
import {
  Card,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import { PageHeader, rankColor } from '../shared';

type Option = { label: string; value: number };

const fmt = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-');

/* ================= 算法字典 ================= */
const AlgorithmTab = () => {
  const [form] = Form.useForm();
  const [previewForm] = Form.useForm();
  const [records, setRecords] = useState<MaskingAlgorithm[]>([]);
  const [supported, setSupported] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [previewResult, setPreviewResult] = useState<string | null>(null);
  const [previewing, setPreviewing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [list, sup] = await Promise.all([listMaskingAlgorithms(), listSupportedAlgorithms()]);
      setRecords(list ?? []);
      setSupported(sup ?? []);
    } catch {
      message.error('加载脱敏算法失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await createMaskingAlgorithm(values);
      message.success('算法已创建');
      setOpen(false);
      form.resetFields();
      await load();
    } catch {
      message.error('保存失败（编码可能已存在）');
    } finally {
      setSaving(false);
    }
  };
  const remove = (record: MaskingAlgorithm) => {
    Modal.confirm({
      title: '删除算法', content: `确定删除「${record.algoName}」？内置算法不可删除。`, okType: 'danger',
      onOk: async () => {
        try {
          await deleteMaskingAlgorithm(record.id);
          await load();
        } catch {
          message.error('删除失败（内置算法受保护）');
        }
      },
    });
  };
  const runPreview = async () => {
    const values = await previewForm.validateFields();
    setPreviewing(true);
    try {
      const result = await previewMasking({ value: values.value, algoCode: values.algoCode, algoParams: values.params });
      setPreviewResult(result);
    } catch {
      setPreviewResult(null);
      message.error('试算失败');
    } finally {
      setPreviewing(false);
    }
  };

  const algoCodeOptions = [
    ...supported.map((code) => ({ label: `内置 · ${code}`, value: code })),
    ...records.filter((r) => !supported.includes(r.algoCode)).map((r) => ({ label: `${r.algoName} · ${r.algoCode}`, value: r.algoCode })),
  ];

  const columns: ColumnsType<MaskingAlgorithm> = [
    { title: '编码', dataIndex: 'algoCode', width: 160 },
    { title: '名称', dataIndex: 'algoName', width: 160 },
    {
      title: '类型', dataIndex: 'builtin', width: 90,
      render: (v: number) => <Tag color={v === 1 ? 'blue' : 'default'}>{v === 1 ? '内置' : '自定义'}</Tag>,
    },
    { title: '参数', dataIndex: 'params', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '操作', key: 'action', width: 100,
      render: (_, record) =>
        record.builtin === 1 ? <span className="text-[#98a2b3]">受保护</span> : <Typography.Link type="danger" onClick={() => remove(record)}>删除</Typography.Link>,
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <div className="text-[13px] text-[#667085]">共 {records.length} 个算法 · {supported.length} 个内置引擎</div>
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={() => { form.resetFields(); setOpen(true); }}>新建算法</YakButton>
      </div>
      <Table<MaskingAlgorithm> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading} pagination={false}
        locale={{ emptyText: <YakEmpty compact title="暂无脱敏算法" /> }} />

      <Card className="mt-4" title="脱敏试算" size="small">
        <Form form={previewForm} layout="vertical" preserve={false}>
          <div className="grid grid-cols-1 gap-x-3 md:grid-cols-3">
            <Form.Item name="algoCode" label="算法" rules={[{ required: true, message: '请选择算法' }]}>
              <Select showSearch placeholder="选择算法" options={algoCodeOptions}
                onChange={(code: string) => {
                  const algo = records.find((r) => r.algoCode === code);
                  previewForm.setFieldsValue({ params: algo?.params });
                }} />
            </Form.Item>
            <Form.Item name="params" label="参数(JSON)"><Input placeholder="如 {&quot;keep&quot;:3}" /></Form.Item>
            <Form.Item name="value" label="原始值" rules={[{ required: true, message: '请输入原始值' }]}><Input placeholder="如 13800138000" /></Form.Item>
          </div>
          <div className="flex items-center gap-3">
            <YakButton type="primary" className="!rounded-lg !text-white" loading={previewing} onClick={() => void runPreview()}>试算</YakButton>
            {previewResult != null ? (
              <span className="text-[14px]">结果：<Tag color={rankColor(2)} className="!px-2 !py-0.5 text-[13px]">{previewResult}</Tag></span>
            ) : null}
          </div>
        </Form>
      </Card>

      <Modal title="新建脱敏算法" open={open} onOk={submit} confirmLoading={saving} onCancel={() => setOpen(false)} destroyOnClose okText="创建">
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="code" label="算法编码" rules={[{ required: true, message: '请输入编码' }]}><Input placeholder="如 KEEP_TAIL_4" /></Form.Item>
          <Form.Item name="name" label="算法名称" rules={[{ required: true, message: '请输入名称' }]}><Input placeholder="如 保留后四位" /></Form.Item>
          <Form.Item name="params" label="参数(JSON)"><Input.TextArea rows={2} placeholder='如 {"keep":4}' /></Form.Item>
          <Form.Item name="description" label="描述"><Input.TextArea rows={2} placeholder="选填" /></Form.Item>
        </Form>
      </Modal>
    </>
  );
};

/* ================= 脱敏策略 ================= */
const PolicyTab = ({ levelOptions, categoryOptions, levels, categories, algorithms }: {
  levelOptions: Option[]; categoryOptions: Option[]; levels: SecurityLevel[]; categories: DataCategory[]; algorithms: MaskingAlgorithm[];
}) => {
  const [form] = Form.useForm();
  const [records, setRecords] = useState<MaskingPolicy[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageMaskingPolicies({ pageNo: p, pageSize: s, keyword: keyword.trim() || undefined });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载脱敏策略失败');
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, keyword, load]);

  const openCreate = () => { form.resetFields(); form.setFieldsValue({ enabled: true, priority: 100 }); setOpen(true); };
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await createMaskingPolicy(values);
      message.success('策略已创建');
      setOpen(false);
      await load(pageNo, pageSize);
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };
  const remove = (record: MaskingPolicy) => {
    Modal.confirm({
      title: '删除脱敏策略', content: `确定删除「${record.policyName}」？`, okType: 'danger',
      onOk: async () => {
        try {
          await deleteMaskingPolicy(record.id);
          await load(pageNo, pageSize);
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const algoOptions = algorithms.map((a) => ({ label: `${a.algoName}（${a.algoCode}）`, value: a.id }));
  const algoName = (id?: number) => (id == null ? '-' : algorithms.find((a) => a.id === id)?.algoName ?? `#${id}`);

  const columns: ColumnsType<MaskingPolicy> = [
    { title: '策略名称', dataIndex: 'policyName', width: 180 },
    {
      title: '适用条件', key: 'cond', ellipsis: true,
      render: (_, r) => [
        r.levelId ? `等级:${levels.find((l) => l.id === r.levelId)?.levelName ?? '-'}` : null,
        r.categoryId ? `分类:${categories.find((c) => c.id === r.categoryId)?.categoryName ?? '-'}` : null,
        r.columnPattern ? `列:${r.columnPattern}` : null,
      ].filter(Boolean).join(' · ') || '全部',
    },
    { title: '脱敏算法', dataIndex: 'algoId', width: 160, render: (v?: number) => algoName(v) },
    { title: '优先级', dataIndex: 'priority', width: 80, render: (v?: number) => v ?? '-' },
    { title: '更新时间', dataIndex: 'updateTime', width: 170, render: fmt },
    {
      title: '操作', key: 'action', width: 80,
      render: (_, record) => <Typography.Link type="danger" onClick={() => remove(record)}>删除</Typography.Link>,
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <Input allowClear placeholder="按名称搜索" className="!w-64" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>新建策略</YakButton>
      </div>
      <Table<MaskingPolicy> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading}
        locale={{ emptyText: <YakEmpty compact title="暂无脱敏策略" description="点击「新建策略」" /> }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }} />

      <Modal title="新建脱敏策略" open={open} onOk={submit} confirmLoading={saving} onCancel={() => setOpen(false)} destroyOnClose okText="创建" width={560}>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="name" label="策略名称" rules={[{ required: true, message: '请输入名称' }]}><Input placeholder="如 手机号脱敏" /></Form.Item>
          <div className="grid grid-cols-2 gap-x-3">
            <Form.Item name="levelId" label="安全等级"><Select allowClear options={levelOptions} placeholder="按等级匹配" /></Form.Item>
            <Form.Item name="categoryId" label="数据分类"><Select allowClear options={categoryOptions} placeholder="按分类匹配" /></Form.Item>
            <Form.Item name="columnPattern" label="列名匹配" className="col-span-2"><Input placeholder="如 phone / *_id，支持包含匹配" /></Form.Item>
            <Form.Item name="algoId" label="脱敏算法" rules={[{ required: true, message: '请选择算法' }]}><Select options={algoOptions} placeholder="选择算法" /></Form.Item>
            <Form.Item name="priority" label="优先级"><InputNumber min={0} className="!w-full" /></Form.Item>
          </div>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item name="description" label="描述"><Input.TextArea rows={2} placeholder="选填" /></Form.Item>
        </Form>
      </Modal>
    </>
  );
};

/* ================= 页面 ================= */
const DataSecurityMaskingPage = () => {
  const [levels, setLevels] = useState<SecurityLevel[]>([]);
  const [categories, setCategories] = useState<DataCategory[]>([]);
  const [algorithms, setAlgorithms] = useState<MaskingAlgorithm[]>([]);

  useEffect(() => {
    Promise.all([listActiveSecurityLevels(), listAllDataCategories(), listMaskingAlgorithms()])
      .then(([ls, cs, as]) => { setLevels(ls ?? []); setCategories(cs ?? []); setAlgorithms(as ?? []); })
      .catch(() => undefined);
  }, []);

  const levelOptions: Option[] = levels.map((l) => ({ label: `${l.levelName}（${l.levelCode}）`, value: l.id }));
  const categoryOptions: Option[] = categories.map((c) => ({ label: c.categoryName, value: c.id }));

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <PageHeader title="数据脱敏" subtitle="脱敏算法字典、脱敏策略与实时试算" />
      <div className="mt-4">
        <Tabs
          items={[
            { key: 'algo', label: '算法字典', children: <AlgorithmTab /> },
            {
              key: 'policy',
              label: '脱敏策略',
              children: (
                <PolicyTab
                  levelOptions={levelOptions}
                  categoryOptions={categoryOptions}
                  levels={levels}
                  categories={categories}
                  algorithms={algorithms}
                />
              ),
            },
          ]}
        />
      </div>
    </div>
  );
};

export default DataSecurityMaskingPage;
