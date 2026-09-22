import { listDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { MODELING_DIALECT_OPTIONS, dialectOfDbType } from '@/pages/modeling/constants';
import {
  importModelingTables,
  listModelingImportTables,
  previewModelingImportColumns,
} from '@/services/modeling/import';
import { captureModelingStandard } from '@/services/modeling/recommend';
import type {
  ModelingImportAction,
  ModelingImportColumnView,
  ModelingImportResult,
  ModelingImportTableView,
} from '@/services/modeling/types';
import { listSemanticLayers } from '@/services/semantic/api';
import { history } from '@umijs/max';
import { Button, Checkbox, Drawer, Form, Input, Modal, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';

/** 单表导入去向文案(2026-09-17):已有字段跳过、已有空模型补字段分别说明原因。 */
const IMPORT_ACTION_LABELS: Record<ModelingImportAction, string> = {
  CREATED: '新建模型',
  FILLED: '已补全字段',
  SKIPPED: '跳过',
  FAILED: '失败',
};

/** 标准字段匹配方式文案(与后端 StandardFieldMatcher 口径一致)。 */
const MATCH_LABELS: Record<string, string> = {
  exact: '名称精确',
  fuzzy: '名称相似',
  comment: '注释匹配',
};

/** event_time 来源字段自动识别:优先含 time,其次含 date/dt(与后端一致)。 */
const detectEventTimeField = (columns: ModelingImportColumnView[]): string | undefined => {
  const names = (columns ?? []).map((column) => column.name.toLowerCase());
  return names.find((name) => name.includes('time')) ?? names.find((name) => name.includes('date') || name === 'dt');
};

interface ReverseImportDrawerProps {
  open: boolean;
  directories: { id?: number; name?: string }[];
  onClose: () => void;
  onImported: () => void;
}

const ReverseImportDrawer = ({ open, directories, onClose, onImported }: ReverseImportDrawerProps) => {
  const [step, setStep] = useState(0);
  const steps = [
    { title: '选源表', description: '数据源与表' },
    { title: '确认配置', description: '分层与目录' },
    { title: '确认字段', description: '预览与导入' },
  ];

  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [datasourceId, setDatasourceId] = useState<number | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [tables, setTables] = useState<ModelingImportTableView[]>([]);
  const [selected, setSelected] = useState<Record<string, boolean>>({});
  const [tablesLoading, setTablesLoading] = useState(false);

  const [layers, setLayers] = useState<{ code: string; name: string }[]>([]);
  const [layerCode, setLayerCode] = useState<string>('ODS');
  const [dialect, setDialect] = useState<string | undefined>(undefined);
  const [directoryId, setDirectoryId] = useState<number | undefined>(undefined);

  const [preview, setPreview] = useState<ModelingImportColumnView[] | null>(null);
  const [previewTable, setPreviewTable] = useState<string | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<ModelingImportResult | null>(null);
  const [eventTimeByTable, setEventTimeByTable] = useState<Record<string, string>>({});
  const [expandedTable, setExpandedTable] = useState<string | null>(null);

  const [captureColumn, setCaptureColumn] = useState<ModelingImportColumnView | null>(null);
  const [captureForm] = Form.useForm<{ code: string; name: string; stdType?: string }>();
  const [capturing, setCapturing] = useState(false);

  const failedReasons = useMemo(
    () => new Map((result?.failed ?? []).map((item) => [item.table, item.reason])),
    [result],
  );

  const selectedTables = useMemo(() => tables.filter((table) => selected[table.name]), [tables, selected]);
  const selectedDatasource = useMemo(() => datasources.find((d) => d.id === datasourceId), [datasources, datasourceId]);

  useEffect(() => {
    if (!open) return;
    setStep(0);
    setDatasourceId(undefined);
    setDialect(undefined);
    setDirectoryId(undefined);
    setKeyword('');
    setTables([]);
    setSelected({});
    setPreview(null);
    setPreviewTable(null);
    setResult(null);
    setExpandedTable(null);
    setEventTimeByTable({});
    setCaptureColumn(null);

    listDataSources({ pageNo: 1, pageSize: 200 })
      .then((r) => setDatasources(r.bizData ?? []))
      .catch(() => setDatasources([]));
    listSemanticLayers()
      .then((list) => setLayers((list ?? []).map((item) => ({ code: item.code, name: item.name }))))
      .catch(() => setLayers([]));
  }, [open]);

  useEffect(() => {
    if (datasourceId && selectedDatasource?.dbType) {
      const inferred = dialectOfDbType(selectedDatasource.dbType);
      if (inferred) setDialect(inferred);
    }
  }, [datasourceId, selectedDatasource]);

  const loadTables = useCallback(async (targetDatasourceId?: number, targetKeyword?: string) => {
    if (!targetDatasourceId) {
      setTables([]);
      return;
    }
    setTablesLoading(true);
    setPreview(null);
    setPreviewTable(null);
    try {
      const list = await listModelingImportTables(targetDatasourceId, targetKeyword);
      setTables(list ?? []);
      setSelected({});
    } catch {
      message.error('加载表清单失败，请确认数据源可用');
      setTables([]);
    } finally {
      setTablesLoading(false);
    }
  }, []);

  const previewTableColumns = async (table: ModelingImportTableView) => {
    if (!datasourceId || !table.database) return;
    setPreviewTable(table.name);
    setPreviewLoading(true);
    try {
      const columns = await previewModelingImportColumns(datasourceId, table.database, table.name);
      setPreview(columns ?? []);
    } catch {
      message.error('预览失败，请稍后重试');
      setPreview(null);
    } finally {
      setPreviewLoading(false);
    }
  };

  const _openCapture = (column: ModelingImportColumnView) => {
    setCaptureColumn(column);
    captureForm.setFieldsValue({
      code: column.name.toLowerCase().replace(/[^a-z0-9_]/g, '_'),
      name: column.remarks || column.name,
      stdType: column.typeName,
    });
  };

  const submitCapture = async () => {
    const values = await captureForm.validateFields();
    setCapturing(true);
    try {
      const result = await captureModelingStandard(datasourceId ?? 0, {
        kind: 'TYPE',
        typeCode: (values.stdType || 'CUSTOM').toUpperCase().replace(/[^A-Z0-9_]/g, '_'),
        stdType: values.stdType,
        ...values,
      });
      message.success(result.created ? `已沉淀为标准「${result.name}」` : result.message || '同名标准已存在');
      setCaptureColumn(null);
    } catch {
      message.error('沉淀失败，请检查后重试');
    } finally {
      setCapturing(false);
    }
  };

  const doImport = async () => {
    if (!datasourceId || !dialect || selectedTables.length === 0) return;
    setImporting(true);
    try {
      const report = await importModelingTables({
        dialect,
        directoryId,
        layerCode: layerCode || undefined,
        tables: selectedTables.map((table) => ({
          datasourceId: datasourceId,
          database: table.database ?? '',
          table: table.name,
          name: table.remarks || undefined,
          remarks: table.remarks || undefined,
          eventTimeField: eventTimeByTable[table.name] || undefined,
        })),
      });
      setResult(report);
      onImported();
    } catch {
      message.error('导入失败，请稍后重试');
    } finally {
      setImporting(false);
    }
  };

  const goNext = () => setStep((s) => Math.min(s + 1, 2));
  const goPrev = () => {
    setStep((s) => Math.max(s - 1, 0));
    setPreview(null);
    setPreviewTable(null);
  };

  const canNext = step === 0 ? datasourceId && selectedTables.length > 0 : Boolean(layerCode && dialect);

  const stepIndicator = (
    <div className="mb-6 flex items-center gap-3">
      {steps.map((s, index) => (
        <div key={index} className="flex items-center gap-2">
          <div
            className={[
              'flex h-6 w-6 items-center justify-center rounded-full text-[12px] font-medium',
              index <= step ? 'bg-[#1677ff] text-white' : 'bg-[#f2f4f7] text-[#667085]',
            ].join(' ')}
          >
            {index + 1}
          </div>
          <div>
            <div className={index <= step ? 'text-[13px] font-medium text-[#101828]' : 'text-[13px] text-[#667085]'}>
              {s.title}
            </div>
            <div className="text-[11px] text-[#98a2b3]">{s.description}</div>
          </div>
          {index < steps.length - 1 ? <div className="mx-2 h-px w-8 bg-[#eaecf0]" /> : null}
        </div>
      ))}
    </div>
  );

  const step0 = (
    <div className="flex flex-col gap-4">
      <Select
        showSearch
        optionFilterProp="label"
        className="!w-full"
        placeholder="选择数据源"
        value={datasourceId}
        onChange={(value) => {
          setDatasourceId(value);
          setDialect(undefined);
          void loadTables(value, keyword);
        }}
        options={datasources.map((item) => ({
          label: item.name ?? `数据源 #${item.id}`,
          value: item.id as number,
        }))}
      />
      <Input.Search
        allowClear
        size="small"
        className="!w-[240px]"
        placeholder="按表名搜索"
        onSearch={(value) => {
          setKeyword(value.trim());
          void loadTables(datasourceId, value.trim());
        }}
      />
      {!datasourceId ? (
        <Typography.Text type="secondary">先选择数据源</Typography.Text>
      ) : tablesLoading ? (
        <Spin />
      ) : tables.length === 0 ? (
        <Typography.Text type="secondary">没有匹配的表</Typography.Text>
      ) : (
        <div className="max-h-[360px] overflow-auto rounded border border-[#f0f0f0] p-2">
          {tables.map((table) => (
            <div key={`${table.database}/${table.name}`} className="flex items-center gap-2 py-1">
              <Checkbox
                checked={Boolean(selected[table.name])}
                onChange={(event) =>
                  setSelected((previous) => ({
                    ...previous,
                    [table.name]: event.target.checked,
                  }))
                }
              />
              <Typography.Text className="!text-[13px]">{table.name}</Typography.Text>
              {table.remarks ? (
                <Typography.Text type="secondary" className="!text-[12px]">
                  {table.remarks}
                </Typography.Text>
              ) : null}
            </div>
          ))}
        </div>
      )}
      <div className="text-[12px] text-[#667085]">已选择 {selectedTables.length} 张表</div>
    </div>
  );

  const step1 = (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-3 gap-4">
        <div>
          <Typography.Text className="!mb-1 block !text-[12px]">目标分层 *</Typography.Text>
          <Select
            showSearch
            optionFilterProp="label"
            className="!w-full"
            placeholder="默认 ODS"
            value={layerCode}
            onChange={(value) => setLayerCode(value)}
            options={layers.map((item) => ({ label: item.name, value: item.code }))}
          />
        </div>
        <div>
          <Typography.Text className="!mb-1 block !text-[12px]">目标方言 *</Typography.Text>
          <Select
            className="!w-full"
            placeholder={
              dialectOfDbType(selectedDatasource?.dbType)
                ? `默认 ${dialectOfDbType(selectedDatasource?.dbType)}`
                : '选择方言'
            }
            value={dialect}
            onChange={(value) => setDialect(value)}
            options={MODELING_DIALECT_OPTIONS}
          />
        </div>
        <div>
          <Typography.Text className="!mb-1 block !text-[12px]">导入到目录</Typography.Text>
          <Select
            allowClear
            className="!w-full"
            placeholder="可空 = 未分类"
            value={directoryId}
            onChange={(value) => setDirectoryId(value)}
            options={directories.map((item) => ({ label: item.name, value: item.id }))}
          />
        </div>
      </div>
      <Typography.Text strong className="!text-[13px]">
        已选表清单（{selectedTables.length}）
      </Typography.Text>
      <div className="max-h-[300px] overflow-auto rounded border border-[#f0f0f0] p-2">
        {selectedTables.map((table) => (
          <div key={table.name} className="flex items-center gap-2 py-1">
            <Typography.Text className="!text-[13px]">{table.name}</Typography.Text>
            {table.remarks ? (
              <Typography.Text type="secondary" className="!text-[12px]">
                {table.remarks}
              </Typography.Text>
            ) : null}
            <Typography.Text type="secondary" className="!ml-auto !text-[12px]">
              {table.database}
            </Typography.Text>
          </div>
        ))}
      </div>
    </div>
  );

  const step2 = (
    <div className="flex flex-col gap-4">
      <div className="rounded bg-[#f9fafb] p-3 text-[12px] text-[#475467]">
        配置：分层 {layerCode} · 方言 {dialect}
        {directoryId ? ` · 目录 ${directories.find((d) => d.id === directoryId)?.name || ''}` : ''}
      </div>

      <div className="flex flex-col gap-3">
        {selectedTables.map((table) => (
          <div key={table.name} className="rounded border border-[#f0f0f0] p-3">
            <div className="flex items-center gap-2">
              <Typography.Text strong className="!text-[13px]">
                {table.name}
              </Typography.Text>
              {table.remarks ? (
                <Typography.Text type="secondary" className="!text-[12px]">
                  {table.remarks}
                </Typography.Text>
              ) : null}
              <Button
                type="link"
                size="small"
                className="!ml-auto"
                onClick={() => void previewTableColumns(table)}
              >
                {previewTable === table.name ? '重新预览' : '预览列'}
              </Button>
            </div>
            {previewTable === table.name && previewLoading ? <Spin size="small" className="mt-2" /> : null}
            {previewTable === table.name && preview ? (
              <div className="mt-2">
                <div className="flex max-h-[140px] flex-wrap gap-2 overflow-auto rounded border border-[#f0f0f0] p-2">
                  {preview.map((column) => (
                    <Tag.CheckableTag key={column.name} checked={false} onChange={() => _openCapture(column)}>
                      {column.name}:{column.typeName}
                      {column.primaryKey ? ' 🔑' : ''}
                    </Tag.CheckableTag>
                  ))}
                </div>
                <div className="mt-2 flex items-center gap-2">
                  <Typography.Text className="!text-[12px]">event_time 来源字段</Typography.Text>
                  <Select
                    size="small"
                    className="!w-[240px]"
                    placeholder="自动识别（含 time/date 的列）"
                    value={eventTimeByTable[table.name] ?? detectEventTimeField(preview)}
                    onChange={(value) =>
                      setEventTimeByTable((previous) => ({ ...previous, [table.name]: value ?? '' }))
                    }
                    options={preview.map((column) => ({
                      label: `${column.name}:${column.typeName}`,
                      value: column.name,
                    }))}
                  />
                </div>
              </div>
            ) : null}
          </div>
        ))}
      </div>

      {result ? (
        <div className="rounded border border-[#f0f0f0] p-3">
          <Typography.Text strong className="!block !text-[13px]">
            导入结果
          </Typography.Text>
          <Typography.Text type="secondary" className="!mb-2 !block !text-[12px]">
            成功 {result.created.length} · 补全字段 {result.filled?.length ?? 0} · 跳过 {result.skipped.length} · 失败{' '}
            {result.failed.length}
          </Typography.Text>
          <div className="flex flex-col gap-1">
            {(result.models ?? []).map((item) => {
              const fields = item.fields ?? [];
              const matched = fields.filter((field) => Boolean(field.stdFieldId)).length;
              const unmatched = fields.filter((field) => !field.stdFieldId && !field.technical).length;
              return (
                <div key={`${item.table}-${item.action}`} className="flex flex-col">
                  <div className="flex items-center gap-2 text-[12px]">
                    <Typography.Text className="!text-[12px]">{item.table}</Typography.Text>
                    <Tag
                      className="!mr-0"
                      color={
                        item.action === 'CREATED'
                          ? 'green'
                          : item.action === 'FILLED'
                            ? 'blue'
                            : item.action === 'SKIPPED'
                              ? 'default'
                              : 'red'
                      }
                    >
                      {IMPORT_ACTION_LABELS[item.action]}
                    </Tag>
                    {item.action === 'SKIPPED' ? (
                      <Typography.Text type="secondary" className="!text-[12px]">
                        模型已有字段，未改动
                      </Typography.Text>
                    ) : null}
                    {item.action === 'FILLED' ? (
                      <Typography.Text type="secondary" className="!text-[12px]">
                        模型已存在但无字段，已按源表补全
                      </Typography.Text>
                    ) : null}
                    {item.action === 'FAILED' ? (
                      <Typography.Text type="danger" className="!text-[12px]">
                        {failedReasons.get(item.table)}
                      </Typography.Text>
                    ) : null}
                    {fields.length ? (
                      <Button
                        type="link"
                        size="small"
                        className="!ml-auto !h-auto !p-0 !text-[12px]"
                        onClick={() => setExpandedTable((previous) => (previous === item.table ? null : item.table))}
                      >
                        字段治理：命中 {matched} · 未命中 {unmatched}
                      </Button>
                    ) : null}
                    {item.modelId ? (
                      <Button
                        type="link"
                        size="small"
                        className="!h-auto !p-0 !text-[12px]"
                        onClick={() => history.push(`/modeling/models/${item.modelId}`)}
                      >
                        打开模型
                      </Button>
                    ) : null}
                  </div>
                  {expandedTable === item.table && fields.length ? (
                    <div className="mb-1 ml-3 rounded border border-[#f0f0f0] p-2">
                      {fields.map((field) => (
                        <div key={field.columnName} className="flex items-center gap-2 text-[12px]">
                          <Typography.Text className="!text-[12px] !text-[#475467]">
                            {field.columnName}
                          </Typography.Text>
                          <span className="text-[#98a2b3]">{field.dataType}</span>
                          {field.technical ? (
                            <Tag className="!mr-0">技术列</Tag>
                          ) : field.stdFieldId ? (
                            <Tag className="!mr-0" color="green">
                              ✅ {field.stdFieldName}（{field.stdFieldCode}）
                              {field.matchedBy ? ` · ${MATCH_LABELS[field.matchedBy] ?? field.matchedBy}` : ''}
                            </Tag>
                          ) : (
                            <Tag className="!mr-0" color="orange">
                              ⚠️ 未治理
                            </Tag>
                          )}
                          {field.stdTypeName ? (
                            <span className="text-[#98a2b3]">类型标准：{field.stdTypeName}</span>
                          ) : null}
                          {!field.stdFieldId && field.suggestedStdFieldName ? (
                            <span className="text-[#98a2b3]">建议：{field.suggestedStdFieldName}（名称相似）</span>
                          ) : null}
                          {field.degradedReason ? (
                            <span className="text-[#98a2b3]">{field.degradedReason}</span>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
          {result.standardApply ? (
            <Typography.Paragraph className="!mb-0 !mt-2 !text-[12px]" type="secondary">
              标准套用：类型 {result.standardApply.typeApplied} · 安全 {result.standardApply.securityApplied} · 命名{' '}
              {result.standardApply.namingApplied} · 降级 {result.standardApply.degraded}
            </Typography.Paragraph>
          ) : null}
        </div>
      ) : null}
    </div>
  );

  const footer = (
    <Space className="w-full justify-end">
      {step > 0 && (
        <Button onClick={goPrev} disabled={importing}>
          上一步
        </Button>
      )}
      {step < 2 ? (
        <Button type="primary" disabled={!canNext || importing} onClick={goNext}>
          下一步
        </Button>
      ) : (
        <Button
          type="primary"
          disabled={!datasourceId || !dialect || selectedTables.length === 0 || importing}
          loading={importing}
          onClick={() => void doImport()}
        >
          导入 {selectedTables.length || ''} 张表
        </Button>
      )}
      <Button onClick={onClose}>关闭</Button>
    </Space>
  );

  return (
    <Drawer
      open={open}
      width={760}
      title="逆向导入：数据源表 → 模型"
      destroyOnClose
      onClose={onClose}
      footer={footer}
    >
      {stepIndicator}
      {step === 0 && step0}
      {step === 1 && step1}
      {step === 2 && step2}

      <Modal
        open={Boolean(captureColumn)}
        title="沉淀为标准（类型）"
        destroyOnClose
        onCancel={() => setCaptureColumn(null)}
        onOk={() => void submitCapture()}
        confirmLoading={capturing}
        okText="沉淀"
        cancelText="取消"
      >
        <Typography.Paragraph type="secondary" className="!text-[12px]">
          把选中的列沉淀为类型标准；重名时不会重复创建。
        </Typography.Paragraph>
        <Form form={captureForm} layout="vertical">
          <Form.Item
            name="code"
            label="编码"
            rules={[
              { required: true, message: '请输入编码' },
              { pattern: /^[A-Za-z0-9_]{1,64}$/, message: '仅允许字母、数字和下划线' },
            ]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="stdType" label="标准类型">
            <Input maxLength={64} />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  );
};

export default ReverseImportDrawer;
