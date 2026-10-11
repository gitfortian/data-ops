import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useLatestOperation, useResourceScope } from '@/hooks/useLatestOperation';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import ModelMappingPanel from '@/components/ai/ModelMappingPanel';
import { YakButton, YakEmpty } from '@/components/ui';
import { listDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { getModelingStructure } from '@/services/modeling/api';
import { listModelingImportTables, previewModelingImportColumns } from '@/services/modeling/import';
import {
  clearAllModelingMappings,
  clearModelingMapping,
  listModelingMappings,
  getModelingMappingContext,
  previewOdsMappingReconcile,
  confirmOdsMappingReconcile,
  type OdsMappingReconcilePreview,
  setModelingMapping,
  validateModelingExpression,
} from '@/services/modeling/mapping';
import type { ModelingImportColumnView, ModelingMappingView } from '@/services/modeling/types';

interface MappingFormValues {
  sourceDatasourceId: number;
  sourceDatabase: string;
  sourceTable: string;
  sourceColumn: string;
  transformExpr?: string;
  businessDescription?: string;
}

import { history, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Form, Input, Modal, message, Select, Space, Table, Tag, Typography } from 'antd';
import { useCallback, useEffect, useState } from 'react';

/** 常用转换函数提示(19)。 */
const COMMON_FUNCTIONS = [
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
  const [views, setViews] = useState<ModelingMappingView[]>([]);
  const [loading, setLoading] = useState(false);
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [sourceDatasourceId, setSourceDatasourceId] = useState<number | undefined>();
  const [sourceTables, setSourceTables] = useState<{ database?: string; name: string }[]>([]);
  const [sourceDatabase, setSourceDatabase] = useState<string | undefined>();
  const [sourceTable, setSourceTable] = useState<string | undefined>();
  const [sourceColumns, setSourceColumns] = useState<ModelingImportColumnView[]>([]);
  const [editTarget, setEditTarget] = useState<ModelingMappingView | null>(null);
  const [form] = Form.useForm<MappingFormValues>();
  const [saving, setSaving] = useState(false);
  const [odsReview, setOdsReview] = useState<OdsMappingReconcilePreview>();
  const [odsReviewLoading, setOdsReviewLoading] = useState(false);
  const [odsSaving, setOdsSaving] = useState(false);
  const [odsChoices, setOdsChoices] = useState<Record<string, string>>({});
  const draft = Form.useWatch<MappingFormValues>([], form);
  const maySave = canAll(['modeling:read', 'modeling:update', 'resource:data-source:read']);
  const maySuggest = maySave && canAll(['agent:chat:run', 'agent:session:read']);

  const loadAll = useCallback(async () => {
    if (!modelId) {
      return;
    }
    const current = resource();
    setLoading(true);
    try {
      const [structureData, mappingViews] = await Promise.all([
        getModelingStructure(modelId),
        listModelingMappings(modelId),
      ]);
      if (!current()) return;
      setStructure(structureData as StructureRecordLite);
      setViews(mappingViews ?? []);
    } catch {
      if (current()) message.error('加载来源映射失败，请稍后重试');
    } finally {
      if (current()) setLoading(false);
    }
  }, [modelId, resource]);

  useEffect(() => {
    const current = resource();
    setEditTarget(null); setDefinition(''); setSaving(false); setEditorLoading(false);
    setViews([]); setStructure(undefined); setDatasources([]); setSourceTables([]); setSourceColumns([]);
    setOdsReview(undefined); setOdsChoices({}); setOdsSaving(false); setOdsReviewLoading(false);
    setSourceDatasourceId(undefined); setSourceDatabase(undefined); setSourceTable(undefined);
    form.resetFields();
    void loadAll();
    listDataSources({ pageNo: 1, pageSize: 200 })
      .then(result => { if (current()) setDatasources(result.bizData ?? []); })
      .catch(() => { if (current()) setDatasources([]); });
  }, [loadAll, resource, form]);

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
      const columns = await previewModelingImportColumns(targetDatasourceId, database, table);
      if (current()) setSourceColumns(columns ?? []);
    } catch {
      if (current()) setSourceColumns([]);
    }
  }, [columnsOperation]);

  const openEdit = async (view: ModelingMappingView) => {
    if (!modelId || !maySave) return;
    const current = openOperation();
    setEditTarget(view); setEditorLoading(true); setDefinition(''); form.resetFields();
    try {
      const edit = await getModelingMappingContext(modelId, view.targetColumn);
      if (!current()) return;
      const saved = edit.mapping;
      setEditTarget(saved); setDefinition(edit.definition);
      const ds = saved.sourceDatasourceId ?? sourceDatasourceId;
      const db = saved.sourceDatabase ?? sourceDatabase;
      const table = saved.sourceTable ?? sourceTable;
      form.setFieldsValue({ sourceDatasourceId: ds, sourceDatabase: db, sourceTable: table,
        sourceColumn: saved.sourceColumn, transformExpr: saved.transformExpr, businessDescription: '' });
      setSourceDatasourceId(ds); setSourceDatabase(db); setSourceTable(table);
      void loadSourceTables(ds);
      if (ds && db && table) void loadSourceColumns(ds, db, table);
    } catch (error) {
      if (current()) message.error(error instanceof Error ? error.message : '无法读取当前映射，请重试');
    } finally { if (current()) setEditorLoading(false); }
  };

  const submitMapping = async () => {
    if (!modelId || !editTarget || !definition || editorLoading || saving || !maySave) {
      return;
    }
    const current = resource();
    const { businessDescription: _description, ...values } = await form.validateFields();
    if (!current()) return;
    setSaving(true);
    try {
      await setModelingMapping(modelId, editTarget.targetColumn, values, definition);
      if (!current()) return;
      message.success('映射已保存');
      setEditTarget(null);
      await loadAll();
    } catch {
      if (current()) message.error('保存失败：目标字段、映射或源字段可能已变化，请重新打开核对；也请检查表达式。');
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

  /** Existing source binding is always resolved by the server, not selected in the browser. */
  const loadOdsReview = async () => {
    if (!modelId || !maySave) return;
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
    if (!modelId || !odsReview || !maySave || odsSaving) return;
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
    if (!modelId) {
      return;
    }
    const expression = form.getFieldValue('transformExpr');
    const check = await validateModelingExpression(modelId, expression);
    if (check.valid) {
      message.success('表达式语法通过');
    } else {
      message.error(check.message || '表达式不合法');
    }
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
      title: '源字段',
      key: 'source',
      render: (_value, record) =>
        record.mapped ? (
          <Typography.Text className="!text-[13px]">
            {record.sourceDatabase}.{record.sourceTable}.{record.sourceColumn}
          </Typography.Text>
        ) : (
          <Tag color="warning">未映射</Tag>
        ),
    },
    {
      title: '转换表达式',
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
            disabled={!maySave}
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
          <Button disabled={!maySave} loading={odsReviewLoading}
            onClick={() => void loadOdsReview()}>ODS 来源映射批量核验/修复</Button>
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
        open={Boolean(odsReview)}
        title="ODS 来源证据核验与显式批量修复"
        width={960}
        footer={<Space>
          <Button onClick={() => { setOdsReview(undefined); setOdsChoices({}); }} disabled={odsSaving}>关闭</Button>
          <Button disabled={odsSaving} onClick={selectMatchingOdsColumns}>选择全部安全同名候选（仅选择，不保存）</Button>
          <Button type="primary" loading={odsSaving}
            disabled={!maySave || Object.keys(odsChoices).length === 0
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
        title={`映射目标字段：${editTarget?.targetColumn || ''}`}
        width={620}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        okButtonProps={{ disabled: !maySave || !definition || editorLoading }}
        destroyOnClose
        onCancel={() => { openOperation(); setEditTarget(null); }}
        onOk={() => {
          void submitMapping();
        }}
      >
        <Form form={form} layout="vertical" className="pt-2" disabled={editorLoading || saving}>
          <div className="grid grid-cols-2 gap-x-4 max-sm:grid-cols-1">
            <Form.Item name="sourceDatasourceId" label="源数据源" rules={[{ required: true, message: '请选择数据源' }]}>
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
                placeholder="选择源表"
                onChange={(value) => {
                  form.setFieldsValue({ sourceColumn: undefined, transformExpr: undefined });
                  setSourceTable(value);
                  if (sourceDatasourceId && sourceDatabase) {
                    void loadSourceColumns(sourceDatasourceId, sourceDatabase, value);
                  }
                }}
                options={sourceTables
                  .filter((table) => !sourceDatabase || table.database === sourceDatabase)
                  .map((table) => ({ label: table.name, value: table.name }))}
              />
            </Form.Item>
            <Form.Item name="sourceColumn" label="源字段" rules={[{ required: true, message: '请选择源字段' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder={sourceColumns.length ? '选择源字段' : '先选择源表加载字段'}
                options={sourceColumns.map((column) => ({
                  label: `${column.name}（${column.typeName}）`,
                  value: column.name,
                }))}
              />
            </Form.Item>
          </div>
          <Form.Item name="businessDescription" label="业务说明（供 AI 匹配，可选）">
            <Input.TextArea maxLength={512} rows={2} placeholder="如：业务订单的付款用户编号，不是操作员编号" />
          </Form.Item>
          {editTarget && draft?.sourceDatasourceId && draft.sourceDatabase && draft.sourceTable && (
            <ModelMappingPanel key={JSON.stringify([scope, editTarget.targetColumn, definition, draft])}
              target={{ modelId: Number(modelId), columnName: editTarget.targetColumn,
                datasourceId: draft.sourceDatasourceId, database: draft.sourceDatabase, table: draft.sourceTable,
                businessDescription: draft.businessDescription ?? '', keyword: '' }}
              definition={definition} disabled={!maySuggest || editorLoading || saving}
              onApply={sourceColumn => form.setFieldsValue({ sourceColumn, transformExpr: undefined })} />
          )}
          <Form.Item label="转换表达式（可空 = 直通）">
            <Space.Compact className="w-full">
              <Form.Item name="transformExpr" noStyle>
                <Input maxLength={1024} placeholder="如 CAST(amount AS DECIMAL(18,2))" />
              </Form.Item>
              <Button
                onClick={() => {
                  void checkExpression();
                }}
              >
                校验
              </Button>
            </Space.Compact>
          </Form.Item>
          <Form.Item label="常用函数（点击插入）">
            <Space wrap>
              {COMMON_FUNCTIONS.map((func) => (
                <Tag
                  key={func}
                  className="!cursor-pointer"
                  onClick={() => {
                    const current = form.getFieldValue('transformExpr') || '';
                    form.setFieldsValue({ transformExpr: `${current}${func}` });
                  }}
                >
                  {func.split('(')[0]}
                </Tag>
              ))}
            </Space>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default MappingPanel;
