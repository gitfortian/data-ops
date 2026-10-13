import '../index.less';
import CronSchedulerInput from '@/components/CronSchedulerEditor/CronSchedulerInput';
import { YakButton } from '@/components/ui';
import { Alert, Button, Collapse, Drawer, Form, Input, Modal, Radio, Select, Switch, message } from 'antd';
import { describeCollectSchedule } from '../constants';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  createCollectJob,
  getEffectivePresencePolicy,
  listEntityTypes,
  updateCollectJob,
} from '@/services/metadata/api';
import type {
  CollectJobRecord,
  CollectJobUpsertParams,
  CollectProviderType,
  EffectivePresencePolicy,
  EntityTypeView,
} from '@/services/metadata/types';
import { listAllDataSources, listDataSourceDatabases, listDataSourceSchemas } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';

export interface CollectJobInitialScope {
  databaseName?: string;
  schemaName?: string;
  sourceTableName?: string;
  jobName?: string;
}

interface CollectJobDrawerProps {
  open: boolean;
  editing: CollectJobRecord | null;
  /** A project-verified URL hint, not an authorization or a saved job scope. */
  initialDataSourceId?: number;
  initialScope?: CollectJobInitialScope;
  onClose: () => void;
  onSaved: (saved: CollectJobRecord) => void;
}

interface JobFormValues {
  providerType: CollectProviderType;
  jobCode?: string;
  jobName: string;
  dataSourceId?: number;
  databaseName?: string;
  schemaName?: string;
  tablePattern?: string;
  collectColumns?: boolean;
  typeName?: string;
  cronExpression?: string;
}

const DEFAULT_CRON = '0 0 3 * * ?';

/** 物理表名匹配式是 SQL LIKE 语义(% 通配),留空即整库采集。 */
const CollectJobDrawer = ({
  open,
  editing,
  initialDataSourceId,
  initialScope,
  onClose,
  onSaved,
}: CollectJobDrawerProps) => {
  const [form] = Form.useForm<JobFormValues>();
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [sourceLoadError, setSourceLoadError] = useState(false);
  const [loadingSources, setLoadingSources] = useState(false);
  const [databases, setDatabases] = useState<string[]>([]);
  const [schemas, setSchemas] = useState<string[]>([]);
  const [entityTypes, setEntityTypes] = useState<EntityTypeView[]>([]);
  const [loadingDatabases, setLoadingDatabases] = useState(false);
  const [loadingSchemas, setLoadingSchemas] = useState(false);
  const [effectivePolicy, setEffectivePolicy] = useState<EffectivePresencePolicy | null>(null);
  const [policyLoadError, setPolicyLoadError] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  useEffect(() => {
    setScheduleOpen(false);
  }, [open, editing?.id]);

  useEffect(() => {
    if (!open) return;
    let active = true;
    setEffectivePolicy(null);
    setPolicyLoadError(false);
    void getEffectivePresencePolicy()
      .then((policy) => {
        if (active) setEffectivePolicy(policy);
      })
      .catch(() => {
        if (active) setPolicyLoadError(true);
      });
    return () => {
      active = false;
    };
  }, [open]);

  const providerType = Form.useWatch('providerType', form) as CollectProviderType | undefined;
  const dataSourceId = Form.useWatch('dataSourceId', form) as number | undefined;
  const cronExpression = Form.useWatch('cronExpression', form) as string | undefined;
  const isHarvest = (providerType ?? editing?.providerType ?? 'HARVESTED') === 'HARVESTED';
  const isEdit = Boolean(editing);

  const loadCatalog = useCallback(async () => {
    setSourceLoadError(false);
    setLoadingSources(true);
    const [sources, types] = await Promise.allSettled([listAllDataSources(), listEntityTypes('ENTITY')]);
    setDataSources(sources.status === 'fulfilled' ? (sources.value.bizData ?? []) : []);
    setSourceLoadError(sources.status !== 'fulfilled');
    setLoadingSources(false);
    const list = types.status === 'fulfilled' ? (types.value ?? []) : [];
    // 投影对账通道只服务写时登记的内部实体:collectible 类型由数据源采集维护,不该出现在这里。
    setEntityTypes(list.filter((t) => t.status === 'ACTIVE' && !t.collectible));
  }, []);

  useEffect(() => {
    if (!open) return;
    void loadCatalog();
  }, [open, loadCatalog]);

  useEffect(() => {
    if (!open) return;
    setDirty(false);
    if (editing) {
      form.setFieldsValue({
        providerType: editing.providerType,
        jobCode: editing.jobCode,
        jobName: editing.jobName,
        dataSourceId: editing.dataSourceId ?? undefined,
        databaseName: editing.databaseName ?? undefined,
        schemaName: editing.schemaName ?? undefined,
        tablePattern: editing.tablePattern ?? undefined,
        collectColumns: editing.collectColumns ?? true,
        typeName: editing.typeName ?? undefined,
        cronExpression: editing.cronExpression || DEFAULT_CRON,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        providerType: 'HARVESTED',
        dataSourceId: initialDataSourceId,
        jobName: initialScope?.jobName,
        databaseName: initialScope?.databaseName,
        schemaName: initialScope?.schemaName,
        collectColumns: true,
        cronExpression: DEFAULT_CRON,
      });
    }
  }, [open, editing, form, initialDataSourceId, initialScope]);

  const refreshDatabases = async (id: number) => {
    setLoadingDatabases(true);
    setDatabases([]);
    try {
      const list = await listDataSourceDatabases(id);
      setDatabases(list ?? []);
    } catch {
      setDatabases([]);
    } finally {
      setLoadingDatabases(false);
    }
  };

  const refreshSchemas = async (id: number, database?: string) => {
    setLoadingSchemas(true);
    setSchemas([]);
    try {
      const list = await listDataSourceSchemas(id, database);
      setSchemas(list ?? []);
    } catch {
      setSchemas([]);
    } finally {
      setLoadingSchemas(false);
    }
  };

  // Verify a prefilled source against the drawer's fresh project-scoped Owner listing;
  // never treat a navigation hint as a valid source if the current list rejects it.
  useEffect(() => {
    if (!open || editing || !initialDataSourceId || dataSources.length === 0) return;
    if (!dataSources.some((source) => Number(source.id) === initialDataSourceId)) {
      form.setFieldValue('dataSourceId', undefined);
      message.warning('原数据源在当前项目下不可用，请重新选择');
      return;
    }
    void refreshDatabases(initialDataSourceId);
    if (initialScope?.databaseName) void refreshSchemas(initialDataSourceId, initialScope.databaseName);
  }, [open, editing, initialDataSourceId, initialScope, dataSources, form]);

  // 编辑数据源采集任务时,按已存作用域把库/schema 下拉补载出来(选而不清,保留原值)。
  useEffect(() => {
    if (!open || !editing) return;
    if (editing.providerType === 'HARVESTED' && editing.dataSourceId) {
      void refreshDatabases(editing.dataSourceId);
      if (editing.databaseName) void refreshSchemas(editing.dataSourceId, editing.databaseName);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing]);

  const onDataSourceChange = (id?: number) => {
    form.setFieldsValue({ databaseName: undefined, schemaName: undefined });
    setSchemas([]);
    if (id != null) void refreshDatabases(id);
  };

  const onDatabaseChange = (database?: string) => {
    form.setFieldsValue({ schemaName: undefined });
    const ds = form.getFieldValue('dataSourceId');
    if (ds != null) void refreshSchemas(Number(ds), database);
  };

  const dataSourceOptions = useMemo(
    () =>
      dataSources.map((ds) => ({
        value: Number(ds.id),
        label: `${ds.name ?? ds.id}${ds.dbType ? ` · ${ds.dbType}` : ''}`,
      })),
    [dataSources],
  );

  const requestClose = () => {
    if (saving) return;
    if (!dirty) {
      onClose();
      return;
    }
    Modal.confirm({
      title: '还有未保存的采集范围',
      content: '离开将放弃本次未保存的表匹配、Schema 与调度改动。已保存任务和运行历史不受影响。',
      okText: '放弃本次编辑',
      okType: 'danger',
      cancelText: '继续编辑',
      onOk: onClose,
    });
  };

  useEffect(() => {
    if (!open || !dirty) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [open, dirty]);

  const handleSubmit = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const harvest = values.providerType === 'HARVESTED';
    // The field may be prefilled from a cross-domain URL. A valid Form value is
    // not proof that this source still belongs to the active project.
    if (harvest && (loadingSources || sourceLoadError)) {
      message.warning('尚未核验当前项目的数据源列表，请稍后重试');
      return;
    }
    if (harvest && !dataSources.some((source) => Number(source.id) === values.dataSourceId)) {
      message.warning('该数据源不在当前项目的可用列表中，请重新选择');
      return;
    }
    const params: CollectJobUpsertParams = {
      providerType: values.providerType,
      jobName: values.jobName.trim(),
      jobCode: values.jobCode?.trim() || undefined,
      cronExpression: values.cronExpression || DEFAULT_CRON,
      typeName: harvest ? undefined : values.typeName,
      dataSourceId: harvest ? (values.dataSourceId ?? null) : null,
      databaseName: harvest ? values.databaseName || null : null,
      schemaName: harvest ? values.schemaName || null : null,
      tablePattern: harvest ? values.tablePattern?.trim() || null : null,
      collectColumns: harvest ? (values.collectColumns ?? true) : null,
    };
    if (editing?.enabled) {
      const changed =
        editing.dataSourceId !== (params.dataSourceId ?? null) ||
        (editing.databaseName || null) !== (params.databaseName ?? null) ||
        (editing.schemaName || null) !== (params.schemaName ?? null) ||
        (editing.tablePattern || null) !== (params.tablePattern ?? null) ||
        (editing.cronExpression || DEFAULT_CRON) !== params.cronExpression ||
        (editing.collectColumns ?? true) !== (params.collectColumns ?? true);
      if (changed) {
        const approved = await new Promise<boolean>((resolve) => {
          Modal.confirm({
            title: '确认修改已启用任务的采集范围或调度？',
            content: '任务已启用，保存后将按新范围和计划执行。采集范围变化后需重新预览。',
            okText: '确认修改已启用任务',
            cancelText: '保留原配置',
            onOk: () => resolve(true),
            onCancel: () => resolve(false),
          });
        });
        if (!approved) return;
      }
    }
    setSaving(true);
    try {
      let saved: CollectJobRecord;
      if (editing) {
        saved = await updateCollectJob(editing.id, params);
        message.success('任务已更新');
      } else {
        saved = await createCollectJob(params);
        message.success('任务已创建');
      }
      setDirty(false);
      onSaved(saved);
    } catch {
      // 全局错误提示已展示业务原因（编码重复 / 作用域非法）
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      width="min(700px, 100vw)"
      placement="right"
      closable
      className="collect-editor"
      destroyOnClose
      maskClosable={!saving}
      keyboard={!saving}
      onClose={requestClose}
      title={
        <div className="pr-4">
          <div className="text-[19px] font-semibold leading-7 text-[#243248]">
            {isEdit ? '编辑采集任务' : '新建采集任务'}
          </div>
        </div>
      }
      footer={
        <div className="flex items-center justify-between gap-3">
          <span className="text-[12px] text-[#8997a8]">{dirty ? '有未保存的修改' : ''}</span>
          <div className="flex items-center gap-2">
            <YakButton disabled={saving} onClick={requestClose} className="!h-9 !rounded-lg !px-5">
              取消
            </YakButton>
            <YakButton
              type="primary"
              loading={saving}
              onClick={() => void handleSubmit()}
              className="!h-9 !rounded-lg !px-5 !text-white"
            >
              保存任务
            </YakButton>
          </div>
        </div>
      }
      styles={{
        header: { padding: '20px 24px', borderBottom: '1px solid #e9edf3' },
        body: { padding: '20px 24px 28px', background: '#fff' },
        footer: { padding: '14px 24px', borderTop: '1px solid #e9edf3', background: '#fff' },
      }}
    >
      <Form form={form} layout="vertical" onValuesChange={() => setDirty(true)} className="space-y-4">
        {!isEdit && initialScope?.sourceTableName ? (
          <Alert
            type="info"
            showIcon
            className="!mb-0"
            message={`从表「${initialScope.sourceTableName}」进入任务定义`}
            description="已带入库和 Schema，表范围默认全部表。"
          />
        ) : null}

        <section className="collect-form-section">
          <h3>任务信息</h3>
          <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <Form.Item
              name="jobName"
              label="任务名称"
              rules={[
                { required: true, message: '请输入任务名称' },
                { max: 128, message: '不能超过 128 个字符' },
              ]}
            >
              <Input placeholder="例如：订单库表结构采集" />
            </Form.Item>
            <Form.Item
              name="jobCode"
              label="任务编码"
              tooltip="留空自动生成，保存后不可修改"
              rules={[{ max: 64, message: '不能超过 64 个字符' }]}
            >
              <Input disabled={isEdit} placeholder="留空自动生成" />
            </Form.Item>
          </div>
          <Form.Item name="providerType" label="任务类型" className="!mb-2">
            <Radio.Group optionType="button" buttonStyle="solid" disabled={isEdit}>
              <Radio.Button value="HARVESTED">数据源采集</Radio.Button>
              <Radio.Button value="REGISTERED" disabled={!isEdit}>
                系统对账 · 暂不可执行
              </Radio.Button>
            </Radio.Group>
          </Form.Item>
        </section>

        <section className="collect-form-section">
          <h3>{isHarvest ? '选择来源与采集范围' : '登记对象范围'}</h3>
          {isHarvest ? (
            <>
              {sourceLoadError ? (
                <Alert
                  className="!mb-4"
                  type="warning"
                  showIcon
                  message="数据源清单读取失败"
                  description="当前无法确认可选择的数据源，请检查服务或权限后重新打开表单。"
                />
              ) : null}
              <Form.Item
                name="dataSourceId"
                label="数据源"
                rules={[{ required: true, message: '请选择要采集的数据源' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择当前项目的数据源"
                  loading={loadingSources}
                  options={dataSourceOptions}
                  onChange={(value?: number) => onDataSourceChange(value)}
                />
              </Form.Item>
              <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
                <Form.Item name="databaseName" label="数据库">
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    disabled={dataSourceId == null}
                    loading={loadingDatabases}
                    placeholder={dataSourceId == null ? '先选择数据源' : '全部库（留空）'}
                    options={databases.map((name) => ({ value: name, label: name }))}
                    onChange={(value?: string) => onDatabaseChange(value)}
                  />
                </Form.Item>
                <Form.Item name="schemaName" label="Schema">
                  <Select
                    allowClear
                    showSearch
                    optionFilterProp="label"
                    disabled={dataSourceId == null}
                    loading={loadingSchemas}
                    placeholder="全部 Schema（留空）"
                    options={schemas.map((name) => ({ value: name, label: name }))}
                  />
                </Form.Item>
              </div>
              <Form.Item
                name="tablePattern"
                label="表名匹配式"
                tooltip="% 表示任意多个字符，_ 表示单个字符；留空采集全部表"
                className="!mb-2"
              >
                <Input placeholder="例如：trade_%；留空表示范围内全部表" />
              </Form.Item>

              <div className="flex items-center justify-between gap-4 rounded-xl border border-solid border-[#e9edf3] bg-[#fafbfd] px-4 py-3">
                <div className="min-w-0">
                  <div className="text-[13px] font-medium text-[#34445a]">同时采集字段结构</div>
                  <div className="mt-1 text-[12px] leading-5 text-[#8a98aa]">字段名、类型、是否可空等信息</div>
                </div>
                <Form.Item name="collectColumns" valuePropName="checked" className="!mb-0">
                  <Switch />
                </Form.Item>
              </div>
            </>
          ) : (
            <>
              <Alert
                type="warning"
                showIcon
                className="!mb-4"
                message="此类任务暂不支持执行"
                description="可查看和编辑已保存的任务。"
              />
              <Form.Item
                name="typeName"
                label="登记实体类型"
                rules={[{ required: true, message: '请选择登记实体类型' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择元数据类型"
                  options={entityTypes.map((type) => ({
                    value: type.typeName,
                    label: type.displayName ? type.displayName + '（' + type.typeName + '）' : type.typeName,
                  }))}
                />
              </Form.Item>
            </>
          )}
        </section>

        <section className="collect-form-section">
          <h3>调度计划</h3>
          <div className="collect-form-schedule">
            <span>{describeCollectSchedule(cronExpression || DEFAULT_CRON)}</span>
            <Button type="text" onClick={() => setScheduleOpen((value) => !value)}>
              {scheduleOpen ? '收起' : '设置计划'}
            </Button>
          </div>
          <Form.Item
            name="cronExpression"
            label="调度设置"
            style={{ display: scheduleOpen ? 'block' : 'none' }}
            tooltip="启用调度后按此计划执行"
            className="!mb-2"
          >
            <CronSchedulerInput placeholder={DEFAULT_CRON} />
          </Form.Item>
        </section>

        {isHarvest ? (
          <Collapse
            ghost
            items={[
              {
                key: 'protection',
                label: '异常保护策略',
                children: (
                  <Alert
                    type={policyLoadError ? 'warning' : 'info'}
                    showIcon
                    message={policyLoadError ? '保护策略暂不可用' : effectivePolicy ? '全局保护策略' : '正在读取策略'}
                    description={
                      effectivePolicy
                        ? '缺失表达到 2 张且比例超过 ' +
                          effectivePolicy.collapseThresholdPct +
                          '% 时自动停止；连续 ' +
                          effectivePolicy.missingRounds +
                          ' 次有效采集未发现的表标记为已移除。'
                        : policyLoadError
                          ? '请稍后重新打开任务查看。'
                          : undefined
                    }
                  />
                ),
              },
            ]}
          />
        ) : null}
      </Form>
    </Drawer>
  );
};

export default CollectJobDrawer;
