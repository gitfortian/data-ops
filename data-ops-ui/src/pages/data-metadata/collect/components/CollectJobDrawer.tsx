import CronSchedulerInput from '@/components/CronSchedulerEditor/CronSchedulerInput';
import { YakButton } from '@/components/ui';
import {
  Alert,
  Drawer,
  Form,
  Input,
  InputNumber,
  Radio,
  Select,
  Switch,
  message,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

import {
  createCollectJob,
  listEntityTypes,
  updateCollectJob,
} from '@/services/metadata/api';
import type {
  CollectJobRecord,
  CollectJobUpsertParams,
  CollectProviderType,
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
  onClose: () => void;
  onSaved: () => void;
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
  collapseThresholdPct?: number;
  missingRounds?: number;
}

const DEFAULT_CRON = '0 0 3 * * ?';

/** 物理表名匹配式是 SQL LIKE 语义(% 通配),留空即整库采集。 */
const CollectJobDrawer = ({ open, editing, onClose, onSaved }: CollectJobDrawerProps) => {
  const [form] = Form.useForm<JobFormValues>();
  const [saving, setSaving] = useState(false);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [databases, setDatabases] = useState<string[]>([]);
  const [schemas, setSchemas] = useState<string[]>([]);
  const [entityTypes, setEntityTypes] = useState<EntityTypeView[]>([]);
  const [loadingDatabases, setLoadingDatabases] = useState(false);
  const [loadingSchemas, setLoadingSchemas] = useState(false);

  const providerType = Form.useWatch('providerType', form) as CollectProviderType | undefined;
  const dataSourceId = Form.useWatch('dataSourceId', form) as number | undefined;
  const databaseName = Form.useWatch('databaseName', form) as string | undefined;
  const isHarvest = (providerType ?? editing?.providerType ?? 'HARVESTED') === 'HARVESTED';
  const isEdit = Boolean(editing);

  const loadCatalog = useCallback(async () => {
    const [sources, types] = await Promise.allSettled([
      listAllDataSources(),
      listEntityTypes('ENTITY'),
    ]);
    setDataSources(
      sources.status === 'fulfilled' ? sources.value.bizData ?? [] : [],
    );
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
        collapseThresholdPct: editing.collapseThresholdPct ?? 30,
        missingRounds: editing.missingRounds ?? 2,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        providerType: 'HARVESTED',
        collectColumns: true,
        cronExpression: DEFAULT_CRON,
        collapseThresholdPct: 30,
        missingRounds: 2,
      });
    }
  }, [open, editing, form]);

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

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const harvest = values.providerType === 'HARVESTED';
    const params: CollectJobUpsertParams = {
      providerType: values.providerType,
      jobName: values.jobName.trim(),
      jobCode: values.jobCode?.trim() || undefined,
      cronExpression: values.cronExpression || DEFAULT_CRON,
      collapseThresholdPct: values.collapseThresholdPct ?? null,
      missingRounds: values.missingRounds ?? null,
      typeName: harvest ? undefined : values.typeName,
      dataSourceId: harvest ? values.dataSourceId ?? null : null,
      databaseName: harvest ? values.databaseName || null : null,
      schemaName: harvest ? values.schemaName || null : null,
      tablePattern: harvest ? values.tablePattern?.trim() || null : null,
      collectColumns: harvest ? values.collectColumns ?? true : null,
    };
    setSaving(true);
    try {
      if (editing) {
        await updateCollectJob(editing.id, params);
        message.success('任务已更新；若作用域有变更，需重新 dry-run 预演后方可启用');
      } else {
        await createCollectJob(params);
        message.success('任务已创建（默认停用，需先通过一次 dry-run 预演）');
      }
      onSaved();
    } catch {
      // 全局错误提示已展示业务原因（编码重复 / 作用域非法）
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      width={560}
      placement="right"
      closable={false}
      destroyOnClose
      maskClosable={!saving}
      keyboard={!saving}
      onClose={onClose}
      title={
        <div className="text-[18px] font-semibold leading-7 text-[#101828]">
          {isEdit ? '编辑采集任务' : '新建采集任务'}
        </div>
      }
      extra={
        <div className="flex items-center gap-2">
          <YakButton disabled={saving} onClick={onClose} className="!h-9 !rounded-lg !px-4">
            取消
          </YakButton>
          <YakButton
            type="primary"
            loading={saving}
            onClick={() => void handleSubmit()}
            className="!h-9 !rounded-lg !px-5 !text-white"
          >
            保存
          </YakButton>
        </div>
      }
      styles={{
        header: { padding: '18px 24px', borderBottom: '1px solid #eaecf0' },
        body: { padding: 24 },
      }}
    >
      <Form form={form} layout="vertical" requiredMark="optional">
        {isEdit ? (
          <Alert
            type="info"
            showIcon
            className="!mb-4"
            message="修改作用域（数据源 / 库 / schema / 表匹配式）会使旧的 dry-run 预演结论失效，需重新预演才能启用。"
          />
        ) : (
          <Alert
            type="info"
            showIcon
            className="!mb-4"
            message="新建任务默认停用，必须先跑一次通过的 dry-run 预演才允许启用。"
          />
        )}

        <Form.Item name="providerType" label="通道" className="!mb-4">
          <Radio.Group optionType="button" buttonStyle="solid" disabled={isEdit}>
            {(Object.keys(PROVIDER_LABELS) as CollectProviderType[]).map((value) => (
              <Radio.Button key={value} value={value}>
                {PROVIDER_LABELS[value]}
              </Radio.Button>
            ))}
          </Radio.Group>
        </Form.Item>

        <div className="mb-4 grid grid-cols-2 gap-3">
          <Form.Item
            name="jobName"
            label="任务名称"
            required
            rules={[
              { required: true, message: '请输入任务名称' },
              { max: 128, message: '不能超过 128 个字符' },
            ]}
          >
            <Input variant="filled" placeholder="如：订单库表结构采集" className="!h-[44px] !rounded-[10px]" />
          </Form.Item>
          <Form.Item
            name="jobCode"
            label="任务编码"
            tooltip="留空自动生成；它是身份不是内容，编辑时不可改"
            rules={[{ max: 64, message: '不能超过 64 个字符' }]}
          >
            <Input
              variant="filled"
              disabled={isEdit}
              placeholder="留空自动生成"
              className="!h-[44px] !rounded-[10px]"
            />
          </Form.Item>
        </div>

        {isHarvest ? (
          <>
            <Form.Item
              name="dataSourceId"
              label="数据源"
              required
              rules={[{ required: true, message: '物理采集任务必须绑定数据源' }]}
            >
              <Select
                variant="filled"
                showSearch
                optionFilterProp="label"
                placeholder="选择要采集的数据源"
                loading={dataSources.length === 0}
                options={dataSourceOptions}
                onChange={(value?: number) => onDataSourceChange(value)}
              />
            </Form.Item>

            <div className="mb-4 grid grid-cols-2 gap-3">
              <Form.Item name="databaseName" label="数据库">
                <Select
                  variant="filled"
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  disabled={dataSourceId == null}
                  loading={loadingDatabases}
                  placeholder={dataSourceId == null ? '先选数据源' : '全部库（留空）'}
                  options={databases.map((name) => ({ value: name, label: name }))}
                  onChange={(value?: string) => onDatabaseChange(value)}
                />
              </Form.Item>
              <Form.Item name="schemaName" label="Schema">
                <Select
                  variant="filled"
                  allowClear
                  showSearch
                  optionFilterProp="label"
                  disabled={dataSourceId == null}
                  loading={loadingSchemas}
                  placeholder={databaseName ? '选择 schema（留空=默认）' : '全部 schema（留空）'}
                  options={schemas.map((name) => ({ value: name, label: name }))}
                />
              </Form.Item>
            </div>

            <Form.Item name="tablePattern" label="表名匹配式" tooltip="SQL LIKE 语义，% 通配；留空采集该作用域下全部表">
              <Input variant="filled" placeholder="如 ods_* 或留空采集全部" className="!h-[44px] !rounded-[10px]" />
            </Form.Item>

            <Form.Item
              name="collectColumns"
              label="采集列级元数据"
              valuePropName="checked"
              tooltip="关闭后只采表级；一期默认采到列"
              className="!mb-4"
            >
              <Switch />
            </Form.Item>
          </>
        ) : (
          <Form.Item
            name="typeName"
            label="对账实体类型"
            required
            tooltip="投影对账校验写时登记通道落库的实体是否与源域一致，只覆盖内部登记类型"
            rules={[{ required: true, message: '请选择实体类型' }]}
          >
            <Select
              variant="filled"
              showSearch
              optionFilterProp="label"
              placeholder="选择内部实体类型"
              options={entityTypes.map((t) => ({
                value: t.typeName,
                label: t.displayName ? `${t.displayName}（${t.typeName}）` : t.typeName,
              }))}
            />
          </Form.Item>
        )}

        <Form.Item
          name="cronExpression"
          label="调度周期"
          tooltip="点选周期与时分生成表达式，无需手写"
          className="mt-2"
        >
          <CronSchedulerInput placeholder={DEFAULT_CRON} />
        </Form.Item>

        <div className="mb-2 mt-2 text-[13px] font-medium text-[#101828]">缺失与熔断</div>
        <div className="mb-4 grid grid-cols-2 gap-3">
          <Form.Item
            name="collapseThresholdPct"
            label="熔断阈值"
            tooltip="单轮缺失表数占上一轮总量的百分比超过该值即判熔断(SUSPECT)，整轮不执行删除"
          >
            <InputNumber variant="filled" min={1} max={100} precision={0} addonAfter="%" className="!w-full !rounded-[10px]" />
          </Form.Item>
          <Form.Item
            name="missingRounds"
            label="确认轮数"
            tooltip="连续缺失多少轮才真正把对象标记为消失(GONE)"
          >
            <InputNumber variant="filled" min={1} max={5} precision={0} addonAfter="轮" className="!w-full !rounded-[10px]" />
          </Form.Item>
        </div>
      </Form>
    </Drawer>
  );
};

export default CollectJobDrawer;
