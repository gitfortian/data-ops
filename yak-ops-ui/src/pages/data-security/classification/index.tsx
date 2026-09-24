import { YakButton, YakEmpty } from '@/components/ui';
import {
  changeClassificationStatus,
  changeSecurityLevelStatus,
  createDataCategory,
  createDiscoveryRule,
  createSecurityLevel,
  deleteClassification,
  deleteDataCategory,
  deleteDiscoveryRule,
  deleteSecurityLevel,
  listActiveSecurityLevels,
  listAllDataCategories,
  pageClassifications,
  pageDataCategories,
  pageDiscoveryRules,
  pageSecurityLevels,
  scanDiscoveryFields,
  updateDataCategory,
  updateDiscoveryRule,
  updateSecurityLevel,
  upsertClassification,
} from '@/services/data-security/api';
import type {
  Classification,
  DataCategory,
  DiscoveryRule,
  DiscoverableField,
  SecurityLevel,
} from '@/services/data-security/types';
import { getStandardOptions } from '@/services/semantic/api';
import type { SemanticStandardOption } from '@/services/semantic/types';
import {
  Button,
  Form,
  Input,
  InputNumber,
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
import { history, useSearchParams } from '@umijs/max';
import { PageHeader, rankColor } from '../shared';

type Option = { label: string; value: number };

const useDsecOptions = () => {
  const [levels, setLevels] = useState<SecurityLevel[]>([]);
  const [categories, setCategories] = useState<DataCategory[]>([]);
  const refresh = useCallback(async () => {
    try {
      const [ls, cs] = await Promise.all([listActiveSecurityLevels(), listAllDataCategories()]);
      setLevels(ls ?? []);
      setCategories(cs ?? []);
    } catch {
      /* 下拉加载失败不阻断列表 */
    }
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);
  const levelOptions: Option[] = levels.map((l) => ({ label: `${l.levelName}（${l.levelCode}）`, value: l.id }));
  const categoryOptions: Option[] = categories.map((c) => ({ label: c.categoryName, value: c.id }));
  const categoryCodeOptions = categories.map((c) => ({ label: c.categoryName, value: c.categoryCode }));
  return { levels, categories, levelOptions, categoryOptions, categoryCodeOptions, refreshCategories: refresh };
};

const levelNameById = (levels: SecurityLevel[], id?: number) =>
  id == null ? '-' : levels.find((l) => l.id === id)?.levelName ?? `#${id}`;
const categoryNameById = (categories: DataCategory[], id?: number) =>
  id == null ? '-' : categories.find((c) => c.id === id)?.categoryName ?? `#${id}`;

const formatTime = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '-');

/* ================= 安全等级 ================= */
const LevelTab = () => {
  const [form] = Form.useForm();
  const [records, setRecords] = useState<SecurityLevel[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState(() => new URLSearchParams(window.location.search).get('keyword') ?? '');
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<SecurityLevel | null>(null);
  const [saving, setSaving] = useState(false);
  // 关联数据标准下拉源=语义中心 SECURITY 标准(字段级分级/脱敏模板,ticket 01 裁决;等级字典真源在本页)
  const [securityStandards, setSecurityStandards] = useState<SemanticStandardOption[]>([]);

  useEffect(() => {
    getStandardOptions(['SECURITY'])
      .then((res) => setSecurityStandards(res.SECURITY ?? []))
      .catch(() => setSecurityStandards([]));
  }, []);

  const stdSecurityOptions: Option[] = securityStandards.map((o) => ({ label: `${o.name}（${o.code}）`, value: o.id }));
  const stdSecurityLabel = (id?: number) => {
    if (id == null) return '-';
    const found = securityStandards.find((o) => o.id === id);
    return found ? `${found.name}（${found.code}）` : `#${id}`;
  };

  const load = useCallback(
    async (nextPageNo: number, nextPageSize: number) => {
      setLoading(true);
      try {
        const result = await pageSecurityLevels({ pageNo: nextPageNo, pageSize: nextPageSize, keyword: keyword.trim() || undefined });
        setRecords(result.bizData ?? []);
        setTotal(result.pagination?.total ?? 0);
      } catch {
        setRecords([]);
        message.error('加载安全等级失败');
      } finally {
        setLoading(false);
      }
    },
    [keyword],
  );

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, keyword, load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setOpen(true);
  };
  const openEdit = (record: SecurityLevel) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.levelName,
      rankNo: record.rankNo,
      stdSecurityId: record.stdSecurityId,
      description: record.description,
    });
    setOpen(true);
  };
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateSecurityLevel(editing.id, values);
        message.success('等级已更新');
      } else {
        await createSecurityLevel(values);
        message.success('等级已创建');
      }
      setOpen(false);
      await load(pageNo, pageSize);
    } catch {
      message.error('保存失败（编码可能已存在）');
    } finally {
      setSaving(false);
    }
  };
  const toggleStatus = (record: SecurityLevel) => {
    const next = record.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    Modal.confirm({
      title: next === 'ACTIVE' ? '启用等级' : '停用等级',
      content: `确定${next === 'ACTIVE' ? '启用' : '停用'}「${record.levelName}」？`,
      onOk: async () => {
        try {
          await changeSecurityLevelStatus(record.id, next);
          await load(pageNo, pageSize);
        } catch {
          message.error('状态变更失败');
        }
      },
    });
  };
  const remove = (record: SecurityLevel) => {
    Modal.confirm({
      title: '删除等级',
      content: `确定删除「${record.levelName}（${record.levelCode}）」？被分级标签引用时将被阻断。`,
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteSecurityLevel(record.id);
          await load(pageNo, pageSize);
        } catch {
          message.error('删除失败（可能已被引用）');
        }
      },
    });
  };

  const columns: ColumnsType<SecurityLevel> = [
    { title: '编码', dataIndex: 'levelCode', width: 140 },
    { title: '名称', dataIndex: 'levelName', width: 160 },
    {
      title: '关联安全标准',
      dataIndex: 'stdSecurityId',
      width: 170,
      ellipsis: true,
      render: (value?: number) => stdSecurityLabel(value),
    },
    {
      title: '序位',
      dataIndex: 'rankNo',
      width: 90,
      render: (value: number) => (
        <Tag color={rankColor(value)}>{`R${value ?? '-'}`}</Tag>
      ),
    },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{value === 'ACTIVE' ? '启用' : '停用'}</Tag>,
    },
    { title: '更新时间', dataIndex: 'updateTime', width: 170, render: formatTime },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size={4}>
          <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link>
          <Typography.Link onClick={() => toggleStatus(record)}>{record.status === 'ACTIVE' ? '停用' : '启用'}</Typography.Link>
          <Typography.Link type="danger" onClick={() => remove(record)}>删除</Typography.Link>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <Input allowClear placeholder="按编码/名称搜索" className="!w-64" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>新建等级</YakButton>
      </div>
      <Table<SecurityLevel>
        rowKey="id"
        size="middle"
        columns={columns}
        dataSource={records}
        loading={loading}
        locale={{ emptyText: <YakEmpty compact title="暂无安全等级" description="点击右上角「新建等级」" /> }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }}
      />
      <Modal title={editing ? '编辑等级（编码不可改）' : '新建等级'} open={open} onOk={submit} confirmLoading={saving} onCancel={() => setOpen(false)} destroyOnClose okText={editing ? '保存' : '创建'}>
        <Form form={form} layout="vertical" preserve={false}>
          {!editing ? (
            <Form.Item name="code" label="等级编码" rules={[{ required: true, message: '请输入编码' }, { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '字母/数字/下划线，1~64 位' }]}>
              <Input placeholder="如 L4" />
            </Form.Item>
          ) : null}
          <Form.Item name="name" label="等级名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如 机密" />
          </Form.Item>
          <Form.Item name="rankNo" label="序位（越高越敏感）" initialValue={1}>
            <InputNumber min={1} max={99} className="!w-full" />
          </Form.Item>
          <Form.Item name="stdSecurityId" label="关联数据标准">
            <Select allowClear placeholder="选填，关联数据标准-安全类(分级/脱敏模板)" options={stdSecurityOptions} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

/* ================= 数据分类 ================= */
const CategoryTab = ({ categoryCodeOptions }: { categoryCodeOptions: { label: string; value: string }[] }) => {
  const [form] = Form.useForm();
  const [records, setRecords] = useState<DataCategory[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<DataCategory | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageDataCategories({ pageNo: p, pageSize: s, keyword: keyword.trim() || undefined });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载数据分类失败');
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, keyword, load]);

  const openCreate = () => { setEditing(null); form.resetFields(); setOpen(true); };
  const openEdit = (record: DataCategory) => {
    setEditing(record);
    form.setFieldsValue({ name: record.categoryName, sortOrder: record.sortOrder, description: record.description });
    setOpen(true);
  };
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateDataCategory(editing.id, { name: values.name, sortOrder: values.sortOrder, description: values.description });
        message.success('分类已更新');
      } else {
        await createDataCategory(values);
        message.success('分类已创建');
      }
      setOpen(false);
      await load(pageNo, pageSize);
    } catch {
      message.error('保存失败（编码可能已存在）');
    } finally {
      setSaving(false);
    }
  };
  const remove = (record: DataCategory) => {
    Modal.confirm({
      title: '删除分类',
      content: `确定删除「${record.categoryName}」？有子级或被引用时将被阻断。`,
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteDataCategory(record.id);
          await load(pageNo, pageSize);
        } catch {
          message.error('删除失败（可能存在子级或引用）');
        }
      },
    });
  };

  const columns: ColumnsType<DataCategory> = [
    { title: '编码', dataIndex: 'categoryCode', width: 160 },
    { title: '名称', dataIndex: 'categoryName', width: 180 },
    { title: '父级', dataIndex: 'parentCode', width: 140, render: (v?: string) => v || '—' },
    { title: '排序', dataIndex: 'sortOrder', width: 80, render: (v?: number) => v ?? '-' },
    { title: '描述', dataIndex: 'description', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '操作', key: 'action', width: 140,
      render: (_, record) => (
        <Space size={4}>
          <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link>
          <Typography.Link type="danger" onClick={() => remove(record)}>删除</Typography.Link>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <Input allowClear placeholder="按编码/名称搜索" className="!w-64" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>新建分类</YakButton>
      </div>
      <Table<DataCategory> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading}
        locale={{ emptyText: <YakEmpty compact title="暂无数据分类" description="点击右上角「新建分类」" /> }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }} />
      <Modal title={editing ? '编辑分类（编码不可改）' : '新建分类'} open={open} onOk={submit} confirmLoading={saving} onCancel={() => setOpen(false)} destroyOnClose okText={editing ? '保存' : '创建'}>
        <Form form={form} layout="vertical" preserve={false}>
          {!editing ? (
            <Form.Item name="code" label="分类编码" rules={[{ required: true, message: '请输入编码' }]}>
              <Input placeholder="如 PERSONAL_INFO" />
            </Form.Item>
          ) : null}
          <Form.Item name="name" label="分类名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="如 个人信息" />
          </Form.Item>
          {!editing ? (
            <Form.Item name="parentCode" label="父级分类">
              <Select allowClear placeholder="顶级留空" options={categoryCodeOptions} />
            </Form.Item>
          ) : null}
          <Form.Item name="sortOrder" label="排序" initialValue={0}>
            <InputNumber min={0} className="!w-full" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

/* ================= 资产分级标签 ================= */
const CLASSIFICATION_STATUS: Record<string, { label: string; color: string }> = {
  ACTIVE: { label: '生效', color: 'green' },
  CANDIDATE: { label: '候选', color: 'gold' },
  DISABLED: { label: '停用', color: 'default' },
};

const ClassificationTab = ({ levelOptions, categoryOptions, levels, categories }: {
  levelOptions: Option[]; categoryOptions: Option[]; levels: SecurityLevel[]; categories: DataCategory[];
}) => {
  const [form] = Form.useForm();
  const [records, setRecords] = useState<Classification[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [levelId, setLevelId] = useState<number | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageClassifications({ pageNo: p, pageSize: s, keyword: keyword.trim() || undefined, levelId, status });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载分级标签失败');
    } finally {
      setLoading(false);
    }
  }, [keyword, levelId, status]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, load]);

  const openCreate = () => { form.resetFields(); form.setFieldsValue({ objectType: 'COLUMN', source: 'MANUAL', status: 'ACTIVE' }); setOpen(true); };
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await upsertClassification(values);
      message.success('分级标签已保存');
      setOpen(false);
      await load(pageNo, pageSize);
    } catch {
      message.error('保存失败，请检查对象与等级');
    } finally {
      setSaving(false);
    }
  };
  const changeStatus = (record: Classification, next: string) => {
    Modal.confirm({
      title: `状态流转为 ${CLASSIFICATION_STATUS[next]?.label ?? next}`,
      content: `确定将「${record.objectName || record.objectKey}」流转为${CLASSIFICATION_STATUS[next]?.label ?? next}？`,
      onOk: async () => {
        try {
          await changeClassificationStatus(record.id, next);
          await load(pageNo, pageSize);
        } catch {
          message.error('状态变更失败');
        }
      },
    });
  };
  const remove = (record: Classification) => {
    Modal.confirm({
      title: '删除分级标签',
      content: `确定删除「${record.objectName || record.objectKey}」的分级标签？`,
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteClassification(record.id);
          await load(pageNo, pageSize);
        } catch {
          message.error('删除失败');
        }
      },
    });
  };

  const columns: ColumnsType<Classification> = [
    { title: '对象', dataIndex: 'objectName', render: (v: string, r) => v || r.objectKey || `${r.dbName ?? ''}.${r.tableName ?? ''}.${r.columnName ?? ''}` },
    { title: '等级', dataIndex: 'levelId', width: 120, render: (v?: number) => levelNameById(levels, v) },
    { title: '分类', dataIndex: 'categoryId', width: 120, render: (v?: number) => categoryNameById(categories, v) },
    { title: '来源', dataIndex: 'source', width: 100, render: (v?: string) => (v === 'DISCOVERY' ? '自动发现' : v === 'MANUAL' ? '人工' : v || '-') },
    { title: '置信度', dataIndex: 'confidence', width: 90, render: (v?: number) => (v == null ? '-' : `${v}%`) },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (v: string) => { const s = CLASSIFICATION_STATUS[v] ?? { label: v, color: 'default' }; return <Tag color={s.color}>{s.label}</Tag>; },
    },
    {
      title: '操作', key: 'action', width: 200,
      render: (_, record) => (
        <Space size={4}>
          {record.status !== 'ACTIVE' ? <Typography.Link onClick={() => changeStatus(record, 'ACTIVE')}>确认</Typography.Link> : null}
          {record.status !== 'DISABLED' ? <Typography.Link onClick={() => changeStatus(record, 'DISABLED')}>停用</Typography.Link> : null}
          <Typography.Link type="danger" onClick={() => remove(record)}>删除</Typography.Link>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <Space wrap>
          <Input allowClear placeholder="按对象/表/列搜索" className="!w-56" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
          <Select allowClear placeholder="按等级" className="!w-40" options={levelOptions} value={levelId} onChange={setLevelId} />
          <Select allowClear placeholder="按状态" className="!w-32" value={status} onChange={setStatus}
            options={Object.entries(CLASSIFICATION_STATUS).map(([value, v]) => ({ label: v.label, value }))} />
        </Space>
        <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>新增定级</YakButton>
      </div>
      <Table<Classification> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading}
        locale={{ emptyText: <YakEmpty compact title="暂无定级标签" description="人工新增或到「敏感发现」扫描生成候选" /> }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }} />
      <Modal title="新增/更新分级标签" open={open} onOk={submit} confirmLoading={saving} onCancel={() => setOpen(false)} destroyOnClose okText="保存" width={560}>
        <Form form={form} layout="vertical" preserve={false}>
          <div className="grid grid-cols-2 gap-x-3">
            <Form.Item name="objectType" label="对象类型" rules={[{ required: true }]}>
              <Select options={[{ label: '字段 COLUMN', value: 'COLUMN' }, { label: '表 TABLE', value: 'TABLE' }]} />
            </Form.Item>
            <Form.Item name="objectName" label="对象名称"><Input placeholder="选填，展示用" /></Form.Item>
            <Form.Item name="dbName" label="库名"><Input placeholder="选填" /></Form.Item>
            <Form.Item name="tableName" label="表名" rules={[{ required: true, message: '请输入表名' }]}><Input placeholder="如 user_info" /></Form.Item>
            <Form.Item name="columnName" label="列名"><Input placeholder="字段级定级时填写" /></Form.Item>
            <Form.Item name="datasourceId" label="数据源ID"><InputNumber className="!w-full" placeholder="选填" /></Form.Item>
            <Form.Item name="levelId" label="安全等级" rules={[{ required: true, message: '请选择等级' }]}><Select options={levelOptions} placeholder="选择等级" /></Form.Item>
            <Form.Item name="categoryId" label="数据分类"><Select allowClear options={categoryOptions} placeholder="选择分类" /></Form.Item>
          </div>
        </Form>
      </Modal>
    </>
  );
};

/* ================= 敏感发现 ================= */
const MATCH_TYPES = [
  { label: '正则 REGEX', value: 'REGEX' },
  { label: '前缀 PREFIX', value: 'PREFIX' },
  { label: '包含 CONTAINS', value: 'CONTAINS' },
  { label: '精确 EQUALS', value: 'EQUALS' },
];

const DiscoveryTab = ({ levelOptions, categoryOptions, levels, categories }: {
  levelOptions: Option[]; categoryOptions: Option[]; levels: SecurityLevel[]; categories: DataCategory[];
}) => {
  const [form] = Form.useForm();
  const [scanForm] = Form.useForm<{ fields: DiscoverableField[] }>();
  const [records, setRecords] = useState<DiscoveryRule[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<DiscoveryRule | null>(null);
  const [saving, setSaving] = useState(false);
  const [scanOpen, setScanOpen] = useState(false);
  const [scanning, setScanning] = useState(false);

  const load = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await pageDiscoveryRules({ pageNo: p, pageSize: s, keyword: keyword.trim() || undefined });
      setRecords(result.bizData ?? []);
      setTotal(result.pagination?.total ?? 0);
    } catch {
      setRecords([]);
      message.error('加载发现规则失败');
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void load(pageNo, pageSize);
  }, [pageNo, pageSize, keyword, load]);

  const openCreate = () => { setEditing(null); form.resetFields(); form.setFieldsValue({ matchType: 'CONTAINS', enabled: true }); setOpen(true); };
  const openEdit = (record: DiscoveryRule) => {
    setEditing(record);
    form.setFieldsValue({
      name: record.ruleName, matchType: record.matchType, pattern: record.pattern,
      levelId: record.levelId, categoryId: record.categoryId, enabled: record.enabled === 1, description: record.description,
    });
    setOpen(true);
  };
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateDiscoveryRule(editing.id, values);
        message.success('规则已更新');
      } else {
        await createDiscoveryRule(values);
        message.success('规则已创建');
      }
      setOpen(false);
      await load(pageNo, pageSize);
    } catch {
      message.error('保存失败（编码可能已存在）');
    } finally {
      setSaving(false);
    }
  };
  const remove = (record: DiscoveryRule) => {
    Modal.confirm({
      title: '删除发现规则', content: `确定删除「${record.ruleName}」？`, okType: 'danger',
      onOk: async () => {
        try {
          await deleteDiscoveryRule(record.id);
          await load(pageNo, pageSize);
        } catch {
          message.error('删除失败');
        }
      },
    });
  };
  const runScan = async () => {
    const values = await scanForm.validateFields();
    const fields = (values.fields ?? []).filter((f) => f.tableName || f.columnName);
    if (fields.length === 0) { message.warning('请至少填写一个字段'); return; }
    setScanning(true);
    try {
      const hits = await scanDiscoveryFields(fields);
      message.success(`扫描完成，生成 ${hits ?? 0} 个候选标签`);
      setScanOpen(false);
      scanForm.resetFields();
    } catch {
      message.error('扫描失败');
    } finally {
      setScanning(false);
    }
  };

  const columns: ColumnsType<DiscoveryRule> = [
    { title: '编码', dataIndex: 'ruleCode', width: 140 },
    { title: '名称', dataIndex: 'ruleName', width: 160 },
    { title: '匹配方式', dataIndex: 'matchType', width: 120 },
    { title: '模式', dataIndex: 'pattern', ellipsis: true, render: (v?: string) => v || '-' },
    { title: '命中等级', dataIndex: 'levelId', width: 120, render: (v?: number) => levelNameById(levels, v) },
    {
      title: '启用', dataIndex: 'enabled', width: 80,
      render: (v: number) => <Tag color={v === 1 ? 'green' : 'default'}>{v === 1 ? '是' : '否'}</Tag>,
    },
    {
      title: '操作', key: 'action', width: 140,
      render: (_, record) => (
        <Space size={4}>
          <Typography.Link onClick={() => openEdit(record)}>编辑</Typography.Link>
          <Typography.Link type="danger" onClick={() => remove(record)}>删除</Typography.Link>
        </Space>
      ),
    },
  ];

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <Input allowClear placeholder="按编码/名称搜索" className="!w-64" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <Space>
          <YakButton className="!h-9 !rounded-lg" onClick={() => { scanForm.resetFields(); scanForm.setFieldsValue({ fields: [{}] }); setScanOpen(true); }}>扫描字段</YakButton>
          <YakButton type="primary" className="!h-9 !rounded-lg !px-4 !text-white" onClick={openCreate}>新建规则</YakButton>
        </Space>
      </div>
      <Table<DiscoveryRule> rowKey="id" size="middle" columns={columns} dataSource={records} loading={loading}
        locale={{ emptyText: <YakEmpty compact title="暂无发现规则" description="点击右上角「新建规则」" /> }}
        pagination={{ current: pageNo, pageSize, total, showSizeChanger: true, showTotal: (c) => `共 ${c} 条`, onChange: (p, s) => { setPageNo(p); setPageSize(s); } }} />

      <Modal title={editing ? '编辑发现规则（编码不可改）' : '新建发现规则'} open={open} onOk={submit} confirmLoading={saving} onCancel={() => setOpen(false)} destroyOnClose okText={editing ? '保存' : '创建'} width={560}>
        <Form form={form} layout="vertical" preserve={false}>
          {!editing ? (
            <Form.Item name="code" label="规则编码" rules={[{ required: true, message: '请输入编码' }]}><Input placeholder="如 PHONE_REGEX" /></Form.Item>
          ) : null}
          <Form.Item name="name" label="规则名称" rules={[{ required: true, message: '请输入名称' }]}><Input placeholder="如 手机号识别" /></Form.Item>
          <div className="grid grid-cols-2 gap-x-3">
            <Form.Item name="matchType" label="匹配方式" rules={[{ required: true }]}><Select options={MATCH_TYPES} /></Form.Item>
            <Form.Item name="pattern" label="匹配模式"><Input placeholder="正则/关键词" /></Form.Item>
            <Form.Item name="levelId" label="命中等级"><Select allowClear options={levelOptions} placeholder="选择等级" /></Form.Item>
            <Form.Item name="categoryId" label="命中分类"><Select allowClear options={categoryOptions} placeholder="选择分类" /></Form.Item>
          </div>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          <Form.Item name="description" label="描述"><Input.TextArea rows={2} placeholder="选填" /></Form.Item>
        </Form>
      </Modal>

      <Modal title="扫描字段生成候选" open={scanOpen} onOk={runScan} confirmLoading={scanning} onCancel={() => setScanOpen(false)} destroyOnClose okText="开始扫描" width={640}>
        <div className="mb-2 text-[12px] text-[#98a2b3]">填写待扫描字段，命中已启用规则后生成「候选」分级标签，再到「资产分级」确认。</div>
        <Form form={scanForm} layout="vertical" preserve={false} initialValues={{ fields: [{}] }}>
          <Form.List name="fields">
            {(fieldsList, { add, remove: removeRow }) => (
              <>
                {fieldsList.map(({ key, name, ...rest }) => (
                  <Space key={key} align="baseline" className="mb-2 flex">
                    <Form.Item {...rest} name={[name, 'dbName']} className="mb-0"><Input placeholder="库名" /></Form.Item>
                    <Form.Item {...rest} name={[name, 'tableName']} className="mb-0"><Input placeholder="表名" /></Form.Item>
                    <Form.Item {...rest} name={[name, 'columnName']} className="mb-0"><Input placeholder="列名" /></Form.Item>
                    <Form.Item {...rest} name={[name, 'comment']} className="mb-0"><Input placeholder="列注释" /></Form.Item>
                    <Typography.Link type="danger" onClick={() => removeRow(name)}>删除</Typography.Link>
                  </Space>
                ))}
                <YakButton size="small" onClick={() => add()}>+ 添加字段</YakButton>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </>
  );
};

/* ================= 页面 ================= */
const DataSecurityClassificationPage = () => {
  const { levelOptions, categoryOptions, categoryCodeOptions, levels, categories } = useDsecOptions();
  const [searchParams] = useSearchParams();
  const returnAssetIdValue = searchParams.get('returnAssetId');
  const returnAssetId = returnAssetIdValue && /^\d+$/.test(returnAssetIdValue)
    ? Number(returnAssetIdValue)
    : undefined;
  const activeTab = searchParams.get('activeTab') === 'classification' ? 'classification' : 'level';

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <PageHeader
        title="分级分类"
        subtitle="安全等级与数据分类字典、资产分级标签、敏感数据发现"
        extra={returnAssetId ? (
          <Button onClick={() => history.push(`/data-asset/detail/${returnAssetId}`)}>返回资产详情</Button>
        ) : undefined}
      />
      <div className="mt-4">
        <Tabs
          defaultActiveKey={activeTab}
          items={[
            { key: 'level', label: '安全等级', children: <LevelTab /> },
            { key: 'category', label: '数据分类', children: <CategoryTab categoryCodeOptions={categoryCodeOptions} /> },
            { key: 'classification', label: '资产分级', children: <ClassificationTab levelOptions={levelOptions} categoryOptions={categoryOptions} levels={levels} categories={categories} /> },
            { key: 'discovery', label: '敏感发现', children: <DiscoveryTab levelOptions={levelOptions} categoryOptions={categoryOptions} levels={levels} categories={categories} /> },
          ]}
        />
      </div>
    </div>
  );
};

export default DataSecurityClassificationPage;
