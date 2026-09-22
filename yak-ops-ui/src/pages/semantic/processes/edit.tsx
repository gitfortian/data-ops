import { history, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import {
  Button,
  Checkbox,
  Drawer,
  Form,
  Input,
  Modal,
  message,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { listDataSources } from '@/services/data-source/api';
import type { DataSourceCatalogColumn, DataSourceCatalogTable } from '@/services/data-source/catalog';
import { listDataSourceColumns, searchDataSourceTables } from '@/services/data-source/catalog';
import type { DataSourceRecord } from '@/services/data-source/types';
import type { ModelingMainlineCoverage } from '@/services/modeling/types';
import { getModelingMainlineCoverage } from '@/services/modeling/view';
import {
  bindSemanticProcessField,
  bindSemanticProcessSource,
  getSemanticDomainTree,
  getSemanticProcess,
  listSemanticProcessFields,
  listSemanticProcessSources,
  pageSemanticFields,
  unbindSemanticProcessField,
  unbindSemanticProcessSource,
  updateSemanticProcess,
} from '@/services/semantic/api';
import type {
  SemanticDomainNode,
  SemanticFieldRecord,
  SemanticProcessRecord,
  SemanticProcessSourceRecord,
} from '@/services/semantic/types';

const ROLE_LABELS: Record<string, string> = {
  FACT: '事实',
  DIMENSION: '维度',
  PROCESS: '过程',
  METRIC: '度量',
  MAIN: '主表',
  DETAIL: '明细表',
  DIM: '维表',
};

interface BasicFormValues {
  name: string;
  domainId: number;
  grain?: string;
  bizType: 'FACT' | 'DIMENSION';
  owner?: string;
  sortOrder?: number;
  description?: string;
}

interface SourceBindFormValues {
  datasourceId: number;
  sourceTable: string;
  tableRole: 'MAIN' | 'DETAIL' | 'DIM';
  relatedTable?: string;
  relatedColumn?: string;
  thisColumn?: string;
}

interface FieldBindFormValues {
  fieldId: number;
  isRequired: boolean;
}

/** 业务过程编辑工作台(ticket 34 交互重构):多 Tab 整页 + 子操作抽屉。 */
const ProcessEditPage: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const processId = params.id ? Number(params.id) : undefined;
  const [process, setProcess] = useState<SemanticProcessRecord | null>(null);
  const [loadError, setLoadError] = useState(false);

  // Tab1 基本信息
  const [basicForm] = Form.useForm<BasicFormValues>();
  const [savingBasic, setSavingBasic] = useState(false);

  // Tab2 关联源表
  const [sources, setSources] = useState<SemanticProcessSourceRecord[]>([]);
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [sourceBindOpen, setSourceBindOpen] = useState(false);
  const [sourceBindForm] = Form.useForm<SourceBindFormValues>();
  const [bindingSource, setBindingSource] = useState(false);
  const [sourceTables, setSourceTables] = useState<DataSourceCatalogTable[]>([]);
  const [sourceTablesLoading, setSourceTablesLoading] = useState(false);
  const [thisColumns, setThisColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [thisColumnsLoading, setThisColumnsLoading] = useState(false);
  const [relatedColumns, setRelatedColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [relatedColumnsLoading, setRelatedColumnsLoading] = useState(false);
  const [sourceRole, setSourceRole] = useState<'MAIN' | 'DETAIL' | 'DIM'>('MAIN');
  const [bindDatasourceId, setBindDatasourceId] = useState<number | undefined>();

  // Tab3 引用标准字段
  const [boundFields, setBoundFields] = useState<SemanticFieldRecord[]>([]);
  const [fieldLibrary, setFieldLibrary] = useState<SemanticFieldRecord[]>([]);
  const [fieldBindOpen, setFieldBindOpen] = useState(false);
  const [fieldBindForm] = Form.useForm<FieldBindFormValues>();
  const [bindingField, setBindingField] = useState(false);
  const [addFieldRoleFilter, setAddFieldRoleFilter] = useState<string | undefined>();

  // Tab4 关联模型
  const [mainline, setMainline] = useState<ModelingMainlineCoverage | null>(null);

  const loadProcess = useCallback(async () => {
    if (!processId) {
      return;
    }
    try {
      const [detail, sources, fields, mainline] = await Promise.all([
        getSemanticProcess(processId),
        listSemanticProcessSources(processId),
        listSemanticProcessFields(processId),
        getModelingMainlineCoverage(processId).catch(() => null),
      ]);
      setProcess(detail);
      basicForm.setFieldsValue({
        name: detail.name,
        domainId: detail.domainId,
        grain: detail.grain,
        bizType: detail.bizType,
        owner: detail.owner,
        sortOrder: detail.sortOrder,
        description: detail.description,
      });
      setSources(sources ?? []);
      setBoundFields(fields ?? []);
      setMainline(mainline);
    } catch {
      setLoadError(true);
      message.error('加载业务过程失败，请稍后重试');
    }
  }, [processId, basicForm]);

  useEffect(() => {
    void loadProcess();
    listDataSources({ pageNo: 1, pageSize: 200 })
      .then((result) => setDatasources(result.bizData ?? []))
      .catch(() => setDatasources([]));
  }, [loadProcess]);

  const datasourceNameOf = (id: number) => datasources.find((item) => Number(item.id) === id)?.name ?? `数据源 #${id}`;

  // ============ Tab1: 基本信息 ============
  const saveBasic = async () => {
    if (!processId) {
      return;
    }
    const values = await basicForm.validateFields();
    setSavingBasic(true);
    try {
      await updateSemanticProcess(processId, values);
      message.success('基本信息已保存');
      void loadProcess();
    } catch {
      message.error('保存失败，请稍后重试');
    } finally {
      setSavingBasic(false);
    }
  };

  const basicTab = (
    <Form form={basicForm} layout="vertical" className="max-w-[720px] pt-2">
      <Form.Item label="业务过程编码">
        <Input value={process?.code} disabled />
      </Form.Item>
      <div className="grid grid-cols-2 gap-x-4">
        <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input maxLength={128} />
        </Form.Item>
      </div>
      <ProcessEditDomainsFormItems />
      <div className="grid grid-cols-2 gap-x-4">
        <Form.Item name="grain" label="粒度">
          <Input maxLength={64} placeholder="如 单据/明细/天" />
        </Form.Item>
        <Form.Item name="bizType" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
          <Select
            options={[
              { label: '事实（FACT）', value: 'FACT' },
              { label: '维度（DIMENSION）', value: 'DIMENSION' },
            ]}
          />
        </Form.Item>
        <Form.Item name="owner" label="负责人">
          <Input maxLength={64} placeholder="可选" />
        </Form.Item>
        <Form.Item name="sortOrder" label="排序">
          <Input type="number" placeholder="0" />
        </Form.Item>
      </div>
      <Form.Item name="description" label="描述">
        <Input.TextArea rows={2} maxLength={512} />
      </Form.Item>
      <YakButton
        type="primary"
        className="!h-9 !rounded-lg !px-5 !text-white"
        loading={savingBasic}
        onClick={() => {
          void saveBasic();
        }}
      >
        保存基本信息
      </YakButton>
    </Form>
  );

  // ============ Tab2: 关联源表 ============
  const loadTablesForDatasource = useCallback((datasourceId: number, keyword?: string) => {
    setSourceTablesLoading(true);
    searchDataSourceTables(datasourceId, keyword)
      .then((list) => setSourceTables(list ?? []))
      .catch(() => setSourceTables([]))
      .finally(() => setSourceTablesLoading(false));
  }, []);

  const openSourceBind = () => {
    sourceBindForm.resetFields();
    setSourceRole('MAIN');
    setThisColumns([]);
    setRelatedColumns([]);
    setSourceBindOpen(true);
    if (bindDatasourceId !== undefined) {
      void loadTablesForDatasource(bindDatasourceId);
    }
  };

  const submitSourceBind = async () => {
    if (!processId) {
      return;
    }
    const values = await sourceBindForm.validateFields();
    setBindingSource(true);
    try {
      const joinCondition =
        values.tableRole === 'MAIN'
          ? undefined
          : values.thisColumn && values.relatedTable && values.relatedColumn
            ? `${values.thisColumn} = ${values.relatedTable}.${values.relatedColumn}`
            : undefined;
      await bindSemanticProcessSource(processId, {
        datasourceId: values.datasourceId,
        sourceTable: values.sourceTable,
        tableRole: values.tableRole,
        joinCondition,
      });
      message.success('源表已绑定（连通性校验通过）');
      setSourceBindOpen(false);
      await loadProcess();
    } catch {
      message.error('绑定失败（连通性校验未通过或参数不合法）');
    } finally {
      setBindingSource(false);
    }
  };

  const removeSource = (binding: SemanticProcessSourceRecord) => {
    Modal.confirm({
      title: '解绑源表',
      content: `确定解绑源表「${binding.sourceTable}」？`,
      okText: '解绑',
      cancelText: '取消',
      onOk: async () => {
        await unbindSemanticProcessSource(processId ?? 0, binding.id);
        message.success('已解绑');
        await loadProcess();
      },
    });
  };

  const sourceColumns: TableColumnsType<SemanticProcessSourceRecord> = [
    {
      title: '数据源',
      dataIndex: 'datasourceId',
      width: 160,
      render: (value: number) => datasourceNameOf(value),
    },
    {
      title: '源表名',
      dataIndex: 'sourceTable',
      width: 200,
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: '表角色',
      dataIndex: 'tableRole',
      width: 100,
      render: (value: string) => <Tag>{ROLE_LABELS[value] ?? value}</Tag>,
    },
    {
      title: '关联条件',
      dataIndex: 'joinCondition',
      ellipsis: true,
      render: (value?: string) => value || '—',
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_: unknown, record: SemanticProcessSourceRecord) => (
        <Button type="link" size="small" danger onClick={() => removeSource(record)}>
          解绑
        </Button>
      ),
    },
  ];

  const sourcesTab = (
    <div>
      <div className="mb-3 flex justify-end">
        <YakButton
          type="primary"
          className="!h-9 !rounded-lg !px-4 !text-white"
          onClick={() => {
            openSourceBind();
          }}
        >
          新增绑定
        </YakButton>
      </div>
      {sources.length === 0 ? (
        <YakEmpty compact title="尚未绑定源表" description="点击「新增绑定」关联源表（连通性校验通过后生效）" />
      ) : (
        <Table<SemanticProcessSourceRecord>
          rowKey="id"
          size="small"
          columns={sourceColumns}
          dataSource={sources}
          pagination={false}
        />
      )}
      {sources.length > 1 ? (
        <div className="mt-4 rounded border border-[#f0f0f0] p-3">
          <Typography.Text strong className="!block !mb-2 !text-[13px]">
            关联关系
          </Typography.Text>
          {sources
            .filter((item) => item.joinCondition)
            .map((item) => (
              <Typography.Paragraph key={item.id} className="!mb-1 !text-[12px]">
                <Tag>{item.sourceTable}</Tag>
                <Typography.Text code>{item.joinCondition}</Typography.Text>
              </Typography.Paragraph>
            ))}
        </div>
      ) : null}

      <Modal
        open={sourceBindOpen}
        width={560}
        title="新增源表绑定"
        destroyOnClose
        onCancel={() => setSourceBindOpen(false)}
        onOk={() => {
          void submitSourceBind();
        }}
        confirmLoading={bindingSource}
        okText="绑定"
        cancelText="取消"
      >
        <Form form={sourceBindForm} layout="vertical" className="pt-2">
          <Form.Item name="datasourceId" label="数据源" rules={[{ required: true, message: '请选择数据源' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择数据源"
              onChange={(value) => {
                setBindDatasourceId(value);
                sourceBindForm.setFieldValue('sourceTable', undefined);
                sourceBindForm.setFieldValue('thisColumn', undefined);
                setThisColumns([]);
                void loadTablesForDatasource(value);
              }}
              options={datasources.map((item) => ({
                label: item.name ?? `数据源 #${item.id}`,
                value: Number(item.id),
              }))}
            />
          </Form.Item>
          <Form.Item name="sourceTable" label="源表名" rules={[{ required: true, message: '请选择源表' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              loading={sourceTablesLoading}
              placeholder="从该数据源的表清单中选择"
              onChange={(value) => {
                sourceBindForm.setFieldValue('thisColumn', undefined);
                setThisColumns([]);
                if (bindDatasourceId && value) {
                  const table = sourceTables.find((t) => t.name === value);
                  setThisColumnsLoading(true);
                  listDataSourceColumns(bindDatasourceId, table?.database, table?.schema, value)
                    .then((cols) => setThisColumns(cols ?? []))
                    .catch(() => setThisColumns([]))
                    .finally(() => setThisColumnsLoading(false));
                }
              }}
              options={sourceTables.map((table) => ({
                label: table.remarks ? `${table.name}（${table.remarks}）` : table.name,
                value: table.name,
              }))}
            />
          </Form.Item>
          <Form.Item name="tableRole" label="表角色" initialValue="MAIN">
            <Select
              options={[
                { label: '主表（MAIN）', value: 'MAIN' },
                { label: '明细表（DETAIL）', value: 'DETAIL' },
                { label: '维表（DIM）', value: 'DIM' },
              ]}
              onChange={(value) => setSourceRole(value)}
            />
          </Form.Item>
          {sourceRole === 'MAIN' ? (
            <Form.Item label="关联条件">
              <Input disabled placeholder="主表为基准，无需关联条件" />
            </Form.Item>
          ) : (
            <>
              <Form.Item
                name="relatedTable"
                label="关联哪张表（从已绑源表中选）"
                rules={[{ required: true, message: '请选择关联表' }]}
              >
                <Select
                  placeholder="选择已绑定的源表"
                  options={sources.map((item) => ({
                    label: item.sourceTable,
                    value: item.sourceTable,
                  }))}
                  onChange={(value) => {
                    sourceBindForm.setFieldValue('relatedColumn', undefined);
                    setRelatedColumns([]);
                    const related = sources.find((item) => item.sourceTable === value);
                    if (related) {
                      setRelatedColumnsLoading(true);
                      listDataSourceColumns(related.datasourceId, undefined, undefined, value)
                        .then((cols) => setRelatedColumns(cols ?? []))
                        .catch(() => setRelatedColumns([]))
                        .finally(() => setRelatedColumnsLoading(false));
                    }
                  }}
                />
              </Form.Item>
              <Form.Item
                name="thisColumn"
                label="本表关联字段"
                rules={[{ required: true, message: '请选择本表关联字段' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择字段"
                  loading={thisColumnsLoading}
                  options={thisColumns.map((column) => ({
                    label: column.typeName ? `${column.name}（${column.typeName}）` : column.name,
                    value: column.name,
                  }))}
                />
              </Form.Item>
              <Form.Item
                name="relatedColumn"
                label="目标表关联字段"
                rules={[{ required: true, message: '请选择目标字段' }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  placeholder="选择字段"
                  loading={relatedColumnsLoading}
                  options={relatedColumns.map((column) => ({
                    label: column.typeName ? `${column.name}（${column.typeName}）` : column.name,
                    value: column.name,
                  }))}
                />
              </Form.Item>
            </>
          )}
        </Form>
      </Modal>
    </div>
  );

  // ============ Tab3: 引用标准字段 ============
  const loadBoundFields = useCallback(async () => {
    if (!processId) {
      return;
    }
    try {
      setBoundFields((await listSemanticProcessFields(processId)) ?? []);
    } catch {
      setBoundFields([]);
    }
  }, [processId]);

  useEffect(() => {
    if (processId) {
      void loadBoundFields();
      pageSemanticFields({ pageNo: 1, pageSize: 200 })
        .then((result) => setFieldLibrary(result.bizData ?? []))
        .catch(() => setFieldLibrary([]));
    }
  }, [processId, loadBoundFields]);

  const submitFieldBind = async () => {
    if (!processId) {
      return;
    }
    const values = await fieldBindForm.validateFields();
    setBindingField(true);
    try {
      await bindSemanticProcessField(processId, values.fieldId, values.isRequired);
      message.success('字段已引用');
      setFieldBindOpen(false);
      await loadBoundFields();
    } catch {
      message.error('引用失败（字段可能已停用或已绑定）');
    } finally {
      setBindingField(false);
    }
  };

  const unbindField = (field: SemanticFieldRecord) => {
    if (!processId) {
      return;
    }
    Modal.confirm({
      title: '移除字段引用',
      content: `确定移除对「${field.name}（${field.code}）」的引用？`,
      okText: '移除',
      cancelText: '取消',
      onOk: async () => {
        await unbindSemanticProcessField(processId, field.id);
        message.success('已移除');
        await loadBoundFields();
      },
    });
  };

  const fieldColumns: TableColumnsType<SemanticFieldRecord> = [
    {
      title: '字段名',
      dataIndex: 'name',
      width: 170,
      render: (value: string, record) => (
        <Space size={6}>
          <Typography.Text className="!text-[13px]">{value}</Typography.Text>
          <Typography.Text code className="!text-[12px]">
            {record.code}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: '角色',
      dataIndex: 'role',
      width: 100,
      render: (value: string) => <Tag>{ROLE_LABELS[value] ?? value}</Tag>,
    },
    {
      title: '是否必填',
      key: 'required',
      width: 100,
      render: (_: unknown, record: SemanticFieldRecord) =>
        record.isRequired ? <Tag color="orange">必需</Tag> : <Tag>可选</Tag>,
    },
    { title: '生效类型', dataIndex: 'dataType', width: 140, render: (v?: string) => v || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_: unknown, record: SemanticFieldRecord) => (
        <Button type="link" size="small" danger onClick={() => unbindField(record)}>
          移除
        </Button>
      ),
    },
  ];

  const fieldsTab = (
    <div>
      <div className="mb-3 flex justify-end">
        <YakButton
          type="primary"
          className="!h-9 !rounded-lg !px-4 !text-white"
          onClick={() => {
            fieldBindForm.resetFields();
            setFieldBindOpen(true);
          }}
        >
          添加字段
        </YakButton>
      </div>
      {boundFields.length === 0 ? (
        <YakEmpty compact title="尚未引用标准字段" description="点击「添加字段」从字段库选择" />
      ) : (
        <Table<SemanticFieldRecord>
          rowKey="id"
          size="small"
          columns={fieldColumns}
          dataSource={boundFields}
          pagination={false}
        />
      )}

      <Drawer
        open={fieldBindOpen}
        width={560}
        title="从标准字段库添加字段"
        destroyOnClose
        onClose={() => setFieldBindOpen(false)}
        footer={null}
      >
        <Form form={fieldBindForm} layout="vertical" className="pt-2">
          <Form.Item label="按角色筛选">
            <Select
              allowClear
              placeholder="全部角色"
              value={addFieldRoleFilter}
              onChange={setAddFieldRoleFilter}
              options={[
                { label: '业务过程', value: 'PROCESS' },
                { label: '维度', value: 'DIMENSION' },
                { label: '度量', value: 'METRIC' },
              ]}
            />
          </Form.Item>
          <Form.Item name="fieldId" label="标准字段" rules={[{ required: true, message: '请选择标准字段' }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="从字段库选择（已绑定字段自动排除）"
              options={fieldLibrary
                .filter(
                  (field) =>
                    field.status !== 'DISABLED' &&
                    !boundFields.some((bound) => bound.id === field.id) &&
                    (!addFieldRoleFilter || field.role === addFieldRoleFilter),
                )
                .map((field) => ({
                  label: `${field.name}（${field.code}）`,
                  value: field.id,
                }))}
            />
          </Form.Item>
          <Form.Item name="isRequired" label="是否必需" valuePropName="checked">
            <Checkbox>必需字段（派生建模默认勾选）</Checkbox>
          </Form.Item>
          <YakButton
            type="primary"
            className="!h-9 !rounded-lg !px-5 !text-white"
            loading={bindingField}
            onClick={() => {
              void submitFieldBind();
            }}
          >
            引用字段
          </YakButton>
        </Form>
      </Drawer>
    </div>
  );

  // ============ Tab4: 关联模型 ============
  const modelTab = mainline ? (
    <div>
      {(mainline.layers ?? []).length === 0 ? (
        <YakEmpty
          compact
          title="该业务过程还没有已建模型"
          description="通过「按业务过程派生建模」（ticket 44）生成后自动展示"
        />
      ) : (
        (mainline.layers ?? []).map((layer) => (
          <div key={layer.layerCode} className="mb-4">
            <Typography.Text strong className="!block !mb-2 !text-[13px]">
              {layer.layerCode}（{layer.modelCount}）
            </Typography.Text>
            {layer.models.map((model) => (
              <div
                key={model.modelId}
                className="mb-1 flex items-center justify-between rounded border border-[#f0f0f0] px-3 py-2"
              >
                <Space>
                  <Typography.Text className="!text-[13px]">{model.name}</Typography.Text>
                  <Typography.Text code className="!text-[12px]">
                    {model.code}
                  </Typography.Text>
                </Space>
                <Space>
                  <Tag color={model.status === 'PUBLISHED' ? 'green' : 'blue'}>{model.status}</Tag>
                  <Button type="link" size="small" onClick={() => history.push(`/modeling/models/${model.modelId}`)}>
                    打开
                  </Button>
                </Space>
              </div>
            ))}
          </div>
        ))
      )}
    </div>
  ) : (
    <YakEmpty compact title="加载中…" />
  );

  if (loadError) {
    return (
      <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pt-5 text-[#242731]">
        <YakEmpty compact title="业务过程不存在或加载失败" />
        <Space className="mt-4">
          <YakButton className="!h-9 !rounded-lg !px-4" onClick={() => history.push('/semantic/processes')}>
            返回列表
          </YakButton>
        </Space>
      </div>
    );
  }

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">
            {process?.name ?? '业务过程编辑'}
            {process?.code ? (
              <Typography.Text code className="!ml-2 !text-[13px]">
                {process.code}
              </Typography.Text>
            ) : null}
          </div>
          <div className="mt-1 text-[13px] text-[#667085]">
            业务过程编辑工作台：基本信息、源表关联、标准字段引用与各层模型覆盖
          </div>
        </div>
        <YakButton className="!h-9 !rounded-lg !px-4" onClick={() => history.push('/semantic/processes')}>
          返回列表
        </YakButton>
      </div>

      <Tabs
        className="mt-3"
        defaultActiveKey="basic"
        items={[
          { key: 'basic', label: '基本信息', children: basicTab },
          { key: 'sources', label: '关联源表', children: sourcesTab },
          { key: 'fields', label: '引用标准字段', children: fieldsTab },
          { key: 'models', label: '关联模型', children: modelTab },
        ]}
      />
    </div>
  );
};

/** 业务域下拉(编辑页内嵌;经 Form 上下文绑定到外层 Form)。 */
function ProcessEditDomainsFormItems() {
  const [domains, setDomains] = useState<SemanticDomainNode[]>([]);
  useEffect(() => {
    getSemanticDomainTree()
      .then((data) => setDomains(data ?? []))
      .catch(() => setDomains([]));
  }, []);
  const flatten = (nodes: SemanticDomainNode[]): { id: number; path: string }[] => {
    const out: { id: number; path: string }[] = [];
    const walk = (list: SemanticDomainNode[], prefix: string) => {
      for (const node of list) {
        const path = prefix ? `${prefix} / ${node.name}` : node.name;
        out.push({ id: node.id, path });
        walk(node.children, path);
      }
    };
    walk(nodes, '');
    return out;
  };
  return (
    <Form.Item name="domainId" label="所属业务域" rules={[{ required: true, message: '请选择业务域' }]}>
      <Select
        showSearch
        optionFilterProp="label"
        placeholder="选择业务域"
        options={flatten(domains).map((item) => ({ label: item.path, value: item.id }))}
      />
    </Form.Item>
  );
}

export default ProcessEditPage;
