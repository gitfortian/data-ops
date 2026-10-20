import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useLatestOperation, useResourceScope } from '@/hooks/useLatestOperation';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import ModelMappingPanel from '@/components/ai/ModelMappingPanel';
import { YakButton, YakEmpty } from '@/components/ui';
import { listDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { getModelingModel, getModelingStructure } from '@/services/modeling/api';
import { listModelingImportTables, previewModelingImportColumns } from '@/services/modeling/import';
import {
  clearAllModelingMappings,
  clearModelingMapping,
  listModelingMappings,
  getModelingMappingContext,
  previewOdsMappingReconcile,
  confirmOdsMappingReconcile,
  inspectOdsSourceEvidence,
  type OdsSourceEvidenceInspection,
  type OdsMappingReconcilePreview,
  setModelingMapping,
  validateModelingExpression,
} from '@/services/modeling/mapping';
import type { ModelingImportColumnView, ModelingMappingSavePayload, ModelingMappingView, ModelingModelRecord } from '@/services/modeling/types';

interface MappingFormValues {
  sourceDatasourceId?: number;
  sourceDatabase?: string;
  sourceTable?: string;
  sourceColumn?: string;
  transformExpr?: string;
  businessDescription?: string;
}

import { history, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Form, Input, Modal, message, Segmented, Select, Space, Table, Tag, Typography } from 'antd';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { useCallback, useEffect, useRef, useState } from 'react';
import './index.less';

type MappingMode = 'SOURCE' | 'EXPRESSION';

/** 常用转换函数提示(19)。 */
const COMMON_FUNCTIONS = [
  'CURRENT_TIMESTAMP',
  'CURRENT_DATE',
  'NOW()',
  'UUID()',
  'CAST(x AS DECIMAL(18,2))',
  'CONCAT(a, b)',
  'UPPER(x)',
  'LOWER(x)',
  'TRIM(x)',
  'COALESCE(x, 0)',
  'SUBSTR(x, 1, 10)',
  "DATE_FORMAT(d, '%Y%m%d')",
  'ROUND(x, 2)',
  'IFNULL(x, 0)',
];

interface StructureRecordLite {
  modelCode?: string;
  modelName?: string;
}

const isExpressionMapping = (mapping: ModelingMappingView) => mapping.mapped
  && mapping.sourceDatasourceId == null && !mapping.sourceDatabase && !mapping.sourceSchema
  && !mapping.sourceTable && !mapping.sourceColumn && Boolean(mapping.transformExpr?.trim());

const MappingPanel: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const modelId = params.id;
  const { currentProject } = useSecurityProject();
  const { canAll } = usePermissionAccess();
  const scope = JSON.stringify([currentProject?.id, modelId]);
  const resource = useResourceScope(scope);
  const openOperation = useLatestOperation(scope);
  const tablesOperation = useLatestOperation(scope);
  const columnsOperation = useLatestOperation(scope);
  const [definition, setDefinition] = useState('');
  const [editorLoading, setEditorLoading] = useState(false);
  const [structure, setStructure] = useState<StructureRecordLite>();
  const [modelRecord, setModelRecord] = useState<ModelingModelRecord>();
  const [views, setViews] = useState<ModelingMappingView[]>([]);
  const [loading, setLoading] = useState(false);
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [sourceDatasourceId, setSourceDatasourceId] = useState<number | undefined>();
  const [sourceTables, setSourceTables] = useState<{ database?: string; name: string }[]>([]);
  const [sourceDatabase, setSourceDatabase] = useState<string | undefined>();
  const [sourceTable, setSourceTable] = useState<string | undefined>();
  const [sourceColumns, setSourceColumns] = useState<ModelingImportColumnView[]>([]);
  const [editTarget, setEditTarget] = useState<ModelingMappingView | null>(null);
  const [mappingMode, setMappingMode] = useState<MappingMode>('SOURCE');
  const [aiExpanded, setAiExpanded] = useState(false);
  const [form] = Form.useForm<MappingFormValues>();
  const expressionRef = useRef<TextAreaRef>(null);
  const [saving, setSaving] = useState(false);
  const [odsReview, setOdsReview] = useState<OdsMappingReconcilePreview>();
  const [odsInspection, setOdsInspection] = useState<OdsSourceEvidenceInspection>();
  const [odsInspectionLoading, setOdsInspectionLoading] = useState(false);
  const [odsReviewLoading, setOdsReviewLoading] = useState(false);
  const [odsSaving, setOdsSaving] = useState(false);
  const [odsChoices, setOdsChoices] = useState<Record<string, string>>({});
  const draft = Form.useWatch<MappingFormValues>([], form);
  const mayInspectOds = canAll(['modeling:read', 'resource:data-source:read']);
  const mayEdit = canAll(['modeling:read', 'modeling:update']);
  const mayReadSource = canAll(['resource:data-source:read']);
  const maySaveSource = mayEdit && mayReadSource;
  const maySave = mayEdit && (mappingMode === 'EXPRESSION' || mayReadSource);
  const maySuggest = maySaveSource && canAll(['agent:chat:run', 'agent:session:read']);

  const loadAll = useCallback(async () => {
    if (!modelId) {
      return;
    }
    const current = resource();
    setLoading(true);
    try {
      const [structureData, mappingViews, currentModel] = await Promise.all([
        getModelingStructure(modelId),
        listModelingMappings(modelId),
        getModelingModel(modelId),
      ]);
      if (!current()) return;
      setStructure(structureData as StructureRecordLite);
      setViews(mappingViews ?? []);
      setModelRecord(currentModel);
    } catch {
      if (current()) message.error('加载来源映射失败，请稍后重试');
    } finally {
      if (current()) setLoading(false);
    }
  }, [modelId, resource]);

  useEffect(() => {
    let active = true;
    const current = resource();
    openOperation(); tablesOperation(); columnsOperation();
    setEditTarget(null); setDefinition(''); setSaving(false); setEditorLoading(false);
    setMappingMode(mayReadSource ? 'SOURCE' : 'EXPRESSION'); setAiExpanded(false);
    setViews([]); setStructure(undefined); setModelRecord(undefined); setDatasources([]); setSourceTables([]); setSourceColumns([]);
    setOdsReview(undefined); setOdsChoices({}); setOdsSaving(false); setOdsReviewLoading(false);
    setOdsInspection(undefined); setOdsInspectionLoading(false);
    setSourceDatasourceId(undefined); setSourceDatabase(undefined); setSourceTable(undefined);
    form.resetFields();
    void loadAll();
    if (mayReadSource) {
      listDataSources({ pageNo: 1, pageSize: 200 })
        .then(result => { if (active && current()) setDatasources(result.bizData ?? []); })
        .catch(() => { if (active && current()) setDatasources([]); });
    }
    return () => { active = false; };
  }, [loadAll, resource, form, mayReadSource, openOperation, tablesOperation, columnsOperation]);

  const loadSourceTables = useCallback(async (targetDatasourceId?: number) => {
    const current = tablesOperation();
    if (!targetDatasourceId) {
      setSourceTables([]);
      return;
    }
    try {
      const list = await listModelingImportTables(targetDatasourceId, undefined);
      if (current()) setSourceTables(list ?? []);
    } catch {
      if (current()) setSourceTables([]);
    }
  }, [tablesOperation]);

  const loadSourceColumns = useCallback(async (targetDatasourceId: number, database: string, table: string) => {
    const current = columnsOperation();
    try {
      // A bound ODS must preview the exact persisted source namespace; omitting
      // Schema can silently show same-named columns from a different schema.
      const boundSchema = modelRecord?.layerCode === 'ODS'
        && modelRecord.sourceDatasourceId === targetDatasourceId
        && modelRecord.sourceDatabase === database && modelRecord.sourceTable === table
        ? modelRecord.sourceSchema ?? undefined : undefined;
      const columns = await previewModelingImportColumns(targetDatasourceId, database, table, boundSchema);
      if (current()) setSourceColumns(columns ?? []);
    } catch {
      if (current()) setSourceColumns([]);
    }
  }, [columnsOperation, modelRecord]);

  const openEdit = async (view: ModelingMappingView) => {
    if (!modelId || !mayEdit) return;
    const current = openOperation();
    tablesOperation(); columnsOperation();
    setAiExpanded(false); setSourceTables([]); setSourceColumns([]);
    setEditTarget(view); setEditorLoading(true); setDefinition(''); form.resetFields();
    try {
      const edit = await getModelingMappingContext(modelId, view.targetColumn);
      if (!current()) return;
      const saved = edit.mapping;
      setEditTarget(saved); setDefinition(edit.definition);
      const useExpression = isExpressionMapping(saved) || (!saved.mapped && !mayReadSource);
      setMappingMode(useExpression ? 'EXPRESSION' : 'SOURCE');
      const ds = useExpression ? undefined : saved.sourceDatasourceId ?? sourceDatasourceId;
      const db = useExpression ? undefined : saved.sourceDatabase ?? sourceDatabase;
      const table = useExpression ? undefined : saved.sourceTable ?? sourceTable;
      form.setFieldsValue({ sourceDatasourceId: ds, sourceDatabase: db, sourceTable: table,
        sourceColumn: saved.sourceColumn, transformExpr: saved.transformExpr, businessDescription: '' });
      setSourceDatasourceId(ds); setSourceDatabase(db); setSourceTable(table);
      if (!useExpression && mayReadSource) {
        void loadSourceTables(ds);
        if (ds && db && table) void loadSourceColumns(ds, db, table);
      }
    } catch (error) {
      if (current()) message.error(error instanceof Error ? error.message : '无法读取当前映射，请重试');
    } finally { if (current()) setEditorLoading(false); }
  };

  const submitMapping = async () => {
    if (!modelId || !editTarget || !definition || editorLoading || saving || !maySave) {
      return;
    }
    const current = resource();
    const mode = mappingMode;
    setSaving(true);
    try {
      let values: MappingFormValues;
      try {
        // Source fields retained by the form must not participate in expression-only saves.
        values = await form.validateFields(mode === 'EXPRESSION'
          ? ['transformExpr']
          : ['sourceDatasourceId', 'sourceDatabase', 'sourceTable', 'sourceColumn', 'transformExpr']);
      } catch { return; }
      if (!current()) return;
      const payload: ModelingMappingSavePayload = {
        sourceDatasourceId: mode === 'EXPRESSION' ? null : values.sourceDatasourceId,
        sourceDatabase: mode === 'EXPRESSION' ? null : values.sourceDatabase,
        sourceTable: mode === 'EXPRESSION' ? null : values.sourceTable,
        sourceColumn: mode === 'EXPRESSION' ? null : values.sourceColumn,
        transformExpr: values.transformExpr?.trim(),
      };
      await setModelingMapping(modelId, editTarget.targetColumn, payload, definition);
      if (!current()) return;
      message.success('映射已保存');
      setEditTarget(null);
      await loadAll();
    } catch (error) {
      if (current()) message.error(error instanceof Error ? error.message : '保存失败，请重新打开映射后重试');
    } finally {
      if (current()) setSaving(false);
    }
  };

  const removeMapping = (view: ModelingMappingView) => {
    if (!modelId) {
      return;
    }
    Modal.confirm({
      title: '清空映射',
      content: `确定清空「${view.targetColumn}」的来源映射？`,
      okText: '清空',
      cancelText: '取消',
      onOk: async () => {
        await clearModelingMapping(modelId, view.targetColumn);
        message.success('已清空');
        await loadAll();
      },
    });
  };

  const removeAll = () => {
    if (!modelId) {
      return;
    }
    Modal.confirm({
      title: '批量清空',
      content: '确定清空该模型的全部来源映射？',
      okText: '批量清空',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        const cleared = await clearAllModelingMappings(modelId);
        message.success(`已清空 ${cleared} 条映射`);
        await loadAll();
      },
    });
  };

  const loadOdsInspection = async () => {
    if (!modelId || !mayInspectOds) return;
    const active = resource();
    setOdsInspection(undefined);
    setOdsInspectionLoading(true);
    try {
      const report = await inspectOdsSourceEvidence(modelId);
      if (active()) setOdsInspection(report);
    } catch (error) {
      if (active()) message.error(error instanceof Error ? error.message
        : '真实来源目录不可用或无权限，不能将核验失败当成零个差异');
    } finally {
      if (active()) setOdsInspectionLoading(false);
    }
  };

  /** Existing source binding is always resolved by the server, not selected in the browser. */
  const loadOdsReview = async () => {
    if (!modelId || !maySaveSource) return;
    const active = resource();
    setOdsReviewLoading(true);
    setOdsChoices({});
    setOdsReview(undefined);
    try {
      const evidence = await previewOdsMappingReconcile(modelId);
      if (active()) setOdsReview(evidence);
    } catch (error) {
      if (active()) message.error(error instanceof Error ? error.message
        : 'ODS 来源证据不可核验，请确认模型仍是已绑定真实来源的草稿');
    } finally { if (active()) setOdsReviewLoading(false); }
  };

  const selectMatchingOdsColumns = () => {
    if (!odsReview) return;
    const selected: Record<string, string> = {};
    const used = new Set<string>();
    odsReview.rows.forEach((row) => {
      const name = row.suggestedSourceColumn;
      if (row.status === 'UNMAPPED' && name && !used.has(name.toLowerCase())) {
        selected[row.targetColumn] = name;
        used.add(name.toLowerCase());
      }
    });
    setOdsChoices(selected);
  };

  const confirmOdsRepair = () => {
    if (!modelId || !odsReview || !maySaveSource || odsSaving) return;
    const selections = Object.entries(odsChoices)
      .filter(([, sourceColumn]) => Boolean(sourceColumn))
      .map(([targetColumn, sourceColumn]) => ({ targetColumn, sourceColumn }));
    if (!selections.length) {
      message.warning('请先选择需要修复的未映射真实源字段');
      return;
    }
    const expectedDefinition = odsReview.definition;
    Modal.confirm({
      title: '确认修复 ODS 正式来源映射',
      content: `将按已核验的来源快照写入 ${selections.length} 条真实来源映射。不会更改现有映射、物理结构或技术生成字段。若期间证据发生变化，所有修改都会拒绝并回滚。`,
      okText: '确认保存并回读',
      cancelText: '返回审阅',
      async onOk() {
        const active = resource();
        setOdsSaving(true);
        try {
          const receipt = await confirmOdsMappingReconcile(modelId, expectedDefinition, selections);
          if (!active()) return;
          if (receipt.status !== 'SAVED_AND_VERIFIED' || receipt.applied !== selections.length) {
            throw new Error('服务端回执与确认项不一致，请刷新核验');
          }
          message.success(`已正式保存并回读验证 ${receipt.applied} 条 ODS 源列映射`);
          setOdsReview(undefined); setOdsChoices({});
          await loadAll();
        } catch (error) {
          if (active()) {
            setOdsReview(undefined); setOdsChoices({});
            message.error(error instanceof Error ? error.message : '源列发生变化，修复未完成，请重新预检');
          }
          throw error;
        } finally { if (active()) setOdsSaving(false); }
      },
    });
  };

  const checkExpression = async () => {
    if (!modelId || editorLoading || saving) return;
    try { await form.validateFields(['transformExpr']); }
    catch { return; }
    const expression = form.getFieldValue('transformExpr');
    const current = resource();
    try {
      const check = await validateModelingExpression(modelId, expression);
      if (!current() || form.getFieldValue('transformExpr') !== expression) return;
      if (check.valid) message.success('表达式基础校验通过');
      else message.error(check.message || '表达式不合法');
    } catch {
      if (current()) message.error('暂时无法校验，请稍后重试');
    }
  };

  const insertFunction = (value: string) => {
    const expression = form.getFieldValue('transformExpr') || '';
    const input = expressionRef.current?.resizableTextArea?.textArea;
    const start = input?.selectionStart ?? expression.length;
    const end = input?.selectionEnd ?? start;
    const next = `${expression.slice(0, start)}${value}${expression.slice(end)}`;
    if (next.length > 1000) return;
    form.setFieldsValue({ transformExpr: next });
    requestAnimationFrame(() => {
      if (!input || expressionRef.current?.resizableTextArea?.textArea !== input) return;
      expressionRef.current?.focus();
      input.setSelectionRange(start + value.length, start + value.length);
    });
  };

  const columns: TableColumnsType<ModelingMappingView> = [
    {
      title: '目标字段',
      dataIndex: 'targetColumn',
      width: 180,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    { title: '类型', dataIndex: 'dataType', width: 130 },
    {
      title: '来源',
      key: 'source',
      render: (_value, record) =>
        isExpressionMapping(record) ? (
          <Tag>表达式</Tag>
        ) : record.mapped ? (
          <Typography.Text className="!text-[13px]">
            {[record.sourceDatabase, record.sourceSchema, record.sourceTable, record.sourceColumn].filter(Boolean).join('.')}
          </Typography.Text>
        ) : (
          <Tag color="warning">未映射</Tag>
        ),
    },
    {
      title: '表达式',
      dataIndex: 'transformExpr',
      ellipsis: true,
      render: (value?: string) => value || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      render: (_value, record) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            disabled={!mayEdit}
            onClick={() => {
              void openEdit(record);
            }}
          >
            {record.mapped ? '编辑' : '映射'}
          </Button>
          {record.mapped ? (
            <Button type="link" size="small" danger onClick={() => removeMapping(record)}>
              清空
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">来源映射</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            模型 {structure?.modelName || ''}（{structure?.modelCode || '-'}）字段级来源映射；为加工 SQL 与血缘提供输入
          </div>
        </div>
        <Space>
          <Button onClick={() => history.push(`/modeling/models/${modelId}`)}>返回模型</Button>
          {modelRecord?.layerCode?.toUpperCase() === 'ODS' && (
            <Button disabled={!mayInspectOds} loading={odsInspectionLoading}
              onClick={() => void loadOdsInspection()}>核验当前 ODS 来源证据（只读）</Button>
          )}
          {modelRecord?.layerCode?.toUpperCase() === 'ODS'
            && modelRecord.status === 'DRAFT'
            && modelRecord.sourceDatasourceId != null && (
            <Button disabled={!maySaveSource} loading={odsReviewLoading}
              onClick={() => void loadOdsReview()}>ODS 来源映射批量核验/修复</Button>
          )}
          <YakButton
            className="!h-9 !rounded-lg !px-4"
            danger
            onClick={() => {
              removeAll();
            }}
          >
            批量清空
          </YakButton>
        </Space>
      </div>

      {views.length === 0 && !loading ? (
        <YakEmpty compact title="模型还没有字段" description="先在表结构编辑器中添加字段" />
      ) : (
        <Table<ModelingMappingView>
          className="mt-4"
          rowKey="targetColumn"
          loading={loading}
          columns={columns}
          dataSource={views}
          pagination={false}
        />
      )}

      <Modal
        open={Boolean(odsInspection)}
        title="ODS 当前来源证据核验（只读）"
        width={1020}
        footer={<Button onClick={() => setOdsInspection(undefined)}>关闭报告</Button>}
        onCancel={() => setOdsInspection(undefined)}>
        {odsInspection && <>
          <Typography.Paragraph type="secondary">
            当前模型状态：{odsInspection.modelStatus}；授权来源：
            #{odsInspection.sourceDatasourceId ?? '未绑定'} · {odsInspection.sourceDatabase || '-'}.
            {odsInspection.sourceTable || '-'}
          </Typography.Paragraph>
          <Space wrap className="mb-3">
            <Tag color={odsInspection.status === 'CONSISTENT_NOW' ? 'green' : 'warning'}>
              {odsInspection.status === 'CONSISTENT_NOW' ? '当前来源与设计一致'
                : odsInspection.status === 'NOT_CHECKABLE' ? '缺少正式来源绑定'
                : '存在需要审阅的来源证据'}
            </Tag>
            <Tag>物理列 {odsInspection.targetCount}</Tag>
            <Tag>真实源列 {odsInspection.sourceCount}</Tag>
            <Tag>持久映射 {odsInspection.mappingCount}</Tag>
          </Space>
          {odsInspection.evidenceSha256 && (
            <Typography.Paragraph type="secondary">
              本次核验指纹：
              <Typography.Text copyable={{ text: odsInspection.evidenceSha256 }}>
                {odsInspection.evidenceSha256.slice(0, 20)}…
              </Typography.Text>
            </Typography.Paragraph>
          )}
          <Typography.Paragraph type="secondary">
            本报告仅核验最新授权源表和当前模型设计，不等于某个已发布历史版本、
            物理表部署、数据质量或真实运行已通过。发现差异不会自动修改任何映射。
          </Typography.Paragraph>
          {odsInspection.blockers.map((blocker, index) => (
            <Typography.Paragraph key={index} type="danger" className="!mb-1">{blocker}</Typography.Paragraph>
          ))}
          <Table size="small" rowKey={(_, index) => String(index)}
            pagination={{ pageSize: 20 }} dataSource={odsInspection.fields}
            columns={[
              { title: '物理目标列', dataIndex: 'targetColumn', render: (v?: string) => v || '—' },
              { title: '目标类型', dataIndex: 'targetType', render: (v?: string) => v || '—' },
              { title: '真实源列', dataIndex: 'sourceColumn', render: (v?: string) => v || '—' },
              { title: '源类型', dataIndex: 'sourceType', render: (v?: string) => v || '—' },
              { title: '证据状态', dataIndex: 'status', render: (v: string) => <Tag
                color={v === 'MATCHED' ? 'green' : ['TECHNICAL', 'EXPRESSION'].includes(v) ? 'default' : 'warning'}>
                {v === 'EXPRESSION' ? '表达式' : v}</Tag> },
              { title: '说明 / 下一步', dataIndex: 'reason' },
            ]} />
        </>}
      </Modal>

      <Modal
        open={Boolean(odsReview)}
        title="ODS 来源证据核验与显式批量修复"
        width={960}
        footer={<Space>
          <Button onClick={() => { setOdsReview(undefined); setOdsChoices({}); }} disabled={odsSaving}>关闭</Button>
          <Button disabled={odsSaving} onClick={selectMatchingOdsColumns}>选择全部安全同名候选（仅选择，不保存）</Button>
          <Button type="primary" loading={odsSaving}
            disabled={!maySaveSource || Object.keys(odsChoices).length === 0
              || Boolean(odsReview?.conflictCount || odsReview?.orphanCount)}
            onClick={confirmOdsRepair}>确认选中来源映射</Button>
        </Space>}
        onCancel={() => { if (!odsSaving) { setOdsReview(undefined); setOdsChoices({}); } }}>
        {odsReview && <>
          <Typography.Paragraph type="secondary">
            正式绑定来源：#{odsReview.sourceDatasourceId} · {odsReview.sourceDatabase}.{odsReview.sourceTable}
          </Typography.Paragraph>
          <Typography.Paragraph type="secondary">
            来源与物理结构证据 SHA-256：
            <Typography.Text copyable={{ text: odsReview.definition }}>{odsReview.definition.slice(0, 20)}…</Typography.Text>
          </Typography.Paragraph>
          <Space wrap className="mb-3">
            <Tag color="green">已映射 {odsReview.mappedCount}</Tag>
            <Tag color="warning">待映射 {odsReview.unmappedCount}</Tag>
            <Tag>技术列 {odsReview.technicalCount}</Tag>
            {odsReview.orphanCount > 0 && <Tag color="red">已删除目标的历史孤儿映射 {odsReview.orphanCount}，需单独核查</Tag>}
            {odsReview.conflictCount > 0 && <Tag color="red">其他来源的历史映射冲突 {odsReview.conflictCount}，请先核查</Tag>}
            <Tag color="blue">本次已选择 {Object.keys(odsChoices).length}</Tag>
          </Space>
          {(odsReview.conflictCount > 0 || odsReview.orphanCount > 0) && (
            <Typography.Paragraph type="danger">
              存在跨来源或已删除目标字段的历史映射，服务端将拒绝本批次写入，请先使用现有逐列编辑和清理操作。
            </Typography.Paragraph>
          )}
          <Table size="small" rowKey="targetColumn" pagination={{ pageSize: 20 }}
            dataSource={odsReview.rows} columns={[
              { title: '物理目标字段', dataIndex: 'targetColumn' },
              { title: '类型', dataIndex: 'dataType' },
              { title: '当前来源状态', key: 'sourceStatus',
                render: (_, row) => row.status === 'MAPPED'
                  ? <Tag color="green">已映射：{row.existingSourceColumn}</Tag>
                  : row.status === 'EXPRESSION' ? <Tag>表达式</Tag>
                  : row.status === 'SOURCE_CONFLICT' ? <Tag color="red">来源冲突：{row.existingSourceColumn}</Tag>
                  : row.status === 'TECHNICAL' ? <Tag>平台技术列，无真实来源</Tag>
                  : <Tag color="warning">未映射</Tag> },
              { title: '用户明确选择源字段', key: 'sourceChoice',
                render: (_, row) => row.status !== 'UNMAPPED' ? '—' : (
                  <Select<string> allowClear showSearch optionFilterProp="label"
                    style={{ width: 230 }}
                    placeholder={row.suggestedSourceColumn
                      ? `同名候选：${row.suggestedSourceColumn}` : '选择真实源列'}
                    value={odsChoices[row.targetColumn]}
                    options={odsReview.sourceColumns.map((sourceColumn) => ({
                      value: sourceColumn, label: sourceColumn,
                    }))}
                    onChange={(value) => {
                      setOdsChoices((prev) => {
                        const next = { ...prev };
                        if (value) next[row.targetColumn] = value;
                        else delete next[row.targetColumn];
                        return next;
                      });
                    }} />
                ) },
            ]} />
          <Typography.Paragraph type="secondary">
            同名列仅为候选，不自动保存。未选择的列保持原状；已有人工映射不会被覆盖。
            保存前后端会重新读取真实源表与模型状态，证据过期则整批失败。
          </Typography.Paragraph>
        </>}
      </Modal>
      <Modal
        open={Boolean(editTarget)}
        title="字段映射"
        className="model-mapping-editor"
        width={680}
        centered
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        okButtonProps={{ disabled: !maySave || !definition || editorLoading }}
        cancelButtonProps={{ disabled: saving }}
        closable={!saving}
        maskClosable={!saving}
        keyboard={!saving}
        destroyOnClose
        onCancel={() => { openOperation(); setEditTarget(null); }}
        onOk={() => {
          void submitMapping();
        }}
      >
        <div className="model-mapping-editor__target">
          <span>目标字段</span>
          <code>{editTarget?.targetColumn}</code>
          <Tag>{editTarget?.dataType || '—'}</Tag>
        </div>
        <Segmented<MappingMode>
          block
          className="model-mapping-editor__mode"
          value={mappingMode}
          disabled={editorLoading || saving}
          options={[
            { label: '源字段', value: 'SOURCE', disabled: !mayReadSource },
            { label: '表达式', value: 'EXPRESSION' },
          ]}
          onChange={(value) => { setMappingMode(value); setAiExpanded(false); }}
        />
        <Form form={form} layout="vertical" disabled={editorLoading || saving || !maySave}>
          {mappingMode === 'SOURCE' && (
            <div className="model-mapping-editor__sources">
              <Form.Item name="sourceDatasourceId" label="数据源" rules={[{ required: true, message: '请选择数据源' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择数据源"
                  onChange={(value) => {
                    columnsOperation();
                    form.setFieldsValue({ sourceDatabase: undefined, sourceTable: undefined, sourceColumn: undefined, transformExpr: undefined });
                    setSourceDatasourceId(value);
                    setSourceDatabase(undefined);
                    setSourceTable(undefined);
                    setSourceColumns([]);
                    setSourceTables([]);
                    void loadSourceTables(value);
                  }}
                  options={datasources.map((item) => ({
                    label: item.name ?? `数据源 #${item.id}`,
                    value: item.id as number,
                  }))}
                />
              </Form.Item>
              <Form.Item name="sourceDatabase" label="源库" rules={[{ required: true, message: '请选择源库' }]}>
                <Select
                  showSearch
                  disabled={!sourceDatasourceId || editorLoading || saving || !mayReadSource}
                  placeholder="选择库"
                  onChange={(value) => {
                    columnsOperation();
                    form.setFieldsValue({ sourceTable: undefined, sourceColumn: undefined, transformExpr: undefined });
                    setSourceDatabase(value);
                    setSourceTable(undefined);
                    setSourceColumns([]);
                  }}
                  options={Array.from(new Set(sourceTables.map((table) => table.database ?? '')))
                    .filter(Boolean)
                    .map((database) => ({ label: database, value: database }))}
                />
              </Form.Item>
              <Form.Item name="sourceTable" label="源表" rules={[{ required: true, message: '请选择源表' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  disabled={!sourceDatabase || editorLoading || saving || !mayReadSource}
                  placeholder="选择源表"
                  onChange={(value) => {
                    form.setFieldsValue({ sourceColumn: undefined, transformExpr: undefined });
                    setSourceTable(value);
                    setSourceColumns([]);
                    if (sourceDatasourceId && sourceDatabase) {
                      void loadSourceColumns(sourceDatasourceId, sourceDatabase, value);
                    }
                  }}
                  options={sourceTables
                    .filter((table) => table.database === sourceDatabase)
                    .map((table) => ({ label: table.name, value: table.name }))}
                />
              </Form.Item>
              <Form.Item name="sourceColumn" label="源字段" rules={[{ required: true, message: '请选择源字段' }]}>
                <Select
                  showSearch
                  optionFilterProp="label"
                  disabled={!sourceTable || editorLoading || saving || !mayReadSource}
                  placeholder="选择字段"
                  options={sourceColumns.map((column) => ({
                    label: `${column.name}（${column.typeName}）`,
                    value: column.name,
                  }))}
                />
              </Form.Item>
            </div>
          )}
          <div className="model-mapping-editor__expression-heading">
            <label htmlFor="transformExpr">
              {mappingMode === 'EXPRESSION' ? '表达式' : '转换表达式'}
              {mappingMode === 'SOURCE' && <span>可选</span>}
            </label>
            <Button type="link" size="small" onClick={() => void checkExpression()}>校验</Button>
          </div>
          <Form.Item
            name="transformExpr"
            rules={mappingMode === 'EXPRESSION'
              ? [{ required: true, whitespace: true, message: '请输入表达式' }]
              : []}
          >
            <Input.TextArea
              ref={expressionRef}
              className="model-mapping-editor__expression"
              maxLength={1000}
              autoSize={{ minRows: 3, maxRows: 6 }}
              placeholder={mappingMode === 'EXPRESSION'
                ? "CURRENT_TIMESTAMP、NOW() 或 'fixed_value'"
                : '留空则直接使用源字段'}
            />
          </Form.Item>
          <div className="model-mapping-editor__functions">
            <span>常用函数</span>
            <div>
              {COMMON_FUNCTIONS.map((func) => (
                <button type="button" key={func} disabled={editorLoading || saving || !maySave}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => insertFunction(func)}>
                  {func.split('(')[0]}
                </button>
              ))}
            </div>
          </div>
          {mappingMode === 'SOURCE' && maySuggest && (
            <div className="model-mapping-editor__ai">
              <Button type="text" size="small" aria-expanded={aiExpanded}
                onClick={() => setAiExpanded(!aiExpanded)}>
                {aiExpanded ? '收起 AI 辅助' : 'AI 辅助选字段'}
              </Button>
              {aiExpanded && <div className="model-mapping-editor__ai-content">
                <Form.Item name="businessDescription" label="业务含义">
                  <Input maxLength={512} placeholder="补充业务含义（可选）" />
                </Form.Item>
                {editTarget && draft?.sourceDatasourceId && draft.sourceDatabase && draft.sourceTable ? (
                  <ModelMappingPanel compact key={JSON.stringify([scope, editTarget.targetColumn, definition, draft])}
                    target={{ modelId: Number(modelId), columnName: editTarget.targetColumn,
                      datasourceId: draft.sourceDatasourceId, database: draft.sourceDatabase, table: draft.sourceTable,
                      businessDescription: draft.businessDescription ?? '', keyword: '' }}
                    definition={definition} disabled={!maySuggest || editorLoading || saving}
                    onApply={sourceColumn => form.setFieldsValue({ sourceColumn, transformExpr: undefined })} />
                ) : <Typography.Text type="secondary">请先选择源表</Typography.Text>}
              </div>}
            </div>
          )}
        </Form>
      </Modal>
    </div>
  );
};

export default MappingPanel;
