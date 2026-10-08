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
