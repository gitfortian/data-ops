import { history, useParams } from '@umijs/max';
import type { TableColumnsType } from 'antd';
import { Button, Form, Input, Modal, message, Select, Space, Table } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import {
  deleteModelingLayerFieldMapping,
  listModelingLayerFieldMappings,
  upsertModelingLayerFieldMapping,
} from '@/services/modeling/layerMapping';
import type { ModelingLayerFieldMappingView } from '@/services/modeling/types';
import { listSemanticLayers, pageSemanticFields } from '@/services/semantic/api';
import type { SemanticFieldRecord, SemanticLayerRecord } from '@/services/semantic/types';

const LayerFieldMappingPage: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const modelId = params.id;
  const [views, setViews] = useState<ModelingLayerFieldMappingView[]>([]);
  const [fields, setFields] = useState<SemanticFieldRecord[]>([]);
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  const loadAll = useCallback(async () => {
    if (!modelId) {
      return;
    }
    setLoading(true);
    try {
      setViews((await listModelingLayerFieldMappings(modelId)) ?? []);
    } catch {
      message.error('加载分层映射失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, [modelId]);

  useEffect(() => {
    void loadAll();
    pageSemanticFields({ pageNo: 1, pageSize: 200 })
      .then((result: { bizData: SemanticFieldRecord[] }) => setFields(result.bizData ?? []))
      .catch(() => setFields([]));
    listSemanticLayers()
      .then((list) => setLayers(list ?? []))
      .catch(() => setLayers([]));
  }, [loadAll]);

  const _fieldNameOf = (id: number) => {
    const hit = fields.find((item) => item.id === id);
    return hit ? `${hit.name}（${hit.code}）` : `ID:${id}`;
  };

  const _layerNameOf = (id: number) => {
    const hit = layers.find((item) => item.id === id);
    return hit ? `${hit.name}（${hit.code}）` : `ID:${id}`;
  };

  const submitEditor = async () => {
    if (!modelId) {
      return;
    }
    const values = await form.validateFields();
    setSaving(true);
    try {
      await upsertModelingLayerFieldMapping(modelId, values);
      message.success('分层映射已保存');
      setEditorOpen(false);
      await loadAll();
    } catch {
      message.error('保存失败（标准字段/分层可能不存在或表达式不合法）');
    } finally {
      setSaving(false);
    }
  };

  const removeRow = (row: ModelingLayerFieldMappingView) => {
    Modal.confirm({
      title: '删除分层映射',
      content: `确定删除「${row.processFieldName ?? row.processFieldId} @ ${row.layerName ?? row.layerId}」？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        await deleteModelingLayerFieldMapping(row.id);
        message.success('已删除');
        await loadAll();
      },
    });
  };

  const columns: TableColumnsType<ModelingLayerFieldMappingView> = [
    {
      title: '标准字段',
      key: 'processField',
      width: 220,
      render: (_value, record) =>
        record.processFieldName
          ? `${record.processFieldName}（${record.processFieldCode}）`
          : record.processFieldId
            ? `ID:${record.processFieldId}`
            : '未治理（未关联标准字段）',
    },
    {
      title: '分层',
      key: 'layer',
      width: 160,
      render: (_value, record) =>
        record.layerName ? `${record.layerName}（${record.layerCode}）` : `ID:${record.layerId}`,
    },
    { title: '落地字段', dataIndex: 'layerFieldName', width: 160 },
    { title: '落地类型', dataIndex: 'layerDataType', width: 130, render: (v?: string) => v || '-' },
    { title: '来源字段', dataIndex: 'sourceField', width: 150, render: (v?: string) => v || '-' },
    { title: '转换表达式', dataIndex: 'transformExpr', ellipsis: true, render: (v?: string) => v || '-' },
    {
      title: '操作',
      key: 'actions',
      width: 80,
      render: (_value, record) => (
        <Button type="link" size="small" danger onClick={() => removeRow(record)}>
          删除
        </Button>
      ),
    },
  ];

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-4 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">字段分层映射</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            标准字段在各层的落地记录：派生建模自动写入（ticket 44），此处为补录与修正
          </div>
        </div>
        <Space>
          <Button onClick={() => history.push(`/modeling/models/${modelId}`)}>返回模型</Button>
          <YakButton
            className="!h-9 !rounded-lg !px-4"
            onClick={() => {
              form.resetFields();
              setEditorOpen(true);
            }}
          >
            补录映射
          </YakButton>
        </Space>
      </div>

      {views.length === 0 && !loading ? (
        <YakEmpty compact title="还没有分层映射" description="通过「按业务过程派生建模」自动生成，或在此补录" />
      ) : (
        <Table<ModelingLayerFieldMappingView>
          className="mt-4"
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={views}
          pagination={false}
        />
      )}

      <Modal
        open={editorOpen}
        title="补录分层映射"
        width={620}
        okText="保存"
        cancelText="取消"
        confirmLoading={saving}
        destroyOnClose
        onCancel={() => setEditorOpen(false)}
        onOk={() => {
          void submitEditor();
        }}
      >
        <Form form={form} layout="vertical" className="pt-2">
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item name="processFieldId" label="标准字段" rules={[{ required: true, message: '请选择标准字段' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择标准字段"
                options={fields.map((item) => ({
                  label: `${item.name}（${item.code}）`,
                  value: item.id,
                }))}
              />
            </Form.Item>
            <Form.Item name="layerId" label="分层" rules={[{ required: true, message: '请选择分层' }]}>
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="选择分层"
                options={layers.map((item) => ({
                  label: `${item.name}（${item.code}）`,
                  value: item.id,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="layerFieldName"
              label="落地字段名"
              rules={[{ required: true, message: '请输入落地字段名' }]}
            >
              <Input maxLength={128} placeholder="该层物理字段名" />
            </Form.Item>
            <Form.Item name="layerDataType" label="落地类型">
              <Input maxLength={64} placeholder="如 DECIMAL(18,2)" />
            </Form.Item>
            <Form.Item name="sourceField" label="来源字段" className="col-span-2">
              <Input maxLength={256} placeholder="模型内字段名或上游字段（可选）" />
            </Form.Item>
            <Form.Item name="transformExpr" label="转换表达式" className="col-span-2">
              <Input maxLength={1024} placeholder="可空 = 直通" />
            </Form.Item>
          </div>
        </Form>
      </Modal>
    </div>
  );
};

export default LayerFieldMappingPage;
