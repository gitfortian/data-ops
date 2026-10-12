import CronSchedulerInput from '@/components/CronSchedulerEditor/CronSchedulerInput';
import { YakButton } from '@/components/ui';
import {
  Alert,
  Drawer,
  Form,
  Input,
  Modal,
  Radio,
  Select,
  Switch,
  message,
} from 'antd';
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
import {
  listAllDataSources,
  listDataSourceDatabases,
  listDataSourceSchemas,
} from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { PROVIDER_LABELS } from '../constants';

interface CollectJobDrawerProps {
  open: boolean;
  editing: CollectJobRecord | null;
  /** A project-verified URL hint, not an authorization or a saved job scope. */
  initialDataSourceId?: number;
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
const CollectJobDrawer = ({ open, editing, initialDataSourceId, onClose, onSaved }: CollectJobDrawerProps) => {
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

  useEffect(() => {
    if (!open) return;
    let active = true;
    setEffectivePolicy(null);
    setPolicyLoadError(false);
    void getEffectivePresencePolicy()
      .then((policy) => { if (active) setEffectivePolicy(policy); })
      .catch(() => { if (active) setPolicyLoadError(true); });
    return () => { active = false; };
  }, [open]);

  const providerType = Form.useWatch('providerType', form) as CollectProviderType | undefined;
  const dataSourceId = Form.useWatch('dataSourceId', form) as number | undefined;
  const databaseName = Form.useWatch('databaseName', form) as string | undefined;
  const isHarvest = (providerType ?? editing?.providerType ?? 'HARVESTED') === 'HARVESTED';
  const isEdit = Boolean(editing);

  const loadCatalog = useCallback(async () => {
    setSourceLoadError(false);
    setLoadingSources(true);
    const [sources, types] = await Promise.allSettled([
      listAllDataSources(),
      listEntityTypes('ENTITY'),
    ]);
    setDataSources(
      sources.status === 'fulfilled' ? sources.value.bizData ?? [] : [],
    );
    setSourceLoadError(sources.status !== 'fulfilled');
    setLoadingSources(false);
    const list = types.status === 'fulfilled' ? types.value ?? [] : [];
    // 投影对账通道只服务写时登记的内部实体:collectible 类型由物理采集维护,不该出现在这里。
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
        collectColumns: true,
        cronExpression: DEFAULT_CRON,
      });
    }
  }, [open, editing, form, initialDataSourceId]);

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
  }, [open, editing, initialDataSourceId, dataSources, form]);

  // 编辑物理采集任务时,按已存作用域把库/schema 下拉补载出来(选而不清,保留原值)。
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
    if (!dirty) { onClose(); return; }
    Modal.confirm({
      title: '还有未保存的采集范围',
      content: '离开将放弃本次未保存的表匹配、Schema 与调度改动。已保存任务和运行历史不受影响。',
      okText: '放弃本次编辑', okType: 'danger', cancelText: '继续编辑',
      onOk: onClose,
    });
  };

  useEffect(() => {
    if (!open || !dirty) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [open, dirty]);

  const handleSubmit = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    const harvest = values.providerType === 'HARVESTED';
    const params: CollectJobUpsertParams = {
      providerType: values.providerType,
      jobName: values.jobName.trim(),
      jobCode: values.jobCode?.trim() || undefined,
      cronExpression: values.cronExpression || DEFAULT_CRON,
      typeName: harvest ? undefined : values.typeName,
      dataSourceId: harvest ? values.dataSourceId ?? null : null,
      databaseName: harvest ? values.databaseName || null : null,
      schemaName: harvest ? values.schemaName || null : null,
      tablePattern: harvest ? values.tablePattern?.trim() || null : null,
      collectColumns: harvest ? values.collectColumns ?? true : null,
    };
    if (editing?.enabled) {
      const changed = editing.dataSourceId !== (params.dataSourceId ?? null)
        || (editing.databaseName || null) !== (params.databaseName ?? null)
        || (editing.schemaName || null) !== (params.schemaName ?? null)
        || (editing.tablePattern || null) !== (params.tablePattern ?? null)
        || (editing.cronExpression || DEFAULT_CRON) !== params.cronExpression
        || (editing.collectColumns ?? true) !== (params.collectColumns ?? true);
      if (changed) {
        const approved = await new Promise<boolean>((resolve) => {
          Modal.confirm({
            title: '确认修改已启用任务的采集范围或调度？',
            content: '任务目前已启用。保存会影响后续 Quartz 执行范围/时间，作用域改动将使旧 dry-run 证据失效，但不会自动停用或执行。请确认后再保存。',
            okText: '确认修改已启用任务', cancelText: '保留原配置',
            onOk: () => resolve(true), onCancel: () => resolve(false),
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
        message.success('任务已更新；若作用域有变更，需重新 dry-run 预演后方可启用');
      } else {
        saved = await createCollectJob(params);
        message.success('任务已创建并保存（默认停用）；手动正式运行不等于启用定时调度');
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
      destroyOnClose
      maskClosable={!saving}
      keyboard={!saving}
      onClose={requestClose}
      title={
        <div className="pr-4">
          <div className="text-[19px] font-semibold leading-7 text-[#243248]">
            {isEdit ? '编辑采集任务' : '新建采集任务'}
          </div>
          <div className="mt-1 text-[12px] font-normal leading-5 text-[#8794a8]">
            先明确采集对象，再决定预演与执行方式；保存不会自动触发采集。
          </div>
        </div>
      }
      footer={
        <div className="flex items-center justify-between gap-3">
          <span className="text-[12px] text-[#8997a8]">
            {dirty ? '有尚未保存的修改' : '所有更改均需明确保存'}
          </span>
          <div className="flex items-center gap-2">
            <YakButton disabled={saving} onClick={requestClose} className="!h-9 !rounded-lg !px-5">取消</YakButton>
            <YakButton type="primary" loading={saving} onClick={() => void handleSubmit()}
              className="!h-9 !rounded-lg !px-5 !text-white">保存任务</YakButton>
          </div>
        </div>
      }
      styles={{
        header: { padding: '20px 24px', borderBottom: '1px solid #e9edf3' },
        body: { padding: '20px 24px 28px', background: '#f7f8fb' },
        footer: { padding: '14px 24px', borderTop: '1px solid #e9edf3', background: '#fff' },
      }}
    >
      <Form
        form={form}
        layout="vertical"
        onValuesChange={() => setDirty(true)}
        className="space-y-4"
      >
        <div className="rounded-xl border border-solid border-[#e7eaf0] bg-[#f0f5fb] px-4 py-3">
          <div className="text-[13px] font-semibold text-[#3b5f87]">
            {isEdit ? '配置修改提醒' : '新任务默认停用'}
          </div>
          <div className="mt-1 text-[12px] leading-5 text-[#637b97]">
            {isEdit
              ? '修改来源、Schema 或表范围后，旧预演结论可能失效；已启用任务的下次调度范围也可能改变，保存前会再次确认。'
              : '保存只创建任务，不采集表结构。建议先预演核对范围，再明确点击正式采集；如需周期执行，还要单独启用调度。'}
          </div>
        </div>

        <section className="rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5">
          <div className="mb-4 flex items-center gap-3">
            <span className="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-[#edf3fa] text-[12px] font-semibold text-[#597fa8]">01</span>
            <div>
              <h3 className="m-0 text-[15px] font-semibold text-[#26364a]">任务信息</h3>
              <p className="mb-0 mt-0.5 text-[12px] text-[#8a98aa]">让后续使用者认得出这个任务的目的。</p>
            </div>
          </div>
          <div className="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <Form.Item
              name="jobName"
              label="任务名称"
              rules={[{ required: true, message: '请输入任务名称' }, { max: 128, message: '不能超过 128 个字符' }]}
            >
              <Input placeholder="例如：订单库表结构采集" />
            </Form.Item>
            <Form.Item name="jobCode" label="任务编码" tooltip="稳定身份，留空自动生成；编辑时不可修改"
              rules={[{ max: 64, message: '不能超过 64 个字符' }]}>
              <Input disabled={isEdit} placeholder="留空自动生成" />
            </Form.Item>
          </div>
          <Form.Item name="providerType" label="任务类型" className="!mb-2">
            <Radio.Group optionType="button" buttonStyle="solid" disabled={isEdit}>
              <Radio.Button value="HARVESTED">物理采集</Radio.Button>
              <Radio.Button value="REGISTERED" disabled={!isEdit}>投影对账 · 尚未开放</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <div className="text-[12px] leading-5 text-[#8b98a9]">
            物理采集读取源库的表与列。内部投影对账已有登记和失败补偿能力，但独立任务执行器尚未开放。
          </div>
        </section>

        <section className="rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5">
          <div className="mb-4 flex items-center gap-3">
            <span className="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-[#edf3fa] text-[12px] font-semibold text-[#597fa8]">02</span>
            <div>
              <h3 className="m-0 text-[15px] font-semibold text-[#26364a]">{isHarvest ? '选择来源与采集范围' : '登记对象范围'}</h3>
              <p className="mb-0 mt-0.5 text-[12px] text-[#8a98aa]">
                {isHarvest ? '范围以保存后的任务配置为准，不会因为页面筛选自动改变。' : '此类任务目前只能维护已保存配置。'}
              </p>
            </div>
          </div>
          {isHarvest ? (
            <>
              {sourceLoadError ? (
                <Alert className="!mb-4" type="warning" showIcon message="数据源清单读取失败"
                  description="当前无法确认可选择的数据源，请检查服务或权限后重新打开表单。" />
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
                    placeholder={databaseName ? '默认 Schema（留空）' : '全部 Schema（留空）'}
                    options={schemas.map((name) => ({ value: name, label: name }))}
                  />
                </Form.Item>
              </div>
              <Form.Item
                name="tablePattern"
                label="表名匹配规则"
                tooltip="采用 SQL LIKE 语义：% 匹配任意字符，_ 匹配单字符；不是文件名通配符 *"
                className="!mb-2"
              >
                <Input placeholder="例如：trade_%；留空表示范围内全部表" />
              </Form.Item>
              <div className="mb-4 text-[12px] leading-5 text-[#8b98a9]">
                留空按所选库 / Schema 的实际范围采集。建议初次测试用明确的库与表范围，避免意外扫描其他业务库。
              </div>
              <div className="flex items-center justify-between gap-4 rounded-xl border border-solid border-[#e9edf3] bg-[#fafbfd] px-4 py-3">
                <div className="min-w-0">
                  <div className="text-[13px] font-medium text-[#34445a]">同时采集字段结构</div>
                  <div className="mt-1 text-[12px] leading-5 text-[#8a98aa]">默认开启，读取字段名、数据类型、可空性等元数据；关闭后只采集表级信息。</div>
                </div>
                <Form.Item name="collectColumns" valuePropName="checked" className="!mb-0">
                  <Switch />
                </Form.Item>
              </div>
            </>
          ) : (
            <>
              <Alert type="warning" showIcon className="!mb-4" message="投影对账任务执行器尚未开放"
                description="当前可查看和维护历史任务定义，但不能进行预演、手动运行或定时执行。" />
              <Form.Item
                name="typeName"
                label="登记实体类型"
                rules={[{ required: true, message: '请选择登记实体类型' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择内部实体类型"
                  options={entityTypes.map((type) => ({
                    value: type.typeName,
                    label: type.displayName ? type.displayName + '（' + type.typeName + '）' : type.typeName,
                  }))}
                />
              </Form.Item>
            </>
          )}
        </section>

        <section className="rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5">
          <div className="mb-4 flex items-center gap-3">
            <span className="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-[#edf3fa] text-[12px] font-semibold text-[#597fa8]">03</span>
            <div>
              <h3 className="m-0 text-[15px] font-semibold text-[#26364a]">执行安排</h3>
              <p className="mb-0 mt-0.5 text-[12px] text-[#8a98aa]">保存配置 ≠ 执行采集 ≠ 启用周期调度。</p>
            </div>
          </div>
          <Form.Item
            name="cronExpression"
            label="周期调度表达式"
            tooltip="只有用户另行启用定时任务且调度器可用时才生效"
            className="!mb-2"
          >
            <CronSchedulerInput placeholder={DEFAULT_CRON} />
          </Form.Item>
          <div className="text-[12px] leading-5 text-[#8a98aa]">
            默认每天 03:00。保存后仍是停用状态；需要正式采集时可在列表手工运行。
          </div>
        </section>

        {isHarvest ? (
          <section className="rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5">
            <div className="mb-3 flex items-center gap-3">
              <span className="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-[#edf3fa] text-[12px] font-semibold text-[#597fa8]">04</span>
              <div>
                <h3 className="m-0 text-[15px] font-semibold text-[#26364a]">缺席安全保护</h3>
                <p className="mb-0 mt-0.5 text-[12px] text-[#8a98aa]">保护策略由服务端管理，任务内不可更改。</p>
              </div>
            </div>
            <Alert
              type={policyLoadError ? 'warning' : 'info'}
              showIcon
              message={policyLoadError ? '当前无法核验缺席保护参数' : effectivePolicy ? '已读取全局保护策略' : '正在读取保护策略'}
              description={
                effectivePolicy
                  ? '表缺席比例超过 ' + effectivePolicy.collapseThresholdPct + '%（至少 2 张缺席）将熔断为 SUSPECT；连续 ' + effectivePolicy.missingRounds + ' 轮有效采集缺席后才可标 GONE。任务历史存储值不代表实际执行阈值。'
                  : policyLoadError
                    ? '无法确认实际阈值，请先核验服务器配置后再正式执行。'
                    : '以服务端当前实际生效的策略为准。'
              }
            />
          </section>
        ) : null}
      </Form>
    </Drawer>
  );
};

export default CollectJobDrawer;
