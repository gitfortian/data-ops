import { YakButton, YakEmpty } from '@/components/ui';
import {
  createDataServiceConsumerKey,
  deleteDataServiceConsumerKey,
  listDataServiceConsumerKeys,
  rotateDataServiceConsumerKey,
  setDataServiceConsumerKeyEnabled,
  updateDataServiceConsumerKey,
  type DataServiceConsumer,
  type DataServiceConsumerKey,
} from '@/services/data-service';
import { DatePicker, Form, Input, InputNumber, Modal, Spin, Switch, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Copy, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

interface ConsumerKeyPanelProps {
  consumer: DataServiceConsumer;
  onChanged: () => void;
}

interface KeyFormValues {
  name: string;
  rateLimitPerMinute: number;
  expiresAt?: Dayjs | null;
}

interface SecretView {
  title: string;
  name: string;
  secret: string;
}

const formatTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '永久';

const expired = (key: DataServiceConsumerKey) =>
  Boolean(key.expiresAt && dayjs(key.expiresAt).isBefore(dayjs()));

const status = (key: DataServiceConsumerKey) => {
  if (!key.enabled) return { label: '已停用', dot: 'bg-[#b0b5bd]' };
  if (expired(key)) return { label: '已过期', dot: 'bg-[#b0b5bd]' };
  return { label: '可用', dot: 'bg-[#20c77a]' };
};

const copyText = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value);
    message.success('API Key 已复制');
  } catch {
    message.error('复制失败，请手动复制');
  }
};

export default function ConsumerKeyPanel({ consumer, onChanged }: ConsumerKeyPanelProps) {
  const [form] = Form.useForm<KeyFormValues>();
  const [keys, setKeys] = useState<DataServiceConsumerKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DataServiceConsumerKey>();
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<number>();
  const [secretView, setSecretView] = useState<SecretView>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setKeys((await listDataServiceConsumerKeys(consumer.id)) || []);
    } catch (error: any) {
      message.error(error?.message || '加载 API Key 失败');
    } finally {
      setLoading(false);
    }
  }, [consumer.id]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    form.setFieldsValue({ rateLimitPerMinute: consumer.defaultRateLimitPerMinute });
    setModalOpen(true);
  };

  const openEdit = (key: DataServiceConsumerKey) => {
    setEditing(key);
    form.setFieldsValue({
      name: key.name,
      rateLimitPerMinute: key.rateLimitPerMinute,
      expiresAt: key.expiresAt ? dayjs(key.expiresAt) : null,
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    if (saving) return;
    setModalOpen(false);
    setEditing(undefined);
    form.resetFields();
  };

  const submit = async (values: KeyFormValues) => {
    setSaving(true);
    try {
      const expiresAt = values.expiresAt
        ? values.expiresAt.format('YYYY-MM-DDTHH:mm:ss')
        : null;

      if (editing) {
        await updateDataServiceConsumerKey(consumer.id, editing.id, {
          name: values.name.trim(),
          rateLimitPerMinute: values.rateLimitPerMinute,
          expiresAt,
          expiresAtSet: true,
        });
        message.success('API Key 已更新');
      } else {
        const created = await createDataServiceConsumerKey(consumer.id, {
          name: values.name.trim(),
          rateLimitPerMinute: values.rateLimitPerMinute,
          expiresAt,
        });
        setSecretView({
          title: 'API Key 创建成功',
          name: created.key.name,
          secret: created.secret,
        });
        message.success('API Key 已创建');
      }

      closeModal();
      await load();
      onChanged();
    } catch (error: any) {
      message.error(error?.message || (editing ? '更新 API Key 失败' : '创建 API Key 失败'));
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (key: DataServiceConsumerKey, enabled: boolean) => {
    setBusyId(key.id);
    try {
      await setDataServiceConsumerKeyEnabled(consumer.id, key.id, enabled);
      message.success(enabled ? 'API Key 已启用' : 'API Key 已停用');
      await load();
      onChanged();
    } catch (error: any) {
      message.error(error?.message || '更新 API Key 状态失败');
    } finally {
      setBusyId(undefined);
    }
  };

  const rotate = (key: DataServiceConsumerKey) => {
    Modal.confirm({
      title: '轮换 API Key',
      content: '轮换后旧密钥立即失效，新密钥只展示一次。',
      okText: '确认轮换',
      cancelText: '取消',
      onOk: async () => {
        setBusyId(key.id);
        try {
          const rotated = await rotateDataServiceConsumerKey(consumer.id, key.id);
          setSecretView({
            title: 'API Key 已轮换',
            name: rotated.key.name,
            secret: rotated.secret,
          });
          message.success('API Key 已轮换');
          await load();
          onChanged();
        } catch (error: any) {
          message.error(error?.message || '轮换 API Key 失败');
        } finally {
          setBusyId(undefined);
        }
      },
    });
  };

  const remove = (key: DataServiceConsumerKey) => {
    Modal.confirm({
      title: '删除 API Key',
      content: `确认删除「${key.name}」？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setBusyId(key.id);
        try {
          await deleteDataServiceConsumerKey(consumer.id, key.id);
          message.success('API Key 已删除');
          await load();
          onChanged();
        } catch (error: any) {
          message.error(error?.message || '删除 API Key 失败');
        } finally {
          setBusyId(undefined);
        }
      },
    });
  };

  if (loading) {
    return (
      <div className="flex min-h-[260px] items-center justify-center rounded-xl bg-white">
        <Spin />
      </div>
    );
  }

  return (
    <section className="rounded-xl bg-white">
      <div className="flex items-center justify-between gap-4 px-7 pt-5">
        <h2 className="m-0 text-[17px] font-semibold leading-6 text-[#161823]">
          API Key
        </h2>
        <div className="flex gap-1.5">
          <YakButton type="text" iconOnly icon={<RefreshCw size={14} />} onClick={() => void load()} />
          <YakButton icon={<Plus size={14} />} onClick={openCreate}>创建 Key</YakButton>
        </div>
      </div>

      <div className="px-7 py-6">
        {keys.length ? (
          <div className="overflow-hidden rounded-lg border border-solid border-[#eceef1]">
            {keys.map((key, index) => {
              const currentStatus = status(key);
              return (
                <div
                  key={key.id}
                  className={[
                    'grid gap-3 px-4 py-3 md:grid-cols-[minmax(180px,1.3fr)_120px_100px_150px_70px_128px] md:items-center',
                    index ? 'border-t border-solid border-[#eceef1]' : '',
                  ].join(' ')}
                >
                  <div className="min-w-0">
                    <div className="truncate text-[12px] font-medium text-[#161823]">{key.name}</div>
                    <div className="mt-1 font-mono text-[10px] text-[#98a2b3]">{key.keyPrefix}••••••••</div>
                  </div>
                  <div className="text-[11px] text-[#667085]">{key.rateLimitPerMinute}/min</div>
                  <div className="flex items-center gap-1.5 text-[11px] text-[#667085]">
                    <span className={`h-1.5 w-1.5 rounded-full ${currentStatus.dot}`} />
                    {currentStatus.label}
                  </div>
                  <div className="text-[10px] text-[#667085]">{formatTime(key.lastUsedAt)}</div>
                  <Switch
                    size="small"
                    checked={key.enabled}
                    loading={busyId === key.id}
                    onChange={(checked) => void toggle(key, checked)}
                  />
                  <div className="flex justify-end gap-1">
                    <YakButton type="text" iconOnly icon={<Pencil size={14} />} onClick={() => openEdit(key)} />
                    <YakButton type="text" iconOnly icon={<RefreshCw size={14} />} loading={busyId === key.id} onClick={() => rotate(key)} />
                    <YakButton type="text" danger iconOnly icon={<Trash2 size={14} />} loading={busyId === key.id} onClick={() => remove(key)} />
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <YakEmpty compact title="暂无 API Key" />
        )}
      </div>

      <Modal
        title={editing ? '编辑 API Key' : '创建 API Key'}
        open={modalOpen}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText={editing ? '保存' : '创建'}
        cancelText="取消"
        destroyOnClose
      >
        <Form<KeyFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Form.Item name="name" label="Key 名称" rules={[{ required: true, message: '请输入 Key 名称' }]}>
            <Input variant="filled" placeholder="请输入 Key 名称" maxLength={128} />
          </Form.Item>
          <Form.Item name="rateLimitPerMinute" label="每分钟调用上限" rules={[{ required: true, message: '请输入调用上限' }]}>
            <InputNumber variant="filled" min={1} max={100000} className="w-full" />
          </Form.Item>
          <Form.Item name="expiresAt" label="过期时间">
            <DatePicker showTime variant="filled" className="w-full" placeholder="永久有效" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={secretView?.title}
        open={Boolean(secretView)}
        onCancel={() => setSecretView(undefined)}
        footer={<YakButton onClick={() => setSecretView(undefined)}>我已保存</YakButton>}
      >
        {secretView ? (
          <div>
            <div className="mb-3 text-[12px] text-[#667085]">
              「{secretView.name}」的密钥只展示这一次。
            </div>
            <div className="flex items-center gap-2 rounded-lg bg-[#f6f6f7] p-3">
              <code className="min-w-0 flex-1 break-all text-[12px] text-[#30343b]">
                {secretView.secret}
              </code>
              <YakButton type="text" iconOnly icon={<Copy size={14} />} onClick={() => void copyText(secretView.secret)} />
            </div>
          </div>
        ) : null}
      </Modal>
    </section>
  );
}
