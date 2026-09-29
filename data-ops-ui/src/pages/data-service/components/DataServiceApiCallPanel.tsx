import { YakButton, YakEmpty, YakTab } from '@/components/ui';
import {
  createDataServiceKey,
  deleteDataServiceKey,
  rotateDataServiceKey,
  setDataServiceAuthMode,
  setDataServiceKeyEnabled,
  updateDataServiceKey,
  type DataServiceApi,
  type DataServiceApiKey,
  type DataServiceAuthMode,
} from '@/services/data-service';
import {
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Tooltip,
  message,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  Copy,
  KeyRound,
  Pencil,
  Plus,
  RefreshCw,
  ShieldCheck,
  Trash2,
} from 'lucide-react';
import { useMemo, useState } from 'react';

interface DataServiceApiCallPanelProps {
  service: DataServiceApi;
  keys: DataServiceApiKey[];
  canManageAccess: boolean;
  onAuthModeChange: (mode: DataServiceAuthMode) => void;
  onKeysChange: (keys: DataServiceApiKey[]) => void;
}

interface ApiKeyFormValues {
  name: string;
  rateLimitPerMinute: number;
  expiresAt?: Dayjs | null;
}

type ExampleKey = 'curl' | 'java' | 'python' | 'javascript';
type KeyActionKind = 'rotate' | 'disable' | 'delete';

interface KeyAction {
  kind: KeyActionKind;
  key: DataServiceApiKey;
}

interface SecretView {
  title: string;
  keyName: string;
  secret: string;
}

const formatTime = (value?: string | null) =>
  value ? value.replace('T', ' ').slice(0, 19) : '永久';

const isExpired = (key: DataServiceApiKey) =>
  Boolean(key.expiresAt && dayjs(key.expiresAt).isBefore(dayjs()));

const isValidKey = (key: DataServiceApiKey) => key.enabled && !isExpired(key);

const keyStatus = (key: DataServiceApiKey) => {
  if (!key.enabled) return { label: '已停用', dot: 'bg-[#b0b5bd]' };
  if (isExpired(key)) return { label: '已过期', dot: 'bg-[#b0b5bd]' };
  return { label: '可用', dot: 'bg-[#20c77a]' };
};

const copyText = async (value: string, successText: string) => {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = value;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
    }
    message.success(successText);
  } catch {
    message.error('复制失败，请手动复制');
  }
};

const buildExampleQuery = (service: DataServiceApi) =>
  (service.parameterNames || [])
    .map((name) => `${encodeURIComponent(name)}=${encodeURIComponent(`<${name}>`)}`)
    .join('&');

const buildExamples = (
  service: DataServiceApi,
  runtimeUrl: string,
): Record<ExampleKey, string> => {
  const parameterNames = service.parameterNames || [];
  const query = buildExampleQuery(service);
  const requestUrl = query ? `${runtimeUrl}?${query}` : runtimeUrl;
  const apiKeyHeader = service.authMode === 'API_KEY';

  const curlParts = [`curl -G '${runtimeUrl}'`];
  if (apiKeyHeader) curlParts.push("-H 'X-API-Key: <YOUR_API_KEY>'");
  parameterNames.forEach((name) => {
    curlParts.push(`--data-urlencode '${name}=<${name}>'`);
  });

  const javaLines = [
    'HttpClient client = HttpClient.newHttpClient();',
    `HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("${requestUrl}"))`,
    '    .GET();',
  ];
  if (apiKeyHeader) {
    javaLines.push('builder.header("X-API-Key", "<YOUR_API_KEY>");');
  }
  javaLines.push(
    '',
    'HttpResponse<String> response = client.send(',
    '    builder.build(),',
    '    HttpResponse.BodyHandlers.ofString()',
    ');',
  );

  const pythonParams = parameterNames.length
    ? `params = {\n${parameterNames
        .map((name) => `    "${name}": "<${name}>",`)
        .join('\n')}\n}`
    : 'params = {}';
  const pythonHeaders = apiKeyHeader
    ? 'headers = {"X-API-Key": "<YOUR_API_KEY>"}'
    : 'headers = {}';

  const jsParams = parameterNames.length
    ? `const params = new URLSearchParams({\n${parameterNames
        .map((name) => `  ${JSON.stringify(name)}: ${JSON.stringify(`<${name}>`)},`)
        .join('\n')}\n});`
    : 'const params = new URLSearchParams();';
  const jsHeaders = apiKeyHeader
    ? `headers: {\n    'X-API-Key': '<YOUR_API_KEY>',\n  },`
    : 'headers: {},';

  return {
    curl: curlParts.join(' \\\n  '),
    java: javaLines.join('\n'),
    python: [
      'import requests',
      '',
      pythonParams,
      pythonHeaders,
      '',
      `response = requests.get("${runtimeUrl}", params=params, headers=headers, timeout=30)`,
      'response.raise_for_status()',
      'print(response.json())',
    ].join('\n'),
    javascript: [
      jsParams,
      `const url = ${JSON.stringify(runtimeUrl)} + (params.size ? '?' + params.toString() : '');`,
      '',
      'const response = await fetch(url, {',
      '  method: \'GET\',',
      `  ${jsHeaders}`,
      '});',
      '',
      'if (!response.ok) throw new Error(`HTTP ${response.status}`);',
      'const result = await response.json();',
      'console.log(result);',
    ].join('\n'),
  };
};

const PanelSection = ({
  title,
  description,
  action,
  children,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
  children: React.ReactNode;
}) => (
  <section className="rounded-lg bg-white">
    <div className="flex min-h-[64px] items-center justify-between gap-4 px-5 py-3">
      <div className="min-w-0">
        <div className="text-[15px] font-semibold text-[#161823]">{title}</div>
        {description ? (
          <div className="mt-1 text-[11px] leading-5 text-[#8a8f98]">
            {description}
          </div>
        ) : null}
      </div>
      {action}
    </div>
    {children}
  </section>
);

export default function DataServiceApiCallPanel({
  service,
  keys,
  canManageAccess,
  onAuthModeChange,
  onKeysChange,
}: DataServiceApiCallPanelProps) {
  const [keyForm] = Form.useForm<ApiKeyFormValues>();
  const [keyModalOpen, setKeyModalOpen] = useState(false);
  const [editingKey, setEditingKey] = useState<DataServiceApiKey>();
  const [keySaving, setKeySaving] = useState(false);
  const [authChanging, setAuthChanging] = useState(false);
  const [busyKeyId, setBusyKeyId] = useState<number>();
  const [pendingEnableAfterCreate, setPendingEnableAfterCreate] = useState(false);
  const [secretView, setSecretView] = useState<SecretView>();
  const [keyAction, setKeyAction] = useState<KeyAction>();
  const [actionSaving, setActionSaving] = useState(false);
  const [exampleKey, setExampleKey] = useState<ExampleKey>('curl');

  const runtimeUrl = useMemo(() => {
    if (typeof window === 'undefined') return service.runtimePath;
    return `${window.location.origin}${service.runtimePath}`;
  }, [service.runtimePath]);

  const examples = useMemo(
    () => buildExamples(service, runtimeUrl),
    [runtimeUrl, service],
  );

  const validKeyCount = useMemo(
    () => keys.filter(isValidKey).length,
    [keys],
  );

  const replaceKey = (next: DataServiceApiKey) => {
    onKeysChange(keys.map((item) => (item.id === next.id ? next : item)));
  };

  const openCreateKey = (enableAuthAfterCreate = false) => {
    setEditingKey(undefined);
    setPendingEnableAfterCreate(enableAuthAfterCreate);
    keyForm.resetFields();
    keyForm.setFieldsValue({ rateLimitPerMinute: 60 });
    setKeyModalOpen(true);
  };

  const openEditKey = (key: DataServiceApiKey) => {
    setEditingKey(key);
    setPendingEnableAfterCreate(false);
    keyForm.setFieldsValue({
      name: key.name,
      rateLimitPerMinute: key.rateLimitPerMinute,
      expiresAt: key.expiresAt ? dayjs(key.expiresAt) : null,
    });
    setKeyModalOpen(true);
  };

  const closeKeyModal = () => {
    if (keySaving) return;
    setKeyModalOpen(false);
    setEditingKey(undefined);
    setPendingEnableAfterCreate(false);
    keyForm.resetFields();
  };

  const handleAuthModeChange = async (mode: DataServiceAuthMode) => {
    if (mode === service.authMode || authChanging) return;
    if (mode === 'API_KEY' && validKeyCount === 0) {
      message.info('请先创建一个有效 API Key，创建完成后将自动启用 API Key 认证');
      openCreateKey(true);
      return;
    }

    setAuthChanging(true);
    try {
      const nextMode = await setDataServiceAuthMode(service.id, mode);
      onAuthModeChange(nextMode);
      message.success(mode === 'API_KEY' ? '已启用 API Key 认证' : '已切换为公开调用');
    } catch (error: any) {
      message.error(error?.message || '更新认证方式失败');
    } finally {
      setAuthChanging(false);
    }
  };

  const handleKeySubmit = async (values: ApiKeyFormValues) => {
    setKeySaving(true);
    try {
      const expiresAt = values.expiresAt
        ? values.expiresAt.format('YYYY-MM-DDTHH:mm:ss')
        : null;

      if (editingKey) {
        const updated = await updateDataServiceKey(service.id, editingKey.id, {
          name: values.name.trim(),
          rateLimitPerMinute: values.rateLimitPerMinute,
          expiresAt,
          expiresAtSet: true,
        });
        replaceKey(updated);
        message.success('API Key 配置已更新');
        closeKeyModal();
        return;
      }

      const created = await createDataServiceKey(service.id, {
        name: values.name.trim(),
        rateLimitPerMinute: values.rateLimitPerMinute,
        expiresAt,
      });
      onKeysChange([...keys, created.key]);
      setKeyModalOpen(false);
      keyForm.resetFields();
      setSecretView({
        title: 'API Key 创建成功',
        keyName: created.key.name,
        secret: created.secret,
      });

      if (pendingEnableAfterCreate) {
        try {
          const nextMode = await setDataServiceAuthMode(service.id, 'API_KEY');
          onAuthModeChange(nextMode);
          message.success('API Key 已创建并启用认证');
        } catch (error: any) {
          message.warning(error?.message || 'API Key 已创建，但启用认证失败');
        }
      } else {
        message.success('API Key 已创建');
      }
      setPendingEnableAfterCreate(false);
    } catch (error: any) {
      message.error(error?.message || (editingKey ? '更新 API Key 失败' : '创建 API Key 失败'));
    } finally {
      setKeySaving(false);
    }
  };

  const enableKey = async (key: DataServiceApiKey) => {
    setBusyKeyId(key.id);
    try {
      const updated = await setDataServiceKeyEnabled(service.id, key.id, true);
      replaceKey(updated);
      message.success('API Key 已启用');
    } catch (error: any) {
      message.error(error?.message || '启用 API Key 失败');
    } finally {
      setBusyKeyId(undefined);
    }
  };

  const handleKeyAction = async () => {
    if (!keyAction) return;
    const { key, kind } = keyAction;
    setActionSaving(true);
    try {
      if (kind === 'rotate') {
        const rotated = await rotateDataServiceKey(service.id, key.id);
        replaceKey(rotated.key);
        setSecretView({
          title: 'API Key 已轮换',
          keyName: rotated.key.name,
          secret: rotated.secret,
        });
        message.success('API Key 已轮换，旧密钥立即失效');
      } else if (kind === 'disable') {
        const updated = await setDataServiceKeyEnabled(service.id, key.id, false);
        replaceKey(updated);
        message.success('API Key 已停用');
      } else {
        await deleteDataServiceKey(service.id, key.id);
        onKeysChange(keys.filter((item) => item.id !== key.id));
        message.success('API Key 已删除');
      }
      setKeyAction(undefined);
    } catch (error: any) {
      message.error(error?.message || '操作 API Key 失败');
    } finally {
      setActionSaving(false);
    }
  };

  const actionTitle = keyAction?.kind === 'rotate'
    ? '轮换 API Key'
    : keyAction?.kind === 'disable'
      ? '停用 API Key'
      : '删除 API Key';
  const actionDescription = keyAction?.kind === 'rotate'
    ? '轮换后旧密钥会立即失效，新密钥只展示一次。请确认调用方可以及时更新。'
    : keyAction?.kind === 'disable'
      ? '停用后使用该 Key 的请求会立即被拒绝。'
      : '删除后无法恢复，使用该 Key 的调用方会立即失去访问能力。';

  return (
    <div className="space-y-3">
      <PanelSection
        title="API 调用"
        description="调用地址、认证方式和常用 SDK 示例集中在这里。"
      >
        <div className="grid gap-4 px-5 pb-5 xl:grid-cols-[minmax(0,1.6fr)_minmax(280px,0.8fr)]">
          <div>
            <div className="mb-2 text-[12px] font-medium text-[#475467]">调用地址</div>
            <div className="flex min-h-11 items-center gap-3 rounded-md bg-[#f6f6f7] px-3 py-2">
              <span className="shrink-0 rounded bg-white px-2 py-1 font-mono text-[11px] font-semibold text-[#475467]">
                GET
              </span>
              <span className="min-w-0 flex-1 break-all font-mono text-[12px] text-[#30343b]">
                {runtimeUrl}
              </span>
              <Tooltip title="复制调用地址">
                <YakButton
                  type="text"
                  iconOnly
                  icon={<Copy size={14} />}
                  onClick={() => void copyText(runtimeUrl, '调用地址已复制')}
                />
              </Tooltip>
            </div>
            {!service.enabled ? (
              <div className="mt-2 rounded-md bg-[#f7f7f8] px-3 py-2 text-[11px] leading-5 text-[#667085]">
                当前数据服务已停用，外部调用会被拒绝。启用服务后再交付给调用方。
              </div>
            ) : null}
          </div>

          <div>
            <div className="mb-2 text-[12px] font-medium text-[#475467]">认证方式</div>
            <Select<DataServiceAuthMode>
              value={service.authMode}
              variant="filled"
              className="w-full"
              disabled={!canManageAccess}
              loading={authChanging}
              options={[
                { value: 'NONE', label: '公开调用（无需认证）' },
                { value: 'API_KEY', label: 'API Key（X-API-Key）' },
              ]}
              onChange={(mode) => void handleAuthModeChange(mode)}
            />
            <div className="mt-2 flex items-start gap-2 text-[11px] leading-5 text-[#8a8f98]">
              <ShieldCheck size={13} className="mt-1 shrink-0" />
              <span>
                {service.authMode === 'API_KEY'
                  ? '请求需要携带 X-API-Key；每个 Key 可独立配置集群共享 RPM。'
                  : '当前无需调用凭证。公开调用不会进入 API Key 级限流。'}
              </span>
            </div>
          </div>
        </div>
      </PanelSection>

      <PanelSection
        title="API Key"
        description="密钥明文只在创建或轮换成功时展示一次；列表仅保留可识别前缀。"
        action={canManageAccess ? (
          <YakButton
            type="primary"
            icon={<Plus size={14} />}
            onClick={() => openCreateKey(false)}
          >
            创建 API Key
          </YakButton>
        ) : undefined}
      >
        {canManageAccess ? (
          keys.length ? (
            <div className="px-5 pb-5">
              <div className="mb-3 grid grid-cols-3 gap-3 rounded-md bg-[#f7f7f8] px-4 py-3 sm:grid-cols-4">
                <div>
                  <div className="text-[10px] text-[#98a2b3]">全部 Key</div>
                  <div className="mt-1 text-[16px] font-semibold text-[#30343b]">{keys.length}</div>
                </div>
                <div>
                  <div className="text-[10px] text-[#98a2b3]">有效 Key</div>
                  <div className="mt-1 text-[16px] font-semibold text-[#30343b]">{validKeyCount}</div>
                </div>
                <div>
                  <div className="text-[10px] text-[#98a2b3]">认证状态</div>
                  <div className="mt-1 text-[12px] font-medium text-[#30343b]">
                    {service.authMode === 'API_KEY' ? '已启用' : '未启用'}
                  </div>
                </div>
                <div className="hidden sm:block">
                  <div className="text-[10px] text-[#98a2b3]">限流粒度</div>
                  <div className="mt-1 text-[12px] font-medium text-[#30343b]">每 Key / 分钟</div>
                </div>
              </div>

              <div className="divide-y divide-[#f0f1f3]">
                {keys.map((key) => {
                  const status = keyStatus(key);
                  return (
                    <div
                      key={key.id}
                      className="grid gap-4 py-4 xl:grid-cols-[minmax(220px,1.15fr)_minmax(360px,1.6fr)_auto] xl:items-center"
                    >
                      <div className="min-w-0">
                        <div className="flex min-w-0 items-center gap-2">
                          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-[#f6f6f7] text-[#667085]">
                            <KeyRound size={15} />
                          </div>
                          <div className="min-w-0">
                            <div className="truncate text-[13px] font-semibold text-[#30343b]">
                              {key.name}
                            </div>
                            <div className="mt-0.5 flex items-center gap-1.5 text-[10px] text-[#8a8f98]">
                              <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                              <span>{status.label}</span>
                              <span>·</span>
                              <span className="font-mono">{key.keyPrefix}••••</span>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div className="grid grid-cols-3 gap-4 text-[11px]">
                        <div>
                          <div className="text-[#98a2b3]">调用上限</div>
                          <div className="mt-1 font-medium text-[#475467]">
                            {key.rateLimitPerMinute} 次/分钟
                          </div>
                        </div>
                        <div>
                          <div className="text-[#98a2b3]">过期时间</div>
                          <div className="mt-1 font-medium text-[#475467]">
                            {formatTime(key.expiresAt)}
                          </div>
                        </div>
                        <div>
                          <div className="text-[#98a2b3]">最近使用</div>
                          <div className="mt-1 font-medium text-[#475467]">
                            {key.lastUsedAt ? formatTime(key.lastUsedAt) : '从未使用'}
                          </div>
                        </div>
                      </div>

                      <div className="flex flex-wrap items-center justify-start gap-1 xl:justify-end">
                        <Tooltip title="编辑配置">
                          <YakButton
                            type="text"
                            iconOnly
                            icon={<Pencil size={14} />}
                            onClick={() => openEditKey(key)}
                          />
                        </Tooltip>
                        <Tooltip title="轮换密钥">
                          <YakButton
                            type="text"
                            iconOnly
                            icon={<RefreshCw size={14} />}
                            onClick={() => setKeyAction({ kind: 'rotate', key })}
                          />
                        </Tooltip>
                        {key.enabled ? (
                          <YakButton
                            type="text"
                            size="small"
                            onClick={() => setKeyAction({ kind: 'disable', key })}
                          >
                            停用
                          </YakButton>
                        ) : (
                          <YakButton
                            type="text"
                            size="small"
                            loading={busyKeyId === key.id}
                            onClick={() => void enableKey(key)}
                          >
                            启用
                          </YakButton>
                        )}
                        <Tooltip title="删除">
                          <YakButton
                            type="text"
                            danger
                            iconOnly
                            icon={<Trash2 size={14} />}
                            onClick={() => setKeyAction({ kind: 'delete', key })}
                          />
                        </Tooltip>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ) : (
            <YakEmpty
              compact
              title="还没有 API Key"
              description="创建调用方凭证后，可设置每分钟调用上限和过期时间。"
            />
          )
        ) : (
          <YakEmpty
            compact
            title="暂无 API Key 管理权限"
            description="需要 data-service:access 权限才能查看和管理调用凭证。"
          />
        )}
      </PanelSection>

      <PanelSection
        title="调用示例"
        description="示例会根据当前认证方式和 SQL 参数自动生成。"
        action={(
          <YakButton
            type="text"
            icon={<Copy size={14} />}
            onClick={() => void copyText(examples[exampleKey], '示例代码已复制')}
          >
            复制代码
          </YakButton>
        )}
      >
        <div className="px-5 pb-5">
          <YakTab
            activeKey={exampleKey}
            onChange={(key) => setExampleKey(key as ExampleKey)}
            items={[
              { key: 'curl', label: 'cURL' },
              { key: 'java', label: 'Java' },
              { key: 'python', label: 'Python' },
              { key: 'javascript', label: 'JavaScript' },
            ]}
          />
          <pre className="mt-1 max-h-[420px] overflow-auto rounded-md bg-[#17181c] p-4 text-[12px] leading-6 text-[#e5e7eb]">
            <code>{examples[exampleKey]}</code>
          </pre>
        </div>
      </PanelSection>

      <Modal
        open={keyModalOpen}
        title={editingKey ? '编辑 API Key' : '创建 API Key'}
        footer={null}
        onCancel={closeKeyModal}
        destroyOnClose
      >
        <Form<ApiKeyFormValues>
          form={keyForm}
          layout="vertical"
          requiredMark={false}
          className="pt-2"
          onFinish={(values) => void handleKeySubmit(values)}
        >
          <Form.Item
            label="名称"
            name="name"
            rules={[
              { required: true, message: '请输入调用方名称' },
              { max: 128, message: '名称不能超过 128 个字符' },
            ]}
          >
            <Input variant="filled" placeholder="例如：生产系统 / BI 平台" />
          </Form.Item>
          <Form.Item
            label="每分钟调用上限"
            name="rateLimitPerMinute"
            rules={[{ required: true, message: '请输入每分钟调用上限' }]}
            extra="限额在整个 Yak Ops 集群内按 Key 共享，范围 1 ~ 100000。"
          >
            <InputNumber
              min={1}
              max={100000}
              precision={0}
              variant="filled"
              className="!w-full"
            />
          </Form.Item>
          <Form.Item
            label="过期时间"
            name="expiresAt"
            extra="留空表示永不过期。"
          >
            <DatePicker
              showTime
              variant="filled"
              className="w-full"
              placeholder="永不过期"
              disabledDate={(date) => date.endOf('day').isBefore(dayjs())}
            />
          </Form.Item>
          <div className="flex justify-end gap-2 pt-2">
            <YakButton onClick={closeKeyModal}>取消</YakButton>
            <YakButton type="primary" htmlType="submit" loading={keySaving}>
              {editingKey ? '保存' : '创建'}
            </YakButton>
          </div>
        </Form>
      </Modal>

      <Modal
        open={Boolean(secretView)}
        title={secretView?.title}
        footer={null}
        closable={false}
        maskClosable={false}
      >
        <div className="pt-2">
          <div className="rounded-md bg-[#f7f7f8] px-4 py-3 text-[12px] leading-5 text-[#667085]">
            密钥明文只展示这一次。关闭窗口后无法再次查看，如遗失只能轮换新 Key。
          </div>
          <div className="mt-4 text-[12px] font-medium text-[#475467]">
            {secretView?.keyName}
          </div>
          <div className="mt-2 flex items-center gap-2 rounded-md bg-[#f6f6f7] p-2">
            <Input
              readOnly
              variant="filled"
              value={secretView?.secret}
              className="font-mono"
            />
            <YakButton
              type="primary"
              icon={<Copy size={14} />}
              onClick={() => {
                if (secretView?.secret) {
                  void copyText(secretView.secret, 'API Key 已复制');
                }
              }}
            >
              复制
            </YakButton>
          </div>
          <div className="mt-5 flex justify-end">
            <YakButton type="primary" onClick={() => setSecretView(undefined)}>
              我已保存
            </YakButton>
          </div>
        </div>
      </Modal>

      <Modal
        open={Boolean(keyAction)}
        title={actionTitle}
        footer={null}
        onCancel={() => {
          if (!actionSaving) setKeyAction(undefined);
        }}
      >
        <div className="pt-2 text-[13px] leading-6 text-[#667085]">
          {actionDescription}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <YakButton
            disabled={actionSaving}
            onClick={() => setKeyAction(undefined)}
          >
            取消
          </YakButton>
          <YakButton
            type="primary"
            danger={keyAction?.kind === 'delete'}
            loading={actionSaving}
            onClick={() => void handleKeyAction()}
          >
            {keyAction?.kind === 'rotate'
              ? '确认轮换'
              : keyAction?.kind === 'disable'
                ? '确认停用'
                : '确认删除'}
          </YakButton>
        </div>
      </Modal>
    </div>
  );
}
