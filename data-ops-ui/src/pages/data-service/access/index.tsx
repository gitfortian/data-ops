import { YakButton, YakEmpty } from '@/components/ui';
import {
  createDataServiceConsumer,
  deleteDataServiceConsumer,
  getDataServiceConsumer,
  listDataServiceAccessOverview,
  listDataServiceConsumers,
  updateDataServiceConsumer,
  type DataServiceAccessOverviewItem,
  type DataServiceConsumer,
} from '@/services/data-service';
import {
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Switch,
  Table,
  message,
  type TableColumnsType,
} from 'antd';
import {
  Plus,
  RefreshCw,
  Search,
  Settings2,
} from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

import ConsumerManagementPanel from './ConsumerManagementPanel';

type StatusFilter = 'ALL' | 'ENABLED' | 'DISABLED';

interface ConsumerFormValues {
  name: string;
  description?: string;
  enabled: boolean;
  defaultRateLimitPerMinute: number;
}

const formatTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '-';

const ipModeLabel = {
  NONE: '不限制',
  ALLOWLIST: '白名单',
  DENYLIST: '黑名单',
} as const;

const Metric = ({ label, value }: { label: string; value: number }) => (
  <div className="min-w-0 px-4 py-3">
    <div className="text-[11px] text-[#98a2b3]">{label}</div>
    <div className="mt-1 text-[20px] font-semibold tracking-[-.02em] text-[#161823]">
      {value}
    </div>
  </div>
);

export default function DataServiceAccessPage() {
  const [form] = Form.useForm<ConsumerFormValues>();
  const [records, setRecords] = useState<DataServiceConsumer[]>([]);
  const [apis, setApis] = useState<DataServiceAccessOverviewItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [selected, setSelected] = useState<DataServiceConsumer>();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DataServiceConsumer>();
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [nextConsumers, nextApis] = await Promise.all([
        listDataServiceConsumers(),
        listDataServiceAccessOverview(),
      ]);
      setRecords(nextConsumers || []);
      setApis(nextApis || []);
    } catch (error: any) {
      message.error(error?.message || '加载 API 调用配置失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const refreshConsumer = useCallback(async (consumerId: number) => {
    try {
      const next = await getDataServiceConsumer(consumerId);
      setRecords((current) => current.map((item) => item.id === next.id ? next : item));
      setSelected((current) => current?.id === next.id ? next : current);
    } catch {
      void load();
    }
  }, [load]);

  const metrics = useMemo(() => ({
    total: records.length,
    credentials: records.reduce((sum, item) => sum + item.activeKeyCount, 0),
    allAccess: records.filter((item) => item.accessScope === 'ALL').length,
    network: records.filter((item) => item.ipAccessMode !== 'NONE').length,
  }), [records]);

  const filtered = useMemo(() => {
    const text = keyword.trim().toLowerCase();
    return records.filter((item) => {
      if (statusFilter === 'ENABLED' && !item.enabled) return false;
      if (statusFilter === 'DISABLED' && item.enabled) return false;
      if (!text) return true;
      return item.name.toLowerCase().includes(text)
        || (item.description || '').toLowerCase().includes(text);
    });
  }, [keyword, records, statusFilter]);

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    form.setFieldsValue({ enabled: true, defaultRateLimitPerMinute: 60 });
    setFormOpen(true);
  };

  const openEdit = (consumer: DataServiceConsumer) => {
    setEditing(consumer);
    form.setFieldsValue({
      name: consumer.name,
      description: consumer.description || undefined,
      enabled: consumer.enabled,
      defaultRateLimitPerMinute: consumer.defaultRateLimitPerMinute,
    });
    setFormOpen(true);
  };

  const closeForm = () => {
    if (saving) return;
    setFormOpen(false);
    setEditing(undefined);
    form.resetFields();
  };

  const submitConsumer = async (values: ConsumerFormValues) => {
    setSaving(true);
    try {
      const payload = {
        name: values.name.trim(),
        description: values.description?.trim() || null,
        enabled: values.enabled,
        defaultRateLimitPerMinute: values.defaultRateLimitPerMinute,
      };

      if (editing) {
        const next = await updateDataServiceConsumer(editing.id, payload);
        setRecords((current) => current.map((item) => item.id === next.id ? next : item));
        setSelected((current) => current?.id === next.id ? next : current);
        message.success('调用方已更新');
      } else {
        const next = await createDataServiceConsumer(payload);
        setRecords((current) => [next, ...current]);
        setSelected(next);
        message.success('调用方已创建');
      }

      setFormOpen(false);
      setEditing(undefined);
      form.resetFields();
    } catch (error: any) {
      message.error(error?.message || (editing ? '更新调用方失败' : '创建调用方失败'));
    } finally {
      setSaving(false);
    }
  };

  const removeConsumer = (consumer: DataServiceConsumer) => {
    Modal.confirm({
      title: '删除调用方',
      content: `确认删除「${consumer.name}」？其 API Key 和来源规则会同时失效。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteDataServiceConsumer(consumer.id);
          setRecords((current) => current.filter((item) => item.id !== consumer.id));
          if (selected?.id === consumer.id) setSelected(undefined);
          message.success('调用方已删除');
        } catch (error: any) {
          message.error(error?.message || '删除调用方失败');
        }
      },
    });
  };

  const updateSelected = (next: DataServiceConsumer) => {
    setSelected(next);
    setRecords((current) => current.map((item) => item.id === next.id ? next : item));
  };

  const columns: TableColumnsType<DataServiceConsumer> = [
    {
      title: '调用方',
      key: 'consumer',
      minWidth: 220,
      render: (_, record) => (
        <div className="min-w-0 py-0.5">
          <div className="truncate text-[12px] font-medium text-[#344054]">{record.name}</div>
          {record.description ? (
            <div className="mt-0.5 truncate text-[10px] text-[#98a2b3]">{record.description}</div>
          ) : null}
        </div>
      ),
    },
    {
      title: 'API 权限',
      key: 'apis',
      width: 150,
      render: (_, record) => (
        <div className="text-[11px] font-medium text-[#475467]">
          {record.accessScope === 'ALL' ? '所有 API' : `${record.apiCount} 个 API`}
        </div>
      ),
    },
    {
      title: '凭证',
      key: 'keys',
      width: 150,
      render: (_, record) => (
        <div>
          <div className="text-[11px] font-medium text-[#475467]">
            {record.activeKeyCount}/{record.keyCount} 个有效 Key
          </div>
          <div className="mt-0.5 text-[10px] text-[#98a2b3]">
            默认 {record.defaultRateLimitPerMinute}/min
          </div>
        </div>
      ),
    },
    {
      title: '来源限制',
      key: 'ip',
      width: 140,
      render: (_, record) => (
        <div>
          <div className="text-[11px] font-medium text-[#475467]">
            {ipModeLabel[record.ipAccessMode]}
          </div>
          {record.ipRuleCount > 0 ? (
            <div className="mt-0.5 text-[10px] text-[#98a2b3]">{record.ipRuleCount} 条规则</div>
          ) : null}
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 92,
      render: (enabled: boolean) => (
        <span className="inline-flex items-center gap-1.5 text-[11px] text-[#667085]">
          <span className={`h-1.5 w-1.5 rounded-full ${enabled ? 'bg-[#20c77a]' : 'bg-[#b0b5bd]'}`} />
          {enabled ? '可调用' : '已停用'}
        </span>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 150,
      render: (value?: string | null) => (
        <span className="text-[10px] text-[#98a2b3]">{formatTime(value)}</span>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 96,
      fixed: 'right',
      render: (_, record) => (
        <YakButton
          type="text"
          size="small"
          icon={<Settings2 size={13} />}
          onClick={() => setSelected(record)}
        >
          管理
        </YakButton>
      ),
    },
  ];

  return (
    <div className="h-full overflow-y-auto bg-[#f6f7f8] p-3">
      <div className="min-h-full rounded-[10px] bg-white px-5 pb-5 pt-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="m-0 text-[17px] font-semibold text-[#161823]">API 调用</h1>
          <div className="flex gap-2">
            <YakButton
              type="text"
              icon={<RefreshCw size={14} />}
              loading={loading}
              onClick={() => void load()}
              className="bg-[#f5f6f7]"
            >
              刷新
            </YakButton>
            <YakButton icon={<Plus size={14} />} onClick={openCreate}>
              新建调用方
            </YakButton>
          </div>
        </div>

        <div className="my-4 border-t border-[#f0f0f0]" />

        <div className="grid grid-cols-2 divide-x divide-[#f0f0f0] rounded-[8px] bg-[#f8f9fa] md:grid-cols-4">
          <Metric label="调用方" value={metrics.total} />
          <Metric label="有效凭证" value={metrics.credentials} />
          <Metric label="全部 API" value={metrics.allAccess} />
          <Metric label="来源限制" value={metrics.network} />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            allowClear
            variant="filled"
            prefix={<Search size={13} className="text-[#98a2b3]" />}
            placeholder="搜索调用方"
            className="w-[300px]"
          />
          <Select<StatusFilter>
            value={statusFilter}
            onChange={setStatusFilter}
            variant="filled"
            className="w-[140px]"
            options={[
              { value: 'ALL', label: '全部状态' },
              { value: 'ENABLED', label: '可调用' },
              { value: 'DISABLED', label: '已停用' },
            ]}
          />
          <div className="ml-auto text-[11px] text-[#98a2b3]">共 {filtered.length} 个调用方</div>
        </div>

        <div className="mt-3">
          <Table<DataServiceConsumer>
            rowKey="id"
            size="small"
            loading={loading}
            columns={columns}
            dataSource={filtered}
            scroll={{ x: 1050 }}
            pagination={{
              pageSize: 10,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50],
              showTotal: (total) => `共 ${total} 条`,
            }}
            locale={{ emptyText: <YakEmpty compact title="暂无调用方" /> }}
            className="[&_.ant-table-container]:!border-t [&_.ant-table-container]:!border-[#f0f0f0] [&_.ant-table-thead>tr>th]:!bg-[#fafafa] [&_.ant-table-thead>tr>th]:!text-[11px] [&_.ant-table-tbody>tr>td]:!py-3"
          />
        </div>
      </div>

      <Drawer
        open={Boolean(selected)}
        width={1100}
        onClose={() => setSelected(undefined)}
        title={selected ? (
          <div className="truncate text-[14px] font-semibold text-[#161823]">{selected.name}</div>
        ) : 'API 调用'}
        styles={{
          body: {
            padding: 0,
            overflow: 'hidden',
            background: '#f7f8fa',
          },
        }}
      >
        {selected ? (
          <ConsumerManagementPanel
            consumer={selected}
            apis={apis}
            onEdit={() => openEdit(selected)}
            onDelete={() => removeConsumer(selected)}
            onRefresh={() => void refreshConsumer(selected.id)}
            onConsumerChanged={updateSelected}
          />
        ) : null}
      </Drawer>

      <Modal
        title={editing ? '编辑调用方' : '新建调用方'}
        open={formOpen}
        onCancel={closeForm}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        width={520}
        destroyOnClose
      >
        <Form<ConsumerFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => void submitConsumer(values)}
        >
          <Form.Item
            name="name"
            label="调用方名称"
            rules={[{ required: true, message: '请输入调用方名称' }]}
          >
            <Input variant="filled" placeholder="请输入调用方名称" maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input.TextArea variant="filled" rows={3} placeholder="请输入说明" maxLength={500} />
          </Form.Item>
          <Form.Item
            name="defaultRateLimitPerMinute"
            label="默认限流"
            rules={[{ required: true, message: '请输入调用上限' }]}
          >
            <InputNumber
              variant="filled"
              min={1}
              max={100000}
              addonAfter="次 / 分钟"
              className="w-full"
            />
          </Form.Item>
          <Form.Item name="enabled" label="状态" valuePropName="checked">
            <Switch checkedChildren="可调用" unCheckedChildren="停用" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
