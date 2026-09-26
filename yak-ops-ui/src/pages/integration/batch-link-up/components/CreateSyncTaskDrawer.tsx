import EmojiIconPicker, {
  DEFAULT_EMOJI_ICON,
  type EmojiIconValue,
} from '@/components/EmojiIconPicker';
import { YakButton } from '@/components/ui';
import {
  bindOfflineDraftSource,
  findReadableSourceDataSource,
} from '@/features/integration/sourceOnboarding';
import {
  createOfflineSyncDraft,
  getOfflineSyncUniqueId,
} from '@/services/batch-link-up';
import type { DataSourceRecord } from '@/services/data-source';
import { fetchDataSourceAll } from '@/services/data-source/legacy';
import {
  ArrowRightOutlined,
  DatabaseOutlined,
  TableOutlined,
} from '@ant-design/icons';
import { history, useIntl } from '@umijs/max';
import {
  ConfigProvider,
  Drawer,
  Form,
  Input,
  Radio,
  Select,
  message,
} from 'antd';
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';

import {
  BRAND_COLOR,
  BRAND_COLOR_BORDER,
  BRAND_COLOR_SOFT,
  BRAND_COLOR_SOFT_HOVER,
  BRAND_THEME,
} from '@/styles/brand';

import { generateDataSourceOptions } from '../DataSourceSelect';
import {
  connectorIdForNewDataSourceType,
  isGuideMultiEnabledForDataSourceType,
  type OfflineSyncConnectorRole,
} from '../connectorProfiles';
import {
  buildCreatePayload,
  isApiSuccess,
  responseMessage,
  type CreateSyncEndpoint,
  type CreateSyncTaskValues,
  type SyncMode,
} from '../detail/model';

interface CreateSyncTaskDrawerProps {
  open: boolean;
  sourceDataSourceId?: string;
  onCancel: () => void;
  onCreated: (taskId: string, mode: SyncMode) => void;
}

interface CreateSyncTaskFormValues extends CreateSyncTaskValues {
  sourceDbType: string;
  targetDbType: string;
}

interface ConnectorOption {
  value: string;
  label: ReactNode;
  pluginName?: string;
}

const DEFAULT_DB_TYPE = 'MYSQL';

const brandCssVariables = {
  '--yak-brand-color': BRAND_COLOR,
  '--yak-brand-color-border': BRAND_COLOR_BORDER,
  '--yak-brand-color-soft': BRAND_COLOR_SOFT,
  '--yak-brand-color-soft-hover': BRAND_COLOR_SOFT_HOVER,
} as CSSProperties;

const normalizeDbType = (value: unknown) =>
  String(value || '').trim().toUpperCase();

const resolveEndpoint = (
  dbType: string,
  options: ConnectorOption[],
  role: OfflineSyncConnectorRole,
): CreateSyncEndpoint => {
  const option = options.find((item) => item.value === dbType);

  return {
    dbType,
    connectorId: connectorIdForNewDataSourceType(dbType, role),
    pluginName: option?.pluginName || `JDBC-${dbType}`,
  };
};

export default function CreateSyncTaskDrawer({
  open,
  sourceDataSourceId,
  onCancel,
  onCreated,
}: CreateSyncTaskDrawerProps) {
  const intl = useIntl();
  const intlRef = useRef(intl);
  intlRef.current = intl;

  const [form] = Form.useForm<CreateSyncTaskFormValues>();
  const [submitting, setSubmitting] = useState(false);
  const [icon, setIcon] = useState<EmojiIconValue>(DEFAULT_EMOJI_ICON);
  const [onboardingSource, setOnboardingSource] =
    useState<DataSourceRecord>();
  const [onboardingSourceLoading, setOnboardingSourceLoading] =
    useState(false);
  const autoJobNameRef = useRef('');

  const connectorOptions = useMemo(
    () => generateDataSourceOptions() as ConnectorOption[],
    [],
  );

  const sourceDbType = Form.useWatch('sourceDbType', form);
  const targetDbType = Form.useWatch('targetDbType', form);
  const guideMultiEnabled =
    isGuideMultiEnabledForDataSourceType(sourceDbType) &&
    isGuideMultiEnabledForDataSourceType(targetDbType);

  const modeOptions: Array<{
    value: SyncMode;
    title: string;
    description: string;
    icon: ReactNode;
    disabled?: boolean;
  }> = [
    {
      value: 'GUIDE_SINGLE',
      title: intl.formatMessage({ id: 'pages.batchLinkUp.create.mode.single' }),
      description: intl.formatMessage({
        id: 'pages.batchLinkUp.create.mode.singleDescription',
      }),
      icon: <TableOutlined />,
    },
    {
      value: 'GUIDE_MULTI',
      title: intl.formatMessage({ id: 'pages.batchLinkUp.create.mode.multi' }),
      description: intl.formatMessage({
        id: guideMultiEnabled
          ? 'pages.batchLinkUp.create.mode.multiDescription'
          : 'pages.batchLinkUp.create.mode.profileSingleOnlyDescription',
      }),
      icon: <DatabaseOutlined />,
      disabled: !guideMultiEnabled,
    },
  ];

  const createDefaultJobName = useCallback(
    (source: string, target: string) =>
      intlRef.current
        .formatMessage(
          { id: 'pages.batchLinkUp.create.defaultJobName' },
          { source, target },
        )
        .slice(0, 64),
    [],
  );

  useEffect(() => {
    if (!open) return;

    const defaultDbType =
      connectorOptions.find((item) => item.value === DEFAULT_DB_TYPE)?.value ||
      connectorOptions[0]?.value ||
      '';
    const defaultJobName = createDefaultJobName(defaultDbType, defaultDbType);

    autoJobNameRef.current = defaultJobName;
    setIcon(DEFAULT_EMOJI_ICON);
    setOnboardingSource(undefined);
    form.setFieldsValue({
      sourceDbType: defaultDbType,
      targetDbType: defaultDbType,
      jobName: defaultJobName,
      jobDesc: undefined,
      mode: 'GUIDE_SINGLE',
    });
  }, [connectorOptions, createDefaultJobName, form, open]);

  useEffect(() => {
    if (!open || !sourceDataSourceId) {
      setOnboardingSource(undefined);
      setOnboardingSourceLoading(false);
      return undefined;
    }

    let active = true;

    const resolveSourceContext = async () => {
      setOnboardingSourceLoading(true);
      try {
        const response = await fetchDataSourceAll();
        if (!active) return;

        if (!isApiSuccess(response)) {
          setOnboardingSource(undefined);
          message.warning(
            responseMessage(
              response,
              '来源数据源暂时无法确认，请手动选择 Source',
            ),
          );
          return;
        }

        const readableSource = findReadableSourceDataSource(
          response?.data?.bizData || [],
          sourceDataSourceId,
        );
        if (!readableSource) {
          setOnboardingSource(undefined);
          message.warning(
            '来源数据源在当前 Project 中不可见或已不存在，请手动选择 Source',
          );
          return;
        }

        const sourceOption = connectorOptions.find(
          (item) =>
            normalizeDbType(item.value) ===
            normalizeDbType(readableSource.dbType),
        );
        if (!sourceOption) {
          setOnboardingSource(undefined);
          message.warning(
            `数据源 ${readableSource.name || readableSource.id} 当前不支持离线同步创建，请手动选择 Source 类型`,
          );
          return;
        }

        setOnboardingSource(readableSource);
        form.setFieldValue('sourceDbType', sourceOption.value);

        const targetType = String(form.getFieldValue('targetDbType') || '');
        if (targetType) {
          const currentJobName = String(form.getFieldValue('jobName') || '').trim();
          const nextJobName = createDefaultJobName(
            sourceOption.value,
            targetType,
          );
          if (!currentJobName || currentJobName === autoJobNameRef.current) {
            form.setFieldValue('jobName', nextJobName);
          }
          autoJobNameRef.current = nextJobName;
        }
      } catch (error) {
        if (!active) return;
        setOnboardingSource(undefined);
        message.warning(
          error instanceof Error
            ? error.message
            : '来源数据源暂时无法确认，请手动选择 Source',
        );
      } finally {
        if (active) setOnboardingSourceLoading(false);
      }
    };

    void resolveSourceContext();
    return () => {
      active = false;
    };
  }, [
    connectorOptions,
    createDefaultJobName,
    form,
    open,
    sourceDataSourceId,
  ]);

  useEffect(() => {
    if (!guideMultiEnabled && form.getFieldValue('mode') === 'GUIDE_MULTI') {
      form.setFieldValue('mode', 'GUIDE_SINGLE');
    }
  }, [form, guideMultiEnabled]);

  const updateAutoJobName = (side: 'source' | 'target', value: string) => {
    const nextSourceDbType =
      side === 'source' ? value : form.getFieldValue('sourceDbType') || '';
    const nextTargetDbType =
      side === 'target' ? value : form.getFieldValue('targetDbType') || '';

    if (!nextSourceDbType || !nextTargetDbType) return;

    const currentJobName = form.getFieldValue('jobName')?.trim() || '';
    const nextJobName = createDefaultJobName(
      nextSourceDbType,
      nextTargetDbType,
    );

    if (!currentJobName || currentJobName === autoJobNameRef.current) {
      form.setFieldValue('jobName', nextJobName);
    }
    autoJobNameRef.current = nextJobName;
  };

  const handleSourceDbTypeChange = (value: string) => {
    if (
      onboardingSource &&
      normalizeDbType(onboardingSource.dbType) !== normalizeDbType(value)
    ) {
      setOnboardingSource(undefined);
    }
    updateAutoJobName('source', value);
  };

  const handleCancel = () => {
    if (submitting) return;

    form.resetFields();
    setIcon(DEFAULT_EMOJI_ICON);
    setOnboardingSource(undefined);
    autoJobNameRef.current = '';
    onCancel();
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const source = resolveEndpoint(values.sourceDbType, connectorOptions, 'SOURCE');
      const sink = resolveEndpoint(values.targetDbType, connectorOptions, 'SINK');
      if (
        values.mode === 'GUIDE_MULTI' &&
        (!isGuideMultiEnabledForDataSourceType(values.sourceDbType) ||
          !isGuideMultiEnabledForDataSourceType(values.targetDbType))
      ) {
        throw new Error(
          intl.formatMessage({
            id: 'pages.batchLinkUp.create.mode.profileSingleOnlyError',
          }),
        );
      }

      const normalizedValues: CreateSyncTaskValues = {
        jobName: values.jobName.trim(),
        jobDesc: values.jobDesc?.trim(),
        mode: values.mode,
      };

      setSubmitting(true);
      const taskId = String(await getOfflineSyncUniqueId());
      const draftPayload = buildCreatePayload(
        taskId,
        normalizedValues,
        source,
        sink,
      );
      const sourceStillMatches =
        onboardingSource &&
        normalizeDbType(onboardingSource.dbType) ===
          normalizeDbType(values.sourceDbType);
      const payload = {
        ...(sourceStillMatches
          ? bindOfflineDraftSource(draftPayload, onboardingSource)
          : draftPayload),
        editorMeta: { icon },
      };
      const savedId = await createOfflineSyncDraft(payload);
      const createdId = String(savedId ?? taskId);
      const path =
        normalizedValues.mode === 'GUIDE_MULTI'
          ? `/sync/batch-link-up/${createdId}/config/multi?scene=edit`
          : `/sync/batch-link-up/${createdId}/config/single?scene=edit`;

      form.resetFields();
      setIcon(DEFAULT_EMOJI_ICON);
      setOnboardingSource(undefined);
      autoJobNameRef.current = '';
      message.success(
        intl.formatMessage({ id: 'pages.batchLinkUp.create.success' }),
      );
      onCreated(createdId, normalizedValues.mode);
      history.push(path);
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      message.error(
        error instanceof Error
          ? error.message
          : intl.formatMessage({ id: 'pages.batchLinkUp.create.failed' }),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <ConfigProvider theme={BRAND_THEME}>
      <Drawer
        open={open}
        width={620}
        placement="right"
        closable={false}
        destroyOnClose
        maskClosable={false}
        keyboard={!submitting}
        rootStyle={brandCssVariables}
        onClose={handleCancel}
        title={
          <div className="min-w-0">
            <div className="text-[18px] font-semibold leading-7 text-[#101828]">
              {intl.formatMessage({ id: 'pages.batchLinkUp.create.title' })}
            </div>
          </div>
        }
        extra={
          <div className="flex shrink-0 items-center gap-2">
            <YakButton
              type="text"
              disabled={submitting}
              onClick={handleCancel}
              className="!h-9 !rounded-lg !px-4 !font-medium !text-[#667085]"
            >
              {intl.formatMessage({ id: 'pages.batchLinkUp.create.cancel' })}
            </YakButton>

            <YakButton
              type="primary"
              loading={submitting || onboardingSourceLoading}
              disabled={
                onboardingSourceLoading || !sourceDbType || !targetDbType
              }
              onClick={handleSubmit}
              className="!h-9 !rounded-lg !px-5 !font-medium !text-white"
            >
              {intl.formatMessage({ id: 'pages.batchLinkUp.create.submit' })}
            </YakButton>
          </div>
        }
        styles={{
          header: {
            padding: '18px 24px',
            borderBottom: '1px solid #eaecf0',
          },
          body: {
            padding: '24px',
          },
        }}
      >
        <Form<CreateSyncTaskFormValues>
          form={form}
          layout="vertical"
          requiredMark="optional"
        >
          <div className="mb-6">
            <div className="grid grid-cols-[minmax(0,1fr)_32px_minmax(0,1fr)] items-end gap-3">
              <Form.Item
                name="sourceDbType"
                label={intl.formatMessage({
                  id: 'pages.batchLinkUp.create.sourceType',
                })}
                className="!mb-0"
                rules={[
                  {
                    required: true,
                    message: intl.formatMessage({
                      id: 'pages.batchLinkUp.create.sourceTypeRequired',
                    }),
                  },
                ]}
              >
                <Select
                  showSearch
                  variant="filled"
                  options={connectorOptions}
                  placeholder={intl.formatMessage({
                    id: 'pages.batchLinkUp.create.sourceTypePlaceholder',
                  })}
                  optionFilterProp="value"
                  filterOption={(input, option) =>
                    String(option?.value || '')
                      .toLowerCase()
                      .includes(input.toLowerCase())
                  }
                  onChange={handleSourceDbTypeChange}
                />
              </Form.Item>

              <div className="flex h-8 items-center justify-center text-[#98a2b3]">
                <ArrowRightOutlined />
              </div>

              <Form.Item
                name="targetDbType"
                label={intl.formatMessage({
                  id: 'pages.batchLinkUp.create.targetType',
                })}
                className="!mb-0"
                rules={[
                  {
                    required: true,
                    message: intl.formatMessage({
                      id: 'pages.batchLinkUp.create.targetTypeRequired',
                    }),
                  },
                ]}
              >
                <Select
                  showSearch
                  variant="filled"
                  options={connectorOptions}
                  placeholder={intl.formatMessage({
                    id: 'pages.batchLinkUp.create.targetTypePlaceholder',
                  })}
                  optionFilterProp="value"
                  filterOption={(input, option) =>
                    String(option?.value || '')
                      .toLowerCase()
                      .includes(input.toLowerCase())
                  }
                  onChange={(value) => updateAutoJobName('target', value)}
                />
              </Form.Item>
            </div>

            {onboardingSourceLoading ? (
              <div className="mt-3 rounded-lg border border-[#e4e7ec] bg-[#f9fafb] px-3 py-2 text-[12px] text-[#667085]">
                正在确认来源数据源是否仍属于当前 Project…
              </div>
            ) : onboardingSource ? (
              <div className="mt-3 rounded-lg border border-[#d1e9ff] bg-[#f5fbff] px-3 py-2 text-[12px] leading-5 text-[#475467]">
                已从数据源
                <strong className="mx-1 text-[#175cd3]">
                  {onboardingSource.name || onboardingSource.id}
                </strong>
                发起创建。创建 Draft 时会将其稳定 ID 绑定为 Source；你仍可手动切换 Source 类型取消预绑定。
              </div>
            ) : null}
          </div>

          <Form.Item
            label={intl.formatMessage({ id: 'pages.batchLinkUp.create.jobName' })}
            required
            className="!mb-6"
          >
            <div className="flex items-start gap-2.5">
              <EmojiIconPicker
                value={icon}
                disabled={submitting}
                onChange={setIcon}
                className="mt-px"
              />
              <Form.Item
                name="jobName"
                noStyle
                rules={[
                  {
                    required: true,
                    message: intl.formatMessage({
                      id: 'pages.batchLinkUp.create.jobNameRequired',
                    }),
                  },
                  {
                    max: 64,
                    message: intl.formatMessage({
                      id: 'pages.batchLinkUp.create.jobNameMax',
                    }),
                  },
                ]}
              >
                <Input
                  autoFocus
                  maxLength={64}
                  showCount
                  variant="filled"
                  placeholder={intl.formatMessage({
                    id: 'pages.batchLinkUp.create.jobNamePlaceholder',
                  })}
                  className="!h-[44px] !rounded-[10px]"
                />
              </Form.Item>
            </div>
          </Form.Item>

          <Form.Item
            name="jobDesc"
            label={intl.formatMessage({ id: 'pages.batchLinkUp.create.jobDesc' })}
            rules={[
              {
                max: 200,
                message: intl.formatMessage({
                  id: 'pages.batchLinkUp.create.jobDescMax',
                }),
              },
            ]}
          >
            <Input.TextArea
              rows={5}
              maxLength={200}
              variant="filled"
              showCount
              placeholder={intl.formatMessage({
                id: 'pages.batchLinkUp.create.jobDescPlaceholder',
              })}
            />
          </Form.Item>

          <Form.Item
            name="mode"
            label={intl.formatMessage({ id: 'pages.batchLinkUp.create.mode' })}
            rules={[
              {
                required: true,
                message: intl.formatMessage({
                  id: 'pages.batchLinkUp.create.modeRequired',
                }),
              },
            ]}
          >
            <Radio.Group className="grid w-full grid-cols-2 gap-2.5">
              {modeOptions.map((option) => (
                <Radio.Button
                  key={option.value}
                  value={option.value}
                  disabled={option.disabled}
                  className={[
                    '!h-auto',
                    '!rounded-lg',
                    '!border',
                    '!border-[#e4e7ec]',
                    '!bg-white',
                    '!px-3',
                    '!py-3',
                    '!shadow-none',
                    'hover:!border-[var(--yak-brand-color-border)]',
                    'hover:!bg-[#fbfcfe]',
                    '[&.ant-radio-button-wrapper-checked]:!border-[var(--yak-brand-color)]',
                    '[&.ant-radio-button-wrapper-checked]:!bg-white',
                    '[&.ant-radio-button-wrapper-checked]:!text-inherit',
                    '[&.ant-radio-button-wrapper-checked]:!shadow-[0_0_0_2px_rgba(201,40,72,0.08)]',
                    'before:!hidden',
                  ].join(' ')}
                >
                  <div className="flex items-start gap-2.5 whitespace-normal">
                    <div
                      className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-[15px]"
                      style={{
                        color: BRAND_COLOR,
                        backgroundColor: BRAND_COLOR_SOFT_HOVER,
                      }}
                    >
                      {option.icon}
                    </div>

                    <div className="min-w-0 text-left">
                      <div className="text-[13px] font-medium leading-5 text-[#182230]">
                        {option.title}
                      </div>

                      <div className="mt-0.5 text-[11px] leading-[18px] text-[#667085]">
                        {option.description}
                      </div>
                    </div>
                  </div>
                </Radio.Button>
              ))}
            </Radio.Group>
          </Form.Item>
        </Form>
      </Drawer>
    </ConfigProvider>
  );
}
