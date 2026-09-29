import { YakButton, YakEmpty } from '@/components/ui';
import { listDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { getModelingStructure } from '@/services/modeling/api';
import { listModelingImportTables, previewModelingImportColumns } from '@/services/modeling/import';
import {
  clearAllModelingMappings,
  clearModelingMapping,
  listModelingMappings,
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

  const loadAll = useCallback(async () => {
    if (!modelId) {
      return;
    }
    setLoading(true);
    try {
      const [structureData, mappingViews] = await Promise.all([
        getModelingStructure(modelId),
        listModelingMappings(modelId),
      ]);
      setStructure(structureData as StructureRecordLite);
      setViews(mappingViews ?? []);
    } catch {
      message.error('加载来源映射失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, [modelId]);

  useEffect(() => {
    void loadAll();
    listDataSources({ pageNo: 1, pageSize: 200 })
      .then((result) => setDatasources(result.bizData ?? []))
      .catch(() => setDatasources([]));
  }, [loadAll]);

  const loadSourceTables = useCallback(async (targetDatasourceId?: number) => {
    if (!targetDatasourceId) {
      setSourceTables([]);
      return;
    }
    try {
      const list = await listModelingImportTables(targetDatasourceId, undefined);
      setSourceTables(list ?? []);
    } catch {
      setSourceTables([]);
    }
  }, []);

  const loadSourceColumns = useCallback(async (targetDatasourceId: number, database: string, table: string) => {
    try {
      const columns = await previewModelingImportColumns(targetDatasourceId, database, table);
      setSourceColumns(columns ?? []);
    } catch {
      setSourceColumns([]);
    }
  }, []);

  const openEdit = (view: ModelingMappingView) => {
    setEditTarget(view);
    form.setFieldsValue({
      sourceDatasourceId: view.sourceDatasourceId ?? sourceDatasourceId,
      sourceDatabase: view.sourceDatabase ?? sourceDatabase,
      sourceTable: view.sourceTable ?? sourceTable,
      sourceColumn: view.sourceColumn,
      transformExpr: view.transformExpr,
    });
  };

  const submitMapping = async () => {
    if (!modelId || !editTarget) {
      return;
    }
    const values = await form.validateFields();
    setSaving(true);
    try {
      await setModelingMapping(modelId, editTarget.targetColumn, values);
      message.success('映射已保存');
      setEditTarget(null);
      await loadAll();
    } catch {
      message.error('保存失败（源字段可能不存在或表达式不合法）');
    } finally {
      setSaving(false);
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
            onClick={() => {
              openEdit(record);
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
        destroyOnClose
        onCancel={() => setEditTarget(null)}
        onOk={() => {
          void submitMapping();
        }}
      >
        <Form form={form} layout="vertical" className="pt-2">
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item name="sourceDatasourceId" label="源数据源" rules={[{ required: true, message: '请选择数据源' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择数据源"
                onChange={(value) => {
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
