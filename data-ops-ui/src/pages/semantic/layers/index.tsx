import Table from '@/components/ReadableTable';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { Alert, Button, Form, Input, InputNumber, Modal, message, Select, Space, Switch,  Tag, Typography } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { getDataSource, listDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { useColumnVisibility } from '@/components/table/useColumnVisibility';
import {
  changeSemanticLayerStatus,
  createSemanticLayer,
  deleteSemanticLayer,
  getStandardOptions,
  initializeSemanticLayers,
  listSemanticLayers,
  updateSemanticLayer,
} from '@/services/semantic/api';
import type { SemanticLayerRecord, SemanticStandardOption } from '@/services/semantic/types';

/** 存储格式枚举(2026-09-16):手填易错,固定下拉。 */
const STORAGE_FORMAT_OPTIONS = ['Parquet', 'ORC', 'TextFile', 'CSV', 'JSON'].map((value) => ({
  label: value,
  value,
}));

/** 默认分区表达式:分组列=值,如 dt=yyyyMMdd、hour=HH。 */
const PARTITION_PATTERN = /^[a-z_]+=[A-Za-z-]+$/;

const LayersPage = () => {
  const { can } = usePermissionAccess();
  const [records, setRecords] = useState<SemanticLayerRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<SemanticLayerRecord | null>(null);
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [datasourceLoading, setDatasourceLoading] = useState(false);
  const [pinnedDatasource, setPinnedDatasource] = useState<DataSourceRecord | null>(null);
  const [datasourceLookupState, setDatasourceLookupState] = useState<'idle' | 'loading' | 'failed'>('idle');
  const [namingStandards, setNamingStandards] = useState<SemanticStandardOption[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [optionsError, setOptionsError] = useState(false);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const datasourceSearchRevision = useRef(0);
  const datasourceLookupRevision = useRef(0);
  const selectedDatasourceId = Form.useWatch('datasourceId', form) as number | undefined;

  const searchDatasources = useCallback(async (searchText: string) => {
    const revision = ++datasourceSearchRevision.current;
    setDatasourceLoading(true);
    try {
      const result = await listDataSources({ pageNo: 1, pageSize: 20, keyword: searchText.trim() || undefined });
      if (revision === datasourceSearchRevision.current) {
        setDatasources(result.bizData ?? []);
      }
    } catch {
      if (revision === datasourceSearchRevision.current) message.error('数据源候选加载失败，可修改关键词后重试');
    } finally {
      if (revision === datasourceSearchRevision.current) setDatasourceLoading(false);
    }
  }, []);

  const loadLayers = useCallback(async () => {
    setLoading(true);
    try {
      setRecords((await listSemanticLayers()) ?? []);
    } catch {
      message.error('加载数仓分层失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, []);

  /** 引用选项字典(数据源 + NAMING 标准):列表需展示名称,弹窗打开时刷新一次。 */
  const loadOptions = useCallback(async () => {
    const revision = ++datasourceSearchRevision.current;
    setOptionsLoading(true);
    setOptionsError(false);
    try {
      const [datasourceResult, namingResult] = await Promise.allSettled([
        listDataSources({ pageNo: 1, pageSize: 20 }),
        getStandardOptions(['NAMING']),
      ]);
      let failed = false;
      if (datasourceResult.status === 'fulfilled') {
        if (revision === datasourceSearchRevision.current) setDatasources(datasourceResult.value.bizData ?? []);
      } else failed = true;
      if (namingResult.status === 'fulfilled') setNamingStandards(namingResult.value.NAMING ?? []);
      else failed = true;
      setOptionsError(failed);
      if (failed) message.error('部分引用选项暂不可用，可重试加载');
    } finally {
      setOptionsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadLayers();
    void loadOptions();
  }, [loadLayers, loadOptions]);

  const datasourceNameOf = (id?: number) => (id ? (datasources.find((item) => Number(item.id) === Number(id))?.name ?? `#${id}`) : '-');

  const openCreate = () => {
    datasourceLookupRevision.current += 1;
    setPinnedDatasource(null);
    setDatasourceLookupState('idle');
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ stdMandatory: true });
    setEditorOpen(true);
    void loadOptions();
  };

  const openEdit = (record: SemanticLayerRecord) => {
    const revision = ++datasourceLookupRevision.current;
    setPinnedDatasource(null);
    setDatasourceLookupState(record.datasourceId ? 'loading' : 'idle');
    setEditing(record);
    // 存量行可能不带该字段,回显按缺省=强制,避免开关把 null 改成 false。
    form.setFieldsValue({ ...record, stdMandatory: record.stdMandatory ?? true });
    setEditorOpen(true);
    void loadOptions();
    if (record.datasourceId) {
      // Paged search cannot prove an existing selection is valid. Read the
      // specific identity instead of silently inventing a confirmed option.
      void getDataSource(record.datasourceId)
        .then((source) => {
          if (revision !== datasourceLookupRevision.current) return;
          setPinnedDatasource(source);
          setDatasourceLookupState('idle');
        })
        .catch(() => {
          if (revision === datasourceLookupRevision.current) setDatasourceLookupState('failed');
        });
    }
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        const { code: _ignored, ...rest } = values;
        await updateSemanticLayer(editing.id, rest);
        message.success('分层配置已更新');
      } else {
        await createSemanticLayer(values);
        message.success('分层已创建');
      }
      setEditorOpen(false);
      await loadLayers();
    } catch {
      message.error('保存失败（编码可能已存在或引用不合法），请检查后重试');
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = (record: SemanticLayerRecord) => {
    const next = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    changeSemanticLayerStatus(record.id, next)
      .then(() => {
        message.success(next === 'ENABLED' ? '已启用' : '已停用');
        return loadLayers();
      })
      .catch(() => message.error('操作失败，请稍后重试'));
  };

  const removeLayer = (record: SemanticLayerRecord) => {
    Modal.confirm({
      title: '删除数仓分层',
      content: `确定删除「${record.name}（${record.code}）」？被模型引用时将被阻断。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await deleteSemanticLayer(record.id);
          message.success('已删除');
          await loadLayers();
        } catch {
          message.error('删除失败（分层可能已被模型引用），请稍后重试');
        }
      },
    });
  };

  const handleInitialize = async () => {
    try {
      const created = await initializeSemanticLayers();
      message.success(`已初始化 ${created} 个默认分层`);
      await loadLayers();
    } catch {
      message.error('初始化默认分层失败，请稍后重试');
    }
  };

  /** 状态三分(2026-09-16):停用(灰)/待配置(缺库名或数据源,黄)/启用(绿)。 */
  const statusTagOf = (record: SemanticLayerRecord) => {
    if (record.status === 'DISABLED') {
      return <Tag color="default">停用</Tag>;
    }
    if (!record.databaseName || !record.datasourceId) {
      return <Tag color="gold">待配置</Tag>;
    }
    return <Tag color="green">启用</Tag>;
  };

  const columns = [
    {
      title: '分层',
      dataIndex: 'code',
      width: 160,
      render: (value: string, record: SemanticLayerRecord) => <div>
        <div className="font-medium">{record.name}</div>
        <Typography.Text type="secondary" className="!text-[12px]">{value}</Typography.Text>
      </div>,
    },
    { title: '名称', dataIndex: 'name', width: 120 },
    { title: '库名', dataIndex: 'databaseName', width: 140, render: (v?: string) => v || '-' },
    {
      title: '数据源',
      dataIndex: 'datasourceId',
      width: 140,
      render: (value?: number) => datasourceNameOf(value),
    },
    {
      title: '命名标准',
      dataIndex: 'stdNamingId',
      width: 180,
      ellipsis: true,
      render: (value?: number) => {
        if (!value) {
          return '-';
        }
        const hit = namingStandards.find((item) => item.id === value);
        return hit ? `${hit.name}（${hit.code}）` : `ID:${value}`;
      },
    },
    { title: '默认分区', dataIndex: 'defaultPartition', width: 140, render: (v?: string) => v || '-' },
    { title: '存储格式', dataIndex: 'storageFormat', width: 100, render: (v?: string) => v || '-' },
    {
      title: '生命周期',
      dataIndex: 'lifecycleDays',
      width: 90,
      render: (v?: number) => (v == null ? '永久' : `${v} 天`),
    },
    {
      title: '是否默认',
      dataIndex: 'preset',
      width: 90,
      render: (value: boolean) => (value ? <Tag color="blue">默认</Tag> : <Tag>自定义</Tag>),
    },
    {
      title: '定标强制',
      dataIndex: 'stdMandatory',
      width: 100,
      // M2-5 定标闸门:该层字段是否必须 100% 绑定标准字段;缺省按强制展示。
      render: (value?: boolean) =>
        value === false ? <Tag color="gold">免强制</Tag> : <Tag color="volcano">强制</Tag>,
    },
    {
      title: '被引用模型数',
      dataIndex: 'modelCount',
      width: 110,
      align: 'right' as const,
      render: (value?: number) => value ?? 0,
    },
    {
      title: '落标进度',
      key: 'stdBinding',
      width: 130,
      // M2-5 只读观察期:展示各层字段落标率,不做硬拦截;免强制层不着色。
      render: (_: unknown, record: SemanticLayerRecord) => {
        const total = record.stdColumnTotal ?? 0;
        if (total === 0) {
          return <span className="text-gray-400">—</span>;
        }
        const bound = record.stdBoundColumns ?? 0;
        const pct = Math.round((bound * 100) / total);
        const color =
          bound >= total ? 'green' : record.stdMandatory === false ? undefined : 'orange';
        return <Tag color={color}>{`${bound}/${total} · ${pct}%`}</Tag>;
      },
    },
    {
      title: '状态',
      key: 'status',
      width: 90,
      render: (_: unknown, record: SemanticLayerRecord) => statusTagOf(record),
    },
    {
      title: '操作',
      key: 'actions',
      width: 150,
      // 宽表在 1280px 下会把操作列挤出可视区,钉在右侧保证始终可操作。
      fixed: 'right' as const,
      render: (_: unknown, record: SemanticLayerRecord) => (
        <>
          {can('semantic:update') && <>
            <Button type="link" size="small" onClick={() => openEdit(record)}>编辑</Button>
            <Button type="link" size="small" onClick={() => toggleStatus(record)}>
              {record.status === 'ENABLED' ? '停用' : '启用'}
            </Button>
          </>}
          {/* 默认分层(ODS/DWD/DWS/ADS)不可删除,只可停用 */}
          {record.preset ? null : (
            can('semantic:delete') ? <Button type="link" size="small" danger onClick={() => removeLayer(record)}>
              删除
            </Button> : null
          )}
        </>
      ),
    },
  ];

  const columnView = useColumnVisibility(columns, ['code', 'databaseName', 'datasourceId', 'stdBinding', 'status', 'actions']);

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">数仓分层</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            各层定位库、数据源与命名标准（只存引用）；默认分层不可删，自定义分层被模型引用时阻断删除
          </div>
        </div>
        <Space wrap>
          {columnView.control}
          {can('semantic:create') && <>
          <YakButton
            className="!h-9 !rounded-lg !px-4"
            onClick={() => {
              void handleInitialize();
            }}
          >
            初始化默认分层
          </YakButton>
          <YakButton
            type="primary"
            className="!h-9 !rounded-lg !px-4 !text-white"
            onClick={() => {
          openCreate();
            }}
          >
            新建分层
          </YakButton>
          </>}
        </Space>
      </div>

      <Table<SemanticLayerRecord>
        className="mt-4"
        rowKey="id"
        loading={loading}
        // 让宽表在表格内部横向滚动,而不是溢出后被外层 overflow-hidden 裁掉。
        scroll={{ x: columnView.scrollWidth }}
        columns={columnView.columns}
        dataSource={records}
        locale={{
          emptyText: (
            <YakEmpty
              compact
              title="还没有分层配置"
              description="点击右上角「初始化默认分层」一键创建 ODS/DWD/DWS/ADS（数据源需补全）"
            />
          ),
        }}
        pagination={false}
      />

      <Modal
        open={editorOpen}
        title={editing ? '编辑数仓分层' : '新建数仓分层'}
        width={640}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnClose
        onCancel={() => {
          datasourceLookupRevision.current += 1;
          setEditorOpen(false);
        }}
        onOk={() => {
          void submitEditor();
        }}
      >
        <Form form={form} layout="vertical" className="pt-2">
          {optionsError && <Alert className="mb-3" type="warning" showIcon message="引用选项未能完整加载" action={<Button size="small" onClick={() => void loadOptions()}>重试</Button>} />}
          {editing && selectedDatasourceId === editing.datasourceId && datasourceLookupState !== 'idle' && (
            <Alert
              className="mb-3"
              type={datasourceLookupState === 'failed' ? 'warning' : 'info'}
              showIcon
              message={datasourceLookupState === 'failed'
                ? '当前引用的数据源暂无法核验：可能已删除、属于其他项目或服务不可用，请重新选择'
                : '正在核验存量数据源是否仍属于当前项目'}
            />
          )}
          <div className="grid grid-cols-2 gap-x-4 max-sm:grid-cols-1">
            <Form.Item
              name="code"
              label="编码"
              rules={[
                { required: true, message: '请输入编码' },
                {
                  pattern: /^[A-Za-z0-9_]{1,32}$/,
                  message: '仅允许字母、数字和下划线，1~32 位',
                },
              ]}
            >
              <Input disabled={Boolean(editing)} maxLength={32} placeholder="如 DWD" />
            </Form.Item>
            <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
              <Input maxLength={64} placeholder="分层名称" />
            </Form.Item>
            <Form.Item
              name="databaseName"
              label="库名"
              rules={[{ required: true, message: '请输入库名' }]}
              extra="分层核心配置，派生建模据此定位落库"
            >
              <Input maxLength={128} placeholder="如 dwd_db" />
            </Form.Item>
            <Form.Item name="datasourceId" label="数据源" rules={[{ required: true, message: '请选择数据源' }]}>
              <Select
                allowClear
                showSearch
                loading={optionsLoading || datasourceLoading}
                filterOption={false}
                placeholder="选择数据源"
                onSearch={(value) => void searchDatasources(value)}
                onFocus={() => {
                  if (datasources.length === 0) void searchDatasources('');
                }}
                options={[...datasources,
                  ...(pinnedDatasource && Number(selectedDatasourceId) === Number(pinnedDatasource.id)
                    && !datasources.some((item) => Number(item.id) === Number(pinnedDatasource.id))
                    ? [pinnedDatasource] : []),
                  ...(selectedDatasourceId
                    && !datasources.some((item) => Number(item.id) === Number(selectedDatasourceId))
                    && Number(pinnedDatasource?.id) !== Number(selectedDatasourceId)
                    ? [{ id: selectedDatasourceId, name: `数据源 #${selectedDatasourceId}（待核验）` }]
                    : [])].map((item) => ({
                  label: item.name ?? `数据源 #${item.id}`,
                  value: Number(item.id),
                }))}
              />
            </Form.Item>
            <Form.Item name="stdNamingId" label="命名标准">
              <Select
                allowClear
                showSearch
                loading={optionsLoading}
                optionFilterProp="label"
                placeholder="引用 NAMING 类标准（可选）"
                options={namingStandards.map((item) => ({
                  label: `${item.name}（${item.code}）`,
                  value: item.id,
                }))}
              />
            </Form.Item>
            <Form.Item name="storageFormat" label="存储格式">
              <Select allowClear placeholder="选择存储格式" options={STORAGE_FORMAT_OPTIONS} />
            </Form.Item>
            <Form.Item
              name="defaultPartition"
              label="默认分区表达式"
              rules={[
                {
                  validator: (_, value?: string) => {
                    if (!value) {
                      return Promise.resolve();
                    }
                    return PARTITION_PATTERN.test(value)
                      ? Promise.resolve()
                      : Promise.reject(new Error('须形如 dt=yyyyMMdd、hour=HH'));
                  },
                },
              ]}
            >
              <Input maxLength={256} placeholder="如 dt=yyyyMMdd（可选）" />
            </Form.Item>
            <Form.Item name="lifecycleDays" label="生命周期（天）">
              <InputNumber min={1} className="!w-full" placeholder="留空 = 永久" />
            </Form.Item>
            <Form.Item name="sortOrder" label="排序" extra="留空自动排在最后">
              <InputNumber min={1} className="!w-full" placeholder="自动" />
            </Form.Item>
            <Form.Item
              name="stdMandatory"
              label="定标强制"
              valuePropName="checked"
              extra="强制=该层字段须 100% 绑定标准字段才可定标;贴源镜像层可免强制"
            >
              <Switch checkedChildren="强制" unCheckedChildren="免强制" />
            </Form.Item>
          </div>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} maxLength={512} placeholder="如 明细层，清洗规范化后的明细数据（可选）" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default LayersPage;
