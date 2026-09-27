import { history, useSearchParams } from '@umijs/max';
import { Button, Form, Input, Modal, message, Select, Space, Table, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { getModelingMainline } from '@/services/modeling/view';
import {
  createSemanticProcess,
  deleteSemanticProcess,
  getSemanticDomainTree,
  getSemanticProcess,
  listSemanticProcessFieldCounts,
  pageSemanticProcesses,
} from '@/services/semantic/api';
import type { SemanticDomainNode, SemanticProcessRecord } from '@/services/semantic/types';

const BIZ_TYPE_LABELS: Record<string, string> = {
  FACT: '事实',
  DIMENSION: '维度',
};

const positiveId = (value: string | null): number | undefined => {
  if (!value || !/^\d+$/.test(value)) return undefined;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
};

const flattenDomains = (nodes: SemanticDomainNode[]): { id: number; path: string }[] => {
  const out: { id: number; path: string }[] = [];
  const walk = (list: SemanticDomainNode[], prefix: string) => {
    for (const node of list) {
      const path = prefix ? `${prefix} / ${node.name}` : node.name;
      out.push({ id: node.id, path });
      walk(node.children, path);
    }
  };
  walk(nodes, '');
  return out;
};

const SemanticProcessesPage = () => {
  const [entryParams] = useSearchParams();
  const initialDomainId = positiveId(entryParams.get('domainId'));
  const focusProcessId = positiveId(entryParams.get('processId'));
  const [records, setRecords] = useState<SemanticProcessRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [domainId, setDomainId] = useState<number | undefined>(initialDomainId);
  const [bizType, setBizType] = useState<'FACT' | 'DIMENSION' | ''>('');
  const [loading, setLoading] = useState(false);
  const [domains, setDomains] = useState<SemanticDomainNode[]>([]);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SemanticProcessRecord | null>(null);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [fieldCounts, setFieldCounts] = useState<Record<number, number>>({});
  const [modelCounts, setModelCounts] = useState<Record<number, number>>({});
  const [focusedProcess, setFocusedProcess] = useState<SemanticProcessRecord | null>(null);

  const loadData = useCallback(
    async (targetPageNo: number, targetPageSize: number) => {
      setLoading(true);
      try {
        const result = await pageSemanticProcesses({
          pageNo: targetPageNo,
          pageSize: targetPageSize,
          domainId,
          keyword: keyword || undefined,
          bizType: bizType || undefined,
        });
        setRecords(result.bizData ?? []);
        setTotal(result.pagination?.total ?? 0);
      } catch {
        message.error('加载业务过程失败，请稍后重试');
      } finally {
        setLoading(false);
      }
    },
    [domainId, keyword, bizType],
  );

  useEffect(() => {
    void loadData(pageNo, pageSize);
  }, [pageNo, pageSize, loadData]);

  useEffect(() => {
    getSemanticDomainTree()
      .then((data) => setDomains(data ?? []))
      .catch(() => setDomains([]));
    listSemanticProcessFieldCounts()
      .then((counts: { processId: number; fieldCount: number }[]) =>
        setFieldCounts(Object.fromEntries((counts ?? []).map((item) => [item.processId, item.fieldCount]))),
      )
      .catch(() => setFieldCounts({}));
    getModelingMainline()
      .then((coverage: { processId: number; totalModels: number }[]) =>
        setModelCounts(Object.fromEntries((coverage ?? []).map((item) => [item.processId, item.totalModels]))),
      )
      .catch(() => setModelCounts({}));
  }, []);

  useEffect(() => {
    if (!focusProcessId) {
      setFocusedProcess(null);
      return;
    }
    getSemanticProcess(focusProcessId)
      .then((process) => {
        setFocusedProcess(process);
        setDomainId(process.domainId);
        setPageNo(1);
      })
      .catch(() => setFocusedProcess(null));
  }, [focusProcessId]);

  const domainOptions = flattenDomains(domains);

  const domainNameOf = (id: number) => domainOptions.find((option) => option.id === id)?.path ?? String(id);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ bizType: 'FACT' });
    setEditorOpen(true);
  };

  const openEdit = (record: SemanticProcessRecord) => {
    history.push(`/semantic/processes/${record.id}/edit`);
  };

  const openMetrics = (record: SemanticProcessRecord) => {
    history.push(`/metric/manage?domainId=${record.domainId}&processId=${record.id}`);
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await createSemanticProcess({ ...values, code: values.code });
      message.success('业务过程已创建');
      setEditorOpen(false);
      await loadData(pageNo, pageSize);
    } catch {
      message.error('保存失败（编码可能已存在），请检查后重试');
    } finally {
      setSaving(false);
    }
  };

  const removeProcess = (record: SemanticProcessRecord) => {
    Modal.confirm({
      title: '删除业务过程',
      content: `确定删除「${record.name}（${record.code}）」？被标准字段集或源表关联引用时将被阻断。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteSemanticProcess(record.id);
          message.success('已删除');
          await loadData(pageNo, pageSize);
        } catch {
          message.error('删除失败，请稍后重试');
        }
      },
    });
  };

  const columns = [
    {
      title: '编码',
      dataIndex: 'code',
      width: 170,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    { title: '名称', dataIndex: 'name', width: 170, ellipsis: true },
    {
      title: '业务域',
      dataIndex: 'domainId',
      width: 200,
      ellipsis: true,
      render: (value: number) => domainNameOf(value),
    },
    { title: '粒度', dataIndex: 'grain', width: 100, render: (v?: string) => v || '-' },
    {
      title: '类型',
      dataIndex: 'bizType',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'FACT' ? 'blue' : 'gold'}>{BIZ_TYPE_LABELS[value] ?? value}</Tag>
      ),
    },
    { title: '负责人', dataIndex: 'owner', width: 110, render: (v?: string) => v || '-' },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: '引用字段数',
      key: 'fieldCount',
      width: 110,
      render: (_: unknown, record: SemanticProcessRecord) => String(fieldCounts[record.id] ?? 0),
    },
    {
      title: '模型数',
      key: 'modelCount',
      width: 90,
      render: (_: unknown, record: SemanticProcessRecord) => String(modelCounts[record.id] ?? 0),
    },
    {
      title: '操作',
      key: 'actions',
      width: 240,
      render: (_: unknown, record: SemanticProcessRecord) => (
        <Space size={0}>
          <Button type="link" size="small" onClick={() => openMetrics(record)}>
            查看指标
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(record)}>
            编辑
          </Button>
          <Button type="link" size="small" danger onClick={() => removeProcess(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">业务过程</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            挂在业务域下的业务动作，是标准字段集与按过程派生建模的锚点
          </div>
        </div>
        <Space wrap>
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="全部业务域"
            style={{ width: 200 }}
            value={domainId}
            onChange={(value) => {
              setDomainId(value);
              setPageNo(1);
            }}
            options={domainOptions.map((item) => ({ label: item.path, value: item.id }))}
          />
          <Select
            allowClear
            placeholder="全部类型"
            style={{ width: 120 }}
            value={bizType || undefined}
            onChange={(value) => {
              setBizType((value ?? '') as 'FACT' | 'DIMENSION' | '');
              setPageNo(1);
            }}
            options={[
              { label: '事实', value: 'FACT' },
              { label: '维度', value: 'DIMENSION' },
            ]}
          />
          <Input.Search
            allowClear
            placeholder="按编码或名称搜索"
            className="!w-[220px]"
            onSearch={(value) => {
              setKeyword(value.trim());
              setPageNo(1);
            }}
          />
          <YakButton
            type="primary"
            className="!h-9 !rounded-lg !px-4 !text-white"
            onClick={() => {
              openCreate();
            }}
          >
            新建业务过程
          </YakButton>
        </Space>
      </div>

      {focusedProcess ? (
        <div className="mt-4 flex flex-wrap items-center gap-3 rounded-lg border border-[#d0d5dd] bg-[#f9fafb] px-4 py-3">
          <div className="min-w-0 flex-1">
            <div className="text-[14px] font-semibold text-[#344054]">
              当前业务过程：{focusedProcess.name}（{focusedProcess.code}）
            </div>
            <div className="mt-0.5 text-[13px] text-[#667085]">
              稳定 ID #{focusedProcess.id} · 业务域 {domainNameOf(focusedProcess.domainId)}
            </div>
          </div>
          <YakButton onClick={() => openMetrics(focusedProcess)}>查看相关指标</YakButton>
        </div>
      ) : null}

      <Table<SemanticProcessRecord>
        className="mt-4"
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={records}
        rowClassName={(record) => (record.id === focusProcessId ? 'bg-[#eff8ff]' : '')}
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title={keyword || domainId || bizType ? '没有符合筛选条件的业务过程' : '还没有业务过程'}
              description={
                keyword || domainId || bizType
                  ? '调整筛选条件或重置后再试'
                  : '先在「业务域」页搭建域树，再在此创建业务过程'
              }
            />
          ),
        }}
        pagination={{
          current: pageNo,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (page, size) => {
            setPageNo(page);
            setPageSize(size);
          },
        }}
      />

      <Modal
        open={editorOpen}
        title="新建业务过程"
        width={600}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnClose
        onCancel={() => setEditorOpen(false)}
        onOk={() => {
          void submitEditor();
        }}
      >
        <Form form={form} layout="vertical" className="pt-2">
          {!editing ? (
            <Form.Item
              name="code"
              label="编码"
              rules={[
                { required: true, message: '请输入编码' },
                {
                  pattern: /^[A-Za-z0-9_]{1,64}$/,
                  message: '仅允许字母、数字和下划线，1~64 位',
                },
              ]}
            >
              <Input maxLength={64} placeholder="如 place_order" />
            </Form.Item>
          ) : null}
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input maxLength={128} placeholder="业务过程名称" />
            </Form.Item>
            <Form.Item name="domainId" label="所属业务域" rules={[{ required: true, message: '请选择业务域' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择业务域"
                options={domainOptions.map((item) => ({ label: item.path, value: item.id }))}
              />
            </Form.Item>
            <Form.Item name="grain" label="粒度">
              <Input maxLength={64} placeholder="如 单据/明细/天" />
            </Form.Item>
            <Form.Item name="bizType" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
              <Select
                options={[
                  { label: '事实（FACT）', value: 'FACT' },
                  { label: '维度（DIMENSION）', value: 'DIMENSION' },
                ]}
              />
            </Form.Item>
            <Form.Item name="owner" label="负责人">
              <Input maxLength={64} placeholder="可选" />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序">
              <Input type="number" placeholder="0" />
            </Form.Item>
          </div>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} maxLength={512} placeholder="业务过程说明（可选）" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default SemanticProcessesPage;
