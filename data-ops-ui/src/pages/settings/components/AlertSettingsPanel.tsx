import {
  Button,
  Drawer,
  Form,
  Input,
  message,
  Radio,
  Spin,
  Switch,
  Tag,
  Tooltip,
} from 'antd';
import { CheckCircleFilled, CloseCircleFilled, DisconnectOutlined, MinusCircleOutlined } from '@ant-design/icons';
import { ChevronRight, Save, X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';

import {
  getAlertChannel,
  listAlertChannels,
  saveAlertChannel,
  testAlertConnection,
  toggleAlertChannelEnabled,
  type AlertChannelVO,
} from '@/services/alert/alert';
import DingTalkIcon from './icon/DingTalkIcon';

/* ─── DingTalk config form — matches DingTalkAlertConfig fields ─── */

interface DingTalkConfig {
  webhookUrl: string;
  securityType?: string;
  secret?: string;
  keywords?: string;
  ipAddresses?: string;
}

const buildConfigJson = (values: DingTalkConfig): string => {
  const config: Record<string, unknown> = {
    webhookUrl: values.webhookUrl,
    securityType: values.securityType || 'SIGN',
    msgType: 'text',
  };
  if (values.securityType === 'SIGN' && values.secret?.trim()) {
    config.secret = values.secret.trim();
  }
  if (values.securityType === 'KEYWORD' && values.keywords?.trim()) {
    config.keywords = values.keywords.split(',').map((s) => s.trim()).filter(Boolean);
  }
  if (values.securityType === 'IP' && values.ipAddresses?.trim()) {
    config.ipAddresses = values.ipAddresses.split(',').map((s) => s.trim()).filter(Boolean);
  }
  return JSON.stringify(config);
};

/** 从 configJson 反向填充表单初始值 */
const parseConfigJson = (json: string | null | undefined): Partial<DingTalkConfig> => {
  if (!json) return {};
  try {
    const obj = JSON.parse(json);
    return {
      webhookUrl: obj.webhookUrl || '',
      securityType: obj.securityType || 'SIGN',
      secret: obj.secret || '',
      keywords: Array.isArray(obj.keywords) ? obj.keywords.join(',') : '',
      ipAddresses: Array.isArray(obj.ipAddresses) ? obj.ipAddresses.join(',') : '',
    };
  } catch {
    return {};
  }
};

/* ─── shared styles (aligned with EditorSettingsPanel) ─── */

const labelClassName = 'mb-1.5 block text-[13px] font-medium text-[#344054]';

/* ─── main panel ─── */

const AlertSettingsPanel = () => {
  const [channels, setChannels] = useState<AlertChannelVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [testing, setTesting] = useState(false);
  const [testingChannel, setTestingChannel] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // drawer state
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [selectedChannelType, setSelectedChannelType] = useState<string>('');
  const [form] = Form.useForm<DingTalkConfig>();
  const securityType = Form.useWatch('securityType', form);

  const fetchChannels = useCallback((silent = false) => {
    if (!silent) setLoading(true);
    listAlertChannels()
      .then((res) => setChannels(res.data || []))
      .catch(() => { if (!silent) message.warning('获取告警渠道失败'); })
      .finally(() => { if (!silent) setLoading(false); });
  }, []);

  useEffect(() => {
    fetchChannels();
  }, [fetchChannels]);

  const openDrawer = async (channelType: string) => {
    setSelectedChannelType(channelType);
    form.resetFields();
    form.setFieldsValue({ securityType: 'SIGN' });
    setDrawerOpen(true);

    // 加载已保存的配置
    try {
      const res = await getAlertChannel(channelType);
      if (res?.data?.configJson) {
        const saved = parseConfigJson(res.data.configJson);
        form.setFieldsValue({
          webhookUrl: saved.webhookUrl || '',
          securityType: saved.securityType || 'SIGN',
          secret: saved.secret || '',
          keywords: saved.keywords || '',
          ipAddresses: saved.ipAddresses || '',
        });
      }
    } catch {
      // 未保存过，保持默认值
    }
  };

  const handleSave = async () => {
    if (!selectedChannelType) return;
    try {
      const values = await form.validateFields();
      const configJson = buildConfigJson(values);
      setSaving(true);
      const res = await saveAlertChannel({
        channelType: selectedChannelType,
        configJson,
        enabled: true,
      });
      if (res?.data === true) {
        message.success('保存成功');
        fetchChannels(true);
      } else {
        message.error(res?.msg || '保存失败');
      }
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error('保存请求失败');
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async () => {
    if (!selectedChannelType) return;
    try {
      const values = await form.validateFields();
      const configJson = buildConfigJson(values);
      setTesting(true);
      const res = await testAlertConnection(selectedChannelType, configJson);
      if (res?.data === true) {
        message.success('测试消息发送成功');
        fetchChannels(true);
      } else {
        message.error(res?.msg || '测试失败，请检查配置');
      }
    } catch (error: any) {
      if (error?.errorFields) return;
      message.error('测试请求失败');
    } finally {
      setTesting(false);
    }
  };

  const handleTestFromList = async (channelType: string) => {
    try {
      setTestingChannel(channelType);
      const res = await testAlertConnection(channelType, undefined);
      if (res?.data === true) {
        message.success('测试消息发送成功');
        fetchChannels(true);
      } else {
        message.error(res?.msg || '测试失败，请检查配置');
      }
    } catch {
      message.error('测试请求失败');
    } finally {
      setTestingChannel(null);
    }
  };

  const handleToggleEnabled = async (channelType: string, enabled: boolean) => {
    try {
      const res = await toggleAlertChannelEnabled(channelType, enabled);
      if (res?.data === true) {
        message.success(enabled ? '已启用' : '已禁用');
        fetchChannels(true);
      } else {
        message.error(res?.msg || '操作失败');
      }
    } catch {
      message.error('操作请求失败');
    }
  };

  const channelTypeLabel: Record<string, string> = {
    DINGTALK: '钉钉',
  };

  /** 根据渠道类型返回图标 */
  const connStatusTag = (status?: string) => {
    const s = (status || 'UNKNOWN').trim().toUpperCase();
    if (s === 'CONNECTED') {
      return (
        <Tooltip title="告警渠道连接正常">
          <Tag
            color="success"
            icon={<CheckCircleFilled />}
            style={{ marginInlineEnd: 0, borderRadius: 999, paddingInline: 8, fontSize: 11, lineHeight: '20px' }}
          >
            已测试
          </Tag>
        </Tooltip>
      );
    }
    if (s === 'DISCONNECTED') {
      return (
        <Tooltip title="最近一次测试失败">
          <Tag
            color="error"
            icon={<CloseCircleFilled />}
            style={{ marginInlineEnd: 0, borderRadius: 999, paddingInline: 8, fontSize: 11, lineHeight: '20px' }}
          >
            连接失败
          </Tag>
        </Tooltip>
      );
    }
    return (
      <Tooltip title="尚未进行测试">
        <Tag
          color="default"
          icon={<MinusCircleOutlined />}
          style={{ marginInlineEnd: 0, borderRadius: 999, paddingInline: 8, fontSize: 11, lineHeight: '20px' }}
        >
          未测试
        </Tag>
      </Tooltip>
    );
  };

  const channelIcon = (type: string, enabled: boolean) => {
    if (type === 'DINGTALK') {
      return (
        <div
          className={[
            'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl',
            'transition-colors duration-200',
            !enabled && 'opacity-40',
          ].join(' ')}
        >
          <DingTalkIcon size={28} />
        </div>
      );
    }
    return (
      <div
        className={[
          'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#f5f5f6]',
          'text-sm font-semibold text-[#475569] transition-colors duration-200',
          !enabled && 'opacity-40',
        ].join(' ')}
      >
        {(channelTypeLabel[type] || type).slice(0, 1).toUpperCase()}
      </div>
    );
  };

  const isDrawerBusy = saving || testing;

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Spin size="small" />
      </div>
    );
  }

  return (
    <div className="text-[13px] text-[#344054]">
      {/* channel list */}
      <section className="border-t border-[#eaecf0] py-6 first:border-t-0 first:pt-0">
        <div className="mb-4 text-[14px] font-semibold text-[#1d2939]">告警渠道</div>

        {channels.length === 0 ? (
          <div className="flex flex-col items-center py-20 text-center">
            <p className="m-0 text-sm text-[#475569]">暂无已注册的告警渠道</p>
          </div>
        ) : (
          <div className="divide-y divide-[#eaecf0]">
            {channels.map((ch) => {
              const isTestingThis = testingChannel === ch.type;
              const enabled = !!ch.enabled;
              return (
                <div
                  key={ch.type}
                  className="group flex items-center gap-4 px-0 py-4"
                >
                  {channelIcon(ch.type, enabled)}
                  <div className="min-w-0 flex-1">
                    <p className={['m-0 truncate text-sm font-semibold', enabled ? 'text-[#1d2939]' : 'text-[#98a2b3]'].join(' ')}>
                      {channelTypeLabel[ch.type] || ch.name}
                    </p>
                    <div className="mt-1.5 flex items-center gap-2">
                      <span className="text-xs text-[#98a2b3]">v{ch.version}</span>
                      {connStatusTag(ch.connStatus)}
                    </div>
                  </div>
                  <Tooltip title="测试连通性">
                    <button
                      type="button"
                      onClick={() => handleTestFromList(ch.type)}
                      disabled={!!testingChannel || !enabled}
                      className={[
                        'shrink-0 inline-flex h-8 w-8 items-center justify-center',
                        'rounded-lg text-[#98a2b3]',
                        'transition-all duration-200',
                        'hover:bg-[#f5f5f6] hover:text-[#475569]',
                        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#d1d5db]',
                        'disabled:pointer-events-none disabled:opacity-40',
                      ].join(' ')}
                    >
                      {isTestingThis
                        ? <Spin size="small" />
                        : <DisconnectOutlined className="text-sm" />
                      }
                    </button>
                  </Tooltip>
                  <Switch
                    size="small"
                    checked={enabled}
                    onChange={(checked) => handleToggleEnabled(ch.type, checked)}
                    className="shrink-0"
                  />
                  <button
                    type="button"
                    onClick={() => openDrawer(ch.type)}
                    className={[
                      'shrink-0 inline-flex h-9 w-9 items-center justify-center',
                      'rounded-lg text-[#d1d5db]',
                      'transition-all duration-200',
                      'hover:bg-[#f5f5f6] hover:text-[#475569]',
                      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[#d1d5db]',
                    ].join(' ')}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </section>

      {/* config drawer */}
      <Drawer
        open={drawerOpen}
        onClose={() => !isDrawerBusy && setDrawerOpen(false)}
        width="min(640px, 100vw)"
        closable={false}
        maskClosable={!isDrawerBusy}
        destroyOnClose
        styles={{ body: { padding: 0, overflow: 'hidden' } }}
      >
        <div className="flex h-full min-h-0 flex-col bg-white">
          {/* drawer header */}
          <header
            className={[
              'flex min-h-[56px] shrink-0',
              'items-center justify-between gap-4',
              'border-b border-slate-100',
              'bg-white px-6 py-3',
            ].join(' ')}
          >
            <div className="flex min-w-0 items-center gap-3">
              {selectedChannelType === 'DINGTALK' ? (
                <DingTalkIcon size={22} />
              ) : null}
              <h2 className="m-0 text-base font-semibold text-slate-950">
                {channelTypeLabel[selectedChannelType] || selectedChannelType}
              </h2>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <Button
                size="small"
                icon={<Save className="h-3.5 w-3.5" />}
                loading={saving}
                onClick={handleSave}
                type="primary"
                className="rounded-lg"
              >
                保存
              </Button>
              <Button
                size="small"
                type="text"
                loading={testing}
                onClick={handleTest}
                className="rounded-lg"
              >
                测试
              </Button>
              <button
                type="button"
                aria-label="关闭"
                disabled={isDrawerBusy}
                onClick={() => setDrawerOpen(false)}
                className={[
                  'ml-1 inline-flex h-9 w-9',
                  'items-center justify-center',
                  'rounded-lg text-slate-400',
                  'transition-colors duration-200',
                  'hover:bg-slate-100',
                  'hover:text-slate-900',
                  'disabled:pointer-events-none',
                  'disabled:opacity-40',
                ].join(' ')}
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </header>

          {/* drawer body */}
          <div className="min-h-0 flex-1 overflow-y-auto px-6 py-6">
            <Form
              form={form}
              layout="vertical"
              requiredMark={false}
            >
              {/* 基础配置 */}
              <div className="border-b border-[#eaecf0] pb-6">
                <div className="mb-4 text-[14px] font-semibold text-[#1d2939]">基础配置</div>
                <div className="space-y-4">
                  <div>
                    <label className={labelClassName}>Webhook 地址</label>
                    <Form.Item
                      name="webhookUrl"
                      noStyle
                      rules={[
                        { required: true, message: '请输入 Webhook 地址' },
                        { type: 'url', message: '请输入合法的 URL 地址' },
                      ]}
                    >
                      <Input variant="filled" placeholder="https://oapi.dingtalk.com/robot/send?access_token=..." />
                    </Form.Item>
                  </div>
                </div>
              </div>

              {/* 安全设置 */}
              <div className="border-b border-[#eaecf0] py-6">
                <div className="mb-4 text-[14px] font-semibold text-[#1d2939]">安全设置</div>
                <div className="space-y-4">
                  <div>
                    <label className={labelClassName}>安全类型</label>
                    <Form.Item name="securityType" noStyle>
                      <Radio.Group>
                        <Radio value="SIGN">加签</Radio>
                        <Radio value="KEYWORD">自定义关键词</Radio>
                        <Radio value="IP">IP 地址（段）</Radio>
                      </Radio.Group>
                    </Form.Item>
                  </div>
                  {securityType === 'SIGN' && (
                    <div>
                      <label className={labelClassName}>加签密钥</label>
                      <Form.Item
                        name="secret"
                        noStyle
                        rules={[{ required: true, message: '加签模式下密钥不能为空' }]}
                      >
                        <Input.Password variant="filled" placeholder="SEC 开头的加签密钥" />
                      </Form.Item>
                    </div>
                  )}
                  {securityType === 'KEYWORD' && (
                    <div>
                      <label className={labelClassName}>自定义关键词</label>
                      <Form.Item
                        name="keywords"
                        noStyle
                        rules={[{ required: true, message: '关键词模式下关键词不能为空' }]}
                      >
                        <Input variant="filled" placeholder="多个以逗号分隔，如 Yak Ops,告警" />
                      </Form.Item>
                    </div>
                  )}
                  {securityType === 'IP' && (
                    <div>
                      <label className={labelClassName}>IP 地址（段）</label>
                      <Form.Item
                        name="ipAddresses"
                        noStyle
                        rules={[{ required: true, message: 'IP 模式下地址不能为空' }]}
                      >
                        <Input variant="filled" placeholder="多个以逗号分隔，如 10.0.0.1,192.168.1.0/24" />
                      </Form.Item>
                    </div>
                  )}
                </div>
              </div>

            </Form>
          </div>
        </div>
      </Drawer>
    </div>
  );
};

export default AlertSettingsPanel;
