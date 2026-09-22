import { YakButton, YakEmpty, YakTab } from '@/components/ui';
import {
  createDataServiceIpAccessRule,
  deleteDataServiceIpAccessRule,
  getDataServiceIpAccess,
  setDataServiceIpAccessMode,
  updateDataServiceIpAccessRule,
  type DataServiceIpAccessMode,
  type DataServiceIpAccessRule,
  type DataServiceIpAccessRuleType,
} from '@/services/data-service';
import { DatePicker, Form, Input, Modal, Spin, Switch, message } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { Pencil, Plus, Shield, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';

interface DataServiceAccessControlPanelProps {
  apiId: number;
}

interface RuleFormValues {
  networkCidr: string;
  description?: string;
  expiresAt?: Dayjs | null;
  enabled: boolean;
}

const MODE_OPTIONS: Array<{
  key: DataServiceIpAccessMode;
  title: string;
  description: string;
}> = [
  { key: 'NONE', title: '不限制', description: '不按来源 IP 限制调用' },
  { key: 'ALLOWLIST', title: '白名单', description: '仅允许白名单中的 IP/CIDR' },
  { key: 'DENYLIST', title: '黑名单', description: '拒绝黑名单中的 IP/CIDR' },
];

const formatTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '永久';

const isExpired = (rule: DataServiceIpAccessRule) =>
  Boolean(rule.expiresAt && dayjs(rule.expiresAt).isBefore(dayjs()));

const ruleStatus = (rule: DataServiceIpAccessRule) => {
  if (!rule.enabled) return '已停用';
  if (isExpired(rule)) return '已过期';
  return '生效中';
};

export default function DataServiceAccessControlPanel({
  apiId,
}: DataServiceAccessControlPanelProps) {
  const [form] = Form.useForm<RuleFormValues>();
  const [loading, setLoading] = useState(true);
  const [mode, setMode] = useState<DataServiceIpAccessMode>('NONE');
  const [rules, setRules] = useState<DataServiceIpAccessRule[]>([]);
  const [activeList, setActiveList] = useState<DataServiceIpAccessRuleType>('ALLOWLIST');
  const [modeSaving, setModeSaving] = useState(false);
  const [ruleModalOpen, setRuleModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<DataServiceIpAccessRule>();
  const [ruleSaving, setRuleSaving] = useState(false);
  const [busyRuleId, setBusyRuleId] = useState<number>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const policy = await getDataServiceIpAccess(apiId);
      setMode(policy.mode);
      setRules(policy.rules || []);
      if (policy.mode !== 'NONE') setActiveList(policy.mode);
    } catch (error: any) {
      message.error(error?.message || '加载访问控制策略失败');
    } finally {
      setLoading(false);
    }
  }, [apiId]);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleRules = useMemo(
    () => rules.filter((item) => item.ruleType === activeList),
    [activeList, rules],
  );

  const activeAllowRules = useMemo(
    () => rules.filter(
      (item) => item.ruleType === 'ALLOWLIST' && item.enabled && !isExpired(item),
    ).length,
    [rules],
  );

  const saveMode = async (nextMode: DataServiceIpAccessMode) => {
    if (nextMode === mode || modeSaving) return;
    setModeSaving(true);
    try {
      const policy = await setDataServiceIpAccessMode(apiId, nextMode);
      setMode(policy.mode);
      setRules(policy.rules || []);
      if (nextMode !== 'NONE') setActiveList(nextMode);
      message.success('来源访问策略已更新');
    } catch (error: any) {
      message.error(error?.message || '更新来源访问策略失败');
    } finally {
      setModeSaving(false);
    }
  };

  const handleModeChange = (nextMode: DataServiceIpAccessMode) => {
    if (nextMode === 'ALLOWLIST' && activeAllowRules === 0) {
      Modal.confirm({
        title: '启用空白名单？',
        content: '当前没有生效中的白名单规则。启用后，所有外部来源都会被拒绝，直到添加可用规则。',
        okText: '仍然启用',
        cancelText: '取消',
        onOk: () => saveMode(nextMode),
      });
      return;
    }
    void saveMode(nextMode);
  };

  const openCreate = () => {
    setEditingRule(undefined);
    form.resetFields();
    form.setFieldsValue({ enabled: true });
    setRuleModalOpen(true);
  };

  const openEdit = (rule: DataServiceIpAccessRule) => {
    setEditingRule(rule);
    setActiveList(rule.ruleType);
    form.setFieldsValue({
      networkCidr: rule.networkCidr,
      description: rule.description || undefined,
      expiresAt: rule.expiresAt ? dayjs(rule.expiresAt) : null,
      enabled: rule.enabled,
    });
    setRuleModalOpen(true);
  };

  const closeModal = () => {
    if (ruleSaving) return;
    setRuleModalOpen(false);
    setEditingRule(undefined);
    form.resetFields();
  };

  const submitRule = async (values: RuleFormValues) => {
    setRuleSaving(true);
    try {
      const payload = {
        ruleType: editingRule?.ruleType || activeList,
        networkCidr: values.networkCidr.trim(),
        description: values.description?.trim() || null,
        enabled: values.enabled,
        expiresAt: values.expiresAt
          ? values.expiresAt.format('YYYY-MM-DDTHH:mm:ss')
          : null,
      };
      if (editingRule) {
        const updated = await updateDataServiceIpAccessRule(
          apiId,
          editingRule.id,
          payload,
        );
        setRules((current) =>
          current.map((item) => (item.id === updated.id ? updated : item)),
        );
        message.success('访问规则已更新');
      } else {
        const created = await createDataServiceIpAccessRule(apiId, payload);
        setRules((current) => [created, ...current]);
        message.success('访问规则已添加');
      }
      closeModal();
    } catch (error: any) {
      message.error(error?.message || '保存访问规则失败');
    } finally {
      setRuleSaving(false);
    }
  };

  const toggleRule = async (rule: DataServiceIpAccessRule, enabled: boolean) => {
    setBusyRuleId(rule.id);
    try {
      const updated = await updateDataServiceIpAccessRule(apiId, rule.id, {
        ruleType: rule.ruleType,
        networkCidr: rule.networkCidr,
        description: rule.description || null,
        enabled,
        expiresAt: rule.expiresAt || null,
      });
      setRules((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      message.success(enabled ? '规则已启用' : '规则已停用');
    } catch (error: any) {
      message.error(error?.message || '更新规则状态失败');
    } finally {
      setBusyRuleId(undefined);
    }
  };

  const removeRule = (rule: DataServiceIpAccessRule) => {
    Modal.confirm({
      title: '删除访问规则',
      content: `确认删除 ${rule.networkCidr}？`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setBusyRuleId(rule.id);
        try {
          await deleteDataServiceIpAccessRule(apiId, rule.id);
          setRules((current) => current.filter((item) => item.id !== rule.id));
          message.success('访问规则已删除');
        } catch (error: any) {
          message.error(error?.message || '删除访问规则失败');
        } finally {
          setBusyRuleId(undefined);
        }
      },
    });
  };

  if (loading) {
    return (
      <div className="flex min-h-[360px] items-center justify-center rounded-lg bg-white">
        <Spin />
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <section className="rounded-lg bg-white p-5">
        <div className="flex items-start gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-[#f5f6f8] text-[#475467]">
            <Shield size={17} />
          </div>
          <div className="min-w-0 flex-1">
            <div className="text-[15px] font-semibold text-[#161823]">来源访问策略</div>
            <div className="mt-1 text-[11px] leading-5 text-[#8a8f98]">
              在 API Key 鉴权和限流之前按客户端 IP/CIDR 拦截请求。
            </div>
          </div>
        </div>

        <div className="mt-5 grid gap-2 md:grid-cols-3">
          {MODE_OPTIONS.map((item) => (
            <button
              key={item.key}
              type="button"
              disabled={modeSaving}
              onClick={() => handleModeChange(item.key)}
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
              <div className="mt-1.5 text-[11px] leading-5 text-[#8a8f98]">
                {item.description}
              </div>
            </button>
          ))}
        </div>
      </section>

      <section className="rounded-lg bg-white">
        <div className="flex items-center justify-between gap-4 px-5 pt-4">
          <div>
            <div className="text-[15px] font-semibold text-[#161823]">黑白名单</div>
            <div className="mt-1 text-[11px] text-[#8a8f98]">
              支持单 IP 与 IPv4/IPv6 CIDR；规则可单独停用或设置有效期。
            </div>
          </div>
          <YakButton icon={<Plus size={14} />} onClick={openCreate}>
            添加规则
          </YakButton>
        </div>

        <div className="px-5">
          <YakTab
            activeKey={activeList}
            onChange={(key) => setActiveList(key as DataServiceIpAccessRuleType)}
            items={[
              {
                key: 'ALLOWLIST',
                label: `白名单 ${rules.filter((item) => item.ruleType === 'ALLOWLIST').length}`,
              },
              {
                key: 'DENYLIST',
                label: `黑名单 ${rules.filter((item) => item.ruleType === 'DENYLIST').length}`,
              },
            ]}
          />
        </div>

        <div className="px-5 pb-5">
          {visibleRules.length ? (
            <div className="overflow-hidden rounded-lg border border-solid border-[#eceef1]">
              {visibleRules.map((rule, index) => (
                <div
                  key={rule.id}
                  className={[
                    'grid gap-3 px-4 py-3 md:grid-cols-[minmax(180px,1fr)_minmax(180px,1.2fr)_140px_100px_116px] md:items-center',
                    index ? 'border-t border-solid border-[#eceef1]' : '',
                  ].join(' ')}
                >
                  <div className="min-w-0">
                    <div className="truncate font-mono text-[12px] font-medium text-[#161823]">
                      {rule.networkCidr}
                    </div>
                    <div className="mt-1 text-[10px] text-[#98a2b3]">{ruleStatus(rule)}</div>
                  </div>
                  <div className="truncate text-[12px] text-[#667085]">
                    {rule.description || '—'}
                  </div>
                  <div className="text-[11px] text-[#667085]">
                    {formatTime(rule.expiresAt)}
                  </div>
                  <Switch
                    size="small"
                    checked={rule.enabled}
                    loading={busyRuleId === rule.id}
                    onChange={(checked) => void toggleRule(rule, checked)}
                  />
                  <div className="flex justify-end gap-1">
                    <YakButton
                      type="text"
                      iconOnly
                      icon={<Pencil size={14} />}
                      onClick={() => openEdit(rule)}
                    />
                    <YakButton
                      type="text"
                      danger
                      iconOnly
                      loading={busyRuleId === rule.id}
                      icon={<Trash2 size={14} />}
                      onClick={() => removeRule(rule)}
                    />
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <YakEmpty
              compact
              title={activeList === 'ALLOWLIST' ? '暂无白名单规则' : '暂无黑名单规则'}
              description="添加 IP 或 CIDR 后，可通过上方来源访问策略启用对应名单。"
            />
          )}
        </div>
      </section>

      <section className="rounded-lg bg-white px-5 py-4">
        <div className="text-[12px] font-medium text-[#344054]">反向代理与真实 IP</div>
        <div className="mt-1 text-[11px] leading-5 text-[#8a8f98]">
          默认不信任 X-Forwarded-For / X-Real-IP。只有直接上游命中
          <code className="mx-1 rounded bg-[#f5f6f8] px-1.5 py-0.5 text-[10px] text-[#475467]">
            yak.data-service.access.trusted-proxies
          </code>
          配置后，才会从可信代理链解析真实客户端 IP；否则始终以 TCP 对端地址为准。
        </div>
      </section>

      <Modal
        title={editingRule ? '编辑访问规则' : `添加${activeList === 'ALLOWLIST' ? '白名单' : '黑名单'}规则`}
        open={ruleModalOpen}
        onCancel={closeModal}
        onOk={() => form.submit()}
        confirmLoading={ruleSaving}
        okText={editingRule ? '保存' : '添加'}
        cancelText="取消"
        destroyOnClose
      >
        <Form<RuleFormValues>
          form={form}
          layout="vertical"
          onFinish={(values) => void submitRule(values)}
          initialValues={{ enabled: true }}
          className="pt-2"
        >
          <Form.Item
            name="networkCidr"
            label="IP / CIDR"
            rules={[{ required: true, message: '请输入 IP 或 CIDR' }]}
          >
            <Input
              variant="filled"
              placeholder="例如 10.20.30.40 或 10.20.0.0/16"
              autoComplete="off"
            />
          </Form.Item>
          <Form.Item name="description" label="说明">
            <Input variant="filled" maxLength={255} placeholder="例如：生产应用出口 IP" />
          </Form.Item>
          <Form.Item name="expiresAt" label="有效期">
            <DatePicker
              variant="filled"
              showTime
              className="w-full"
              placeholder="不设置表示永久"
              disabledDate={(date) => date.endOf('day').isBefore(dayjs())}
            />
          </Form.Item>
          <Form.Item name="enabled" label="状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="停用" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
