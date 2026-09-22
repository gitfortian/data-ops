import { YakButton, YakEmpty, YakTab } from '@/components/ui';
import {
  createDataServiceConsumerIpAccessRule,
  deleteDataServiceConsumerIpAccessRule,
  getDataServiceConsumerIpAccess,
  setDataServiceConsumerIpAccessMode,
  updateDataServiceConsumerIpAccessRule,
  type DataServiceConsumer,
  type DataServiceConsumerIpAccessRule,
  type DataServiceIpAccessMode,
  type DataServiceIpAccessRuleType,
} from '@/services/data-service';
import { DatePicker, Form, Input, Modal, Spin, Switch, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

interface ConsumerIpAccessPanelProps {
  consumer: DataServiceConsumer;
  onChanged: () => void;
}

interface RuleFormValues {
  networkCidr: string;
  description?: string;
  expiresAt?: Dayjs | null;
  enabled: boolean;
}

const MODES: Array<{ key: DataServiceIpAccessMode; title: string }> = [
  { key: 'NONE', title: '不限制' },
  { key: 'ALLOWLIST', title: '白名单' },
  { key: 'DENYLIST', title: '黑名单' },
];

const formatTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '永久';

const expired = (rule: DataServiceConsumerIpAccessRule) =>
  Boolean(rule.expiresAt && dayjs(rule.expiresAt).isBefore(dayjs()));

const status = (rule: DataServiceConsumerIpAccessRule) => {
  if (!rule.enabled) return '已停用';
  if (expired(rule)) return '已过期';
  return '生效中';
};

export default function ConsumerIpAccessPanel({
  consumer,
  onChanged,
}: ConsumerIpAccessPanelProps) {
  const [form] = Form.useForm<RuleFormValues>();
  const [loading, setLoading] = useState(true);
  const [mode, setMode] = useState<DataServiceIpAccessMode>('NONE');
  const [rules, setRules] = useState<DataServiceConsumerIpAccessRule[]>([]);
  const [activeList, setActiveList] = useState<DataServiceIpAccessRuleType>('ALLOWLIST');
  const [modeSaving, setModeSaving] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DataServiceConsumerIpAccessRule>();
  const [saving, setSaving] = useState(false);
  const [busyId, setBusyId] = useState<number>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const policy = await getDataServiceConsumerIpAccess(consumer.id);
      setMode(policy.mode);
      setRules(policy.rules || []);
      if (policy.mode !== 'NONE') setActiveList(policy.mode);
    } catch (error: any) {
      message.error(error?.message || '加载来源访问策略失败');
    } finally {
      setLoading(false);
    }
  }, [consumer.id]);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleRules = useMemo(
    () => rules.filter((rule) => rule.ruleType === activeList),
    [activeList, rules],
  );

  const activeAllow = useMemo(
    () => rules.filter((rule) => rule.ruleType === 'ALLOWLIST' && rule.enabled && !expired(rule)).length,
    [rules],
  );

  const saveMode = async (nextMode: DataServiceIpAccessMode) => {
    if (nextMode === mode || modeSaving) return;
    setModeSaving(true);
    try {
      const policy = await setDataServiceConsumerIpAccessMode(consumer.id, nextMode);
      setMode(policy.mode);
      setRules(policy.rules || []);
      if (nextMode !== 'NONE') setActiveList(nextMode);
      message.success('来源访问策略已更新');
      onChanged();
    } catch (error: any) {
      message.error(error?.message || '更新来源访问策略失败');
    } finally {
      setModeSaving(false);
    }
  };

  const changeMode = (nextMode: DataServiceIpAccessMode) => {
    if (nextMode === 'ALLOWLIST' && activeAllow === 0) {
      Modal.confirm({
        title: '启用空白名单？',
        content: '当前没有生效中的白名单规则。启用后，该调用方的请求都会被拒绝。',
        okText: '仍然启用',
        cancelText: '取消',
        onOk: () => saveMode(nextMode),
      });
      return;
    }
    void saveMode(nextMode);
  };

  const openCreate = () => {
    setEditing(undefined);
    form.resetFields();
    form.setFieldsValue({ enabled: true });
    setModalOpen(true);
  };

  const openEdit = (rule: DataServiceConsumerIpAccessRule) => {
    setEditing(rule);
    setActiveList(rule.ruleType);
    form.setFieldsValue({
      networkCidr: rule.networkCidr,
      description: rule.description || undefined,
      expiresAt: rule.expiresAt ? dayjs(rule.expiresAt) : null,
      enabled: rule.enabled,
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    if (saving) return;
    setModalOpen(false);
    setEditing(undefined);
    form.resetFields();
  };

  const submit = async (values: RuleFormValues) => {
    setSaving(true);
    try {
      const payload = {
        ruleType: editing?.ruleType || activeList,
        networkCidr: values.networkCidr.trim(),
        description: values.description?.trim() || null,
        enabled: values.enabled,
        expiresAt: values.expiresAt
          ? values.expiresAt.format('YYYY-MM-DDTHH:mm:ss')
          : null,
      };

      if (editing) {
        await updateDataServiceConsumerIpAccessRule(consumer.id, editing.id, payload);
        message.success('访问规则已更新');
      } else {
        await createDataServiceConsumerIpAccessRule(consumer.id, payload);
        message.success('访问规则已添加');
      }

      closeModal();
      await load();
      onChanged();
    } catch (error: any) {
      message.error(error?.message || '保存访问规则失败');
    } finally {
      setSaving(false);
    }
  };

  const toggle = async (rule: DataServiceConsumerIpAccessRule, enabled: boolean) => {
    setBusyId(rule.id);
    try {
      await updateDataServiceConsumerIpAccessRule(consumer.id, rule.id, {
        ruleType: rule.ruleType,
        networkCidr: rule.networkCidr,
        description: rule.description || null,
        enabled,
        expiresAt: rule.expiresAt || null,
      });
      message.success(enabled ? '规则已启用' : '规则已停用');
      await load();
      onChanged();
    } catch (error: any) {
      message.error(error?.message || '更新规则状态失败');
    } finally {
      setBusyId(undefined);
    }
  };

  const remove = (rule: DataServiceConsumerIpAccessRule) => {
    Modal.confirm({
      title: '删除访问规则',
      content: `确认删除 ${rule.networkCidr}？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setBusyId(rule.id);
        try {
          await deleteDataServiceConsumerIpAccessRule(consumer.id, rule.id);
          message.success('访问规则已删除');
          await load();
          onChanged();
        } catch (error: any) {
          message.error(error?.message || '删除访问规则失败');
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

  const allowCount = rules.filter((rule) => rule.ruleType === 'ALLOWLIST').length;
  const denyCount = rules.filter((rule) => rule.ruleType === 'DENYLIST').length;

  return (
    <section className="rounded-xl bg-white">
      <div className="px-7 pt-5">
        <h2 className="m-0 text-[17px] font-semibold leading-6 text-[#161823]">
          来源限制
        </h2>
      </div>

      <div className="px-7 py-6">
        <div className="grid gap-2 md:grid-cols-3">
          {MODES.map((item) => (
            <button
              key={item.key}
              type="button"
              disabled={modeSaving}
              onClick={() => changeMode(item.key)}
              className={[
                'rounded-lg border border-solid px-4 py-3 text-left transition-colors',
                mode === item.key
                  ? 'border-[#161823] bg-[#f7f7f8]'
                  : 'border-[#eceef1] bg-white hover:bg-[#fafafa]',
              ].join(' ')}
            >
              <div className="flex items-center gap-2 text-[13px] font-medium text-[#161823]">
                <span
                  className={[
                    'h-2 w-2 rounded-full',
                    mode === item.key ? 'bg-[#161823]' : 'bg-[#d0d5dd]',
                  ].join(' ')}
                />
                {item.title}
              </div>
            </button>
          ))}
        </div>

        <div className="mt-6 flex items-center justify-between gap-4">
          <YakTab
            activeKey={activeList}
            onChange={(key) => setActiveList(key as DataServiceIpAccessRuleType)}
            items={[
              { key: 'ALLOWLIST', label: `白名单 ${allowCount}` },
              { key: 'DENYLIST', label: `黑名单 ${denyCount}` },
            ]}
          />
          <YakButton icon={<Plus size={14} />} onClick={openCreate}>添加规则</YakButton>
        </div>

        <div className="mt-3">
          {visibleRules.length ? (
            <div className="overflow-hidden rounded-lg border border-solid border-[#eceef1]">
              {visibleRules.map((rule, index) => (
                <div
                  key={rule.id}
                  className={[
                    'grid gap-3 px-4 py-3 md:grid-cols-[minmax(180px,1fr)_minmax(160px,1fr)_140px_84px_104px] md:items-center',
                    index ? 'border-t border-solid border-[#eceef1]' : '',
                  ].join(' ')}
                >
                  <div className="min-w-0">
                    <div className="truncate font-mono text-[12px] font-medium text-[#161823]">{rule.networkCidr}</div>
                    <div className="mt-1 text-[10px] text-[#98a2b3]">{status(rule)}</div>
                  </div>
                  <div className="truncate text-[12px] text-[#667085]">{rule.description || '—'}</div>
                  <div className="text-[11px] text-[#667085]">{formatTime(rule.expiresAt)}</div>
                  <Switch
                    size="small"
                    checked={rule.enabled}
                    loading={busyId === rule.id}
                    onChange={(checked) => void toggle(rule, checked)}
                  />
                  <div className="flex justify-end gap-1">
                    <YakButton type="text" iconOnly icon={<Pencil size={14} />} onClick={() => openEdit(rule)} />
                    <YakButton
                      type="text"
                      danger
                      iconOnly
                      icon={<Trash2 size={14} />}
                      loading={busyId === rule.id}
                      onClick={() => remove(rule)}
                    />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <YakEmpty compact title={activeList === 'ALLOWLIST' ? '暂无白名单规则' : '暂无黑名单规则'} />
          )}
        </div>
      </div>

      <Modal
        title={editing ? '编辑访问规则' : `添加${activeList === 'ALLOWLIST' ? '白名单' : '黑名单'}规则`}
        open={modalOpen}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={saving}
        okText={editing ? '保存' : '添加'}
        cancelText="取消"
        destroyOnClose
      >
        <Form<RuleFormValues> form={form} layout="vertical" onFinish={(values) => void submit(values)}>
          <Form.Item name="networkCidr" label="IP / CIDR" rules={[{ required: true, message: '请输入 IP 或 CIDR' }]}>
            <Input variant="filled" placeholder="例如 10.20.0.0/16 或 203.0.113.8" />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input variant="filled" placeholder="请输入说明" maxLength={255} />
          </Form.Item>
          <Form.Item name="expiresAt" label="有效期">
            <DatePicker showTime variant="filled" className="w-full" placeholder="永久有效" />
          </Form.Item>
          <Form.Item name="enabled" label="状态" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  );
}
