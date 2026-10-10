import { history } from '@umijs/max';
import {
  Alert, Button, Card, Empty, Form, Input, Modal, Select, Space, Table, Tag, Typography, message,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { listSemanticProcessFields, pageSemanticProcesses } from '@/services/semantic/api';
import type { SemanticFieldRecord, SemanticProcessRecord } from '@/services/semantic/types';
import {
  addLogicalAttribute, addLogicalEntity, addLogicalRelation, createLogicalDraft,
  freezeLogicalDraft, getLogicalDraft, getLogicalVersionSnapshot, listLogicalDrafts,
  listLogicalVersions,
  type LogicalDraftDetail, type LogicalDraftVersion, type LogicalModelDraft,
  type LogicalRelation,
} from '@/services/modeling/logical';

type CreateValues = { processId: number; code: string; name: string; description?: string };
type EntityValues = { code: string; name: string; businessName?: string; description?: string };
type AttributeValues = { code: string; name: string; stdFieldId?: number; logicalType?: string;
  primaryFlag?: boolean; nullable?: boolean; description?: string };
type RelationValues = { sourceEntityId: number; targetEntityId: number;
  cardinality: LogicalRelation['cardinality']; description?: string };

const errorText = (error: unknown): string =>
  error instanceof Error ? error.message : '操作失败，请检查项目权限及数据状态';

/**
 * Draft-first business modeling. Semantic owns field/process definitions;
 * this page never claims that snapshots are deployed/published physical tables.
 */
export default function LogicalWorkspace() {
  const [models, setModels] = useState<LogicalModelDraft[]>([]);
  const [processes, setProcesses] = useState<SemanticProcessRecord[]>([]);
  const [selected, setSelected] = useState<number>();
  const [detail, setDetail] = useState<LogicalDraftDetail>();
  const [versions, setVersions] = useState<LogicalDraftVersion[]>([]);
  const [fields, setFields] = useState<SemanticFieldRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [entityOpen, setEntityOpen] = useState(false);
  const [attributeEntityId, setAttributeEntityId] = useState<number>();
  const [relationOpen, setRelationOpen] = useState(false);
  const [snapshotOpen, setSnapshotOpen] = useState(false);
  const [snapshotText, setSnapshotText] = useState('');
  const [createForm] = Form.useForm<CreateValues>();
  const [entityForm] = Form.useForm<EntityValues>();
  const [attributeForm] = Form.useForm<AttributeValues>();
  const [relationForm] = Form.useForm<RelationValues>();

  const reload = useCallback(async (id?: number) => {
    setLoading(true);
    try {
      const [next, all] = await Promise.all([
        listLogicalDrafts(),
        pageSemanticProcesses({ pageNo: 1, pageSize: 200 }),
      ]);
      setModels(next);
      setProcesses(all.bizData ?? []);
      const active = id ?? next[0]?.id;
      setSelected(active);
      if (active != null) {
        const [data, historyVersions] = await Promise.all([
          getLogicalDraft(active), listLogicalVersions(active),
        ]);
        setDetail(data);
        setVersions(historyVersions);
      } else {
        setDetail(undefined);
        setVersions([]);
      }
    } catch (err) {
      message.error(errorText(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  useEffect(() => {
    if (!detail?.process?.id) { setFields([]); return; }
    let active = true;
    listSemanticProcessFields(detail.process.id)
      .then((result) => { if (active) setFields((result || []).filter((field) => field.status === 'ENABLED')); })
      .catch(() => { if (active) setFields([]); });
    return () => { active = false; };
  }, [detail?.process?.id]);

  const selectModel = useCallback(async (id: number) => {
    setSelected(id);
    setLoading(true);
    try {
      const [data, previous] = await Promise.all([getLogicalDraft(id), listLogicalVersions(id)]);
      setDetail(data);
      setVersions(previous);
    } catch (err) {
      message.error(errorText(err));
      setDetail(undefined);
    } finally { setLoading(false); }
  }, []);

  const perform = async (action: () => Promise<LogicalDraftDetail>, successText: string, close: () => void) => {
    setSaving(true);
    try {
      const data = await action();
      setDetail(data);
      close();
      message.success(successText);
      const next = await listLogicalDrafts();
      setModels(next);
    } catch (err) {
      message.error(errorText(err));
    } finally { setSaving(false); }
  };

  const entities = useMemo(() => detail?.entities.map((item) => item.entity) ?? [], [detail]);

  const create = async () => {
    const values = await createForm.validateFields();
    await perform(() => createLogicalDraft(values), '已创建逻辑设计草稿，尚未发布或生成物理表', () => {
      setCreateOpen(false);
      createForm.resetFields();
    });
    // A newly created model appears in the list; let the user choose the exact draft.
    await reload().catch(() => undefined);
  };
  const addEntity = async () => {
    if (selected == null) return;
    const values = await entityForm.validateFields();
    await perform(() => addLogicalEntity(selected, values), '已保存逻辑实体', () => {
      setEntityOpen(false); entityForm.resetFields();
    });
  };
  const addAttribute = async () => {
    if (selected == null || attributeEntityId == null) return;
    const values = await attributeForm.validateFields();
    await perform(() => addLogicalAttribute(selected, attributeEntityId, values), '已保存业务属性', () => {
      setAttributeEntityId(undefined); attributeForm.resetFields();
    });
  };
  const addRelation = async () => {
    if (selected == null) return;
    const values = await relationForm.validateFields();
    await perform(() => addLogicalRelation(selected, values), '已保存待确认或已确认的逻辑关系', () => {
      setRelationOpen(false); relationForm.resetFields();
    });
  };
  const freeze = async () => {
    if (selected == null) return;
    setSaving(true);
    try {
      const version = await freezeLogicalDraft(selected);
      setVersions(await listLogicalVersions(selected));
      message.success(`已冻结第 ${version.versionNo} 个逻辑设计草稿快照；未发布、未部署`);
    } catch (err) { message.error(errorText(err)); }
    finally { setSaving(false); }
  };
  const showSnapshot = async (versionNo: number) => {
    if (selected == null) return;
    try {
      const snapshot = await getLogicalVersionSnapshot(selected, versionNo);
      setSnapshotText(snapshot);
      setSnapshotOpen(true);
    } catch (err) { message.error(errorText(err)); }
  };

  return (
    <div className="min-h-screen bg-[#f8fafc] p-6">
      <Space className="mb-4" wrap>
        <Button onClick={() => history.push('/modeling')}>返回模型工作台</Button>
        <Typography.Title level={4} className="!mb-0">业务逻辑模型</Typography.Title>
      </Space>
      <Alert className="mb-4" type="info" showIcon
        message="逻辑设计是业务语义与物理模型之间的桥梁"
        description="这里创建和冻结的都是逻辑设计草稿。业务过程、标准字段由 Semantic 正式管理；保存草稿不代表物理模型已经发布、Doris 已建表或数据已执行。" />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_minmax(0,1fr)]">
        <Card title="项目逻辑模型" extra={<Button type="primary" onClick={() => setCreateOpen(true)}>新建草稿</Button>}>
          {models.length === 0 ? <Empty description="尚无逻辑模型，选择业务过程开始" /> : (
            <div className="flex flex-col gap-2">
              {models.map((model) => (
                <Button key={model.id} block
                  type={model.id === selected ? 'primary' : 'default'}
                  onClick={() => void selectModel(model.id)}>
                  {model.name} · {model.code}
                </Button>
              ))}
            </div>
          )}
        </Card>
        <div className="flex min-w-0 flex-col gap-4">
          {!detail ? <Card loading={loading}><Empty description="选择逻辑草稿查看实体及字段" /></Card> : (
            <>
              <Card title={<Space>{detail.model.name}<Tag>DRAFT</Tag></Space>}
                extra={<Space wrap>
                  <Button onClick={() => setEntityOpen(true)}>添加业务实体</Button>
                  <Button disabled={entities.length < 2} onClick={() => setRelationOpen(true)}>定义实体关系</Button>
                  <Button type="primary" loading={saving} onClick={() => void freeze()}>冻结草稿快照</Button>
                </Space>}>
                <Typography.Text>业务过程：{detail.process.name}（{detail.process.code}）</Typography.Text>
                <Typography.Paragraph className="!mt-2 !mb-0" type="secondary">
                  业务粒度：{detail.process.grain || '尚未由业务负责人确认'}。
                  实体主键、关系基数、物理目标都需要独立审阅，系统不会按同名自动推断。
                </Typography.Paragraph>
              </Card>
              {detail.entities.map(({ entity, attributes }) => (
                <Card key={entity.id} title={entity.businessName || entity.name}
                  extra={<Button onClick={() => { setAttributeEntityId(entity.id); attributeForm.resetFields(); }}>添加属性</Button>}>
                  <Typography.Text type="secondary">{entity.code} · {entity.description || '暂无业务说明'}</Typography.Text>
                  <Table size="small" rowKey="id" pagination={false} className="mt-3"
                    dataSource={attributes} columns={[
                      { title: '属性', dataIndex: 'name', key: 'name' },
                      { title: '编码', dataIndex: 'code', key: 'code' },
                      { title: '逻辑类型', dataIndex: 'logicalType', key: 'logicalType' },
                      { title: '标准字段 ID', dataIndex: 'stdFieldId', key: 'stdFieldId',
                        render: (v?: number) => v == null ? <Tag>待治理</Tag> : <Tag color="blue">{v}</Tag> },
                      { title: '业务主标识', dataIndex: 'primaryFlag', key: 'primaryFlag',
                        render: (v?: boolean) => v ? '用户确认' : '否' },
                    ]} />
                </Card>
              ))}
              <Card title="实体关系（仅用户确认，未知允许保留）">
                <Table rowKey="id" size="small" pagination={false} dataSource={detail.relations}
                  columns={[
                    { title: '来源实体', dataIndex: 'sourceEntityId', key: 'sourceEntityId',
                      render: (id: number) => entities.find((e) => e.id === id)?.businessName || entities.find((e) => e.id === id)?.name || id },
                    { title: '目标实体', dataIndex: 'targetEntityId', key: 'targetEntityId',
                      render: (id: number) => entities.find((e) => e.id === id)?.businessName || entities.find((e) => e.id === id)?.name || id },
                    { title: '业务关系基数', dataIndex: 'cardinality', key: 'cardinality' },
                    { title: '说明', dataIndex: 'description', key: 'description' },
                  ]} />
              </Card>
              <Card title="独立设计快照（不是部署版本）">
                <Table size="small" rowKey="id" pagination={false} dataSource={versions}
                  columns={[
                    { title: '快照版本', dataIndex: 'versionNo', key: 'versionNo' },
                    { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag>{s}</Tag> },
                    { title: '冻结人', dataIndex: 'createdBy', key: 'createdBy' },
                    { title: '查看', key: 'action', render: (_, version: LogicalDraftVersion) =>
                      <Button type="link" onClick={() => void showSnapshot(version.versionNo)}>查看冻结快照</Button> },
                  ]} />
              </Card>
            </>
          )}
        </div>
      </div>

      <Modal open={createOpen} title="基于已确认业务过程创建逻辑草稿"
        okText="创建草稿" okButtonProps={{ loading: saving }}
        onOk={() => void create()} onCancel={() => setCreateOpen(false)} destroyOnHidden>
        <Form layout="vertical" form={createForm}>
          <Form.Item label="正式业务过程" name="processId" rules={[{ required: true }]}>
            <Select showSearch optionFilterProp="label" options={processes.map(p => ({
              label: `${p.name} · ${p.grain || '粒度待确认'}`, value: p.id,
            }))} />
          </Form.Item>
          <Form.Item label="模型编码" name="code" rules={[{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_]{0,127}$/, message: '英文字母开头，最多128字符' }]}>
            <Input placeholder="order_domain" />
          </Form.Item>
          <Form.Item label="模型名称" name="name" rules={[{ required: true }]}><Input placeholder="订单业务逻辑模型" /></Form.Item>
          <Form.Item label="业务说明" name="description"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal open={entityOpen} title="新增业务实体" onOk={() => void addEntity()}
        okText="保存实体" okButtonProps={{ loading: saving }} onCancel={() => setEntityOpen(false)} destroyOnHidden>
        <Form layout="vertical" form={entityForm}>
          <Form.Item name="code" label="实体编码" rules={[{ required: true }]}><Input placeholder="order" /></Form.Item>
          <Form.Item name="name" label="实体名称" rules={[{ required: true }]}><Input placeholder="Order" /></Form.Item>
          <Form.Item name="businessName" label="业务名称"><Input placeholder="订单" /></Form.Item>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal open={attributeEntityId != null} title="新增业务属性" okText="保存属性"
        okButtonProps={{ loading: saving }} onOk={() => void addAttribute()}
        onCancel={() => setAttributeEntityId(undefined)} destroyOnHidden>
        <Form layout="vertical" form={attributeForm} initialValues={{ primaryFlag: false, nullable: true }}>
          <Form.Item label="属性编码" name="code" rules={[{ required: true }]}><Input placeholder="order_id" /></Form.Item>
          <Form.Item label="属性名称" name="name" rules={[{ required: true }]}><Input placeholder="订单标识" /></Form.Item>
          <Form.Item label="标准字段（可先留待确认）" name="stdFieldId">
            <Select allowClear showSearch optionFilterProp="label"
              options={fields.map(f => ({ label: `${f.name} · ${f.code}`, value: f.id }))} />
          </Form.Item>
          <Form.Item label="逻辑类型" name="logicalType"><Input placeholder="STRING / DECIMAL / DATE" /></Form.Item>
          <Form.Item label="是否已确认的业务主标识" name="primaryFlag">
            <Select options={[{ label: '待确认/不是', value: false }, { label: '已确认', value: true }]} />
          </Form.Item>
          <Form.Item label="是否可空" name="nullable">
            <Select options={[{ label: '可空', value: true }, { label: '不可空', value: false }]} />
          </Form.Item>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal open={relationOpen} title="明确业务关系（绝不自动推断）" okText="保存关系"
        okButtonProps={{ loading: saving }} onOk={() => void addRelation()}
        onCancel={() => setRelationOpen(false)} destroyOnHidden>
        <Form form={relationForm} layout="vertical">
          <Form.Item name="sourceEntityId" label="来源实体" rules={[{ required: true }]}>
            <Select options={entities.map(e => ({ label: e.businessName || e.name, value: e.id }))} />
          </Form.Item>
          <Form.Item name="targetEntityId" label="目标实体" rules={[{ required: true }]}>
            <Select options={entities.map(e => ({ label: e.businessName || e.name, value: e.id }))} />
          </Form.Item>
          <Form.Item name="cardinality" label="业务关系基数" rules={[{ required: true }]}>
            <Select options={[
              { label: '尚未确认', value: 'UNKNOWN' },
              { label: '一对一', value: 'ONE_TO_ONE' },
              { label: '一对多', value: 'ONE_TO_MANY' },
              { label: '多对一', value: 'MANY_TO_ONE' },
              { label: '多对多', value: 'MANY_TO_MANY' },
            ]} />
          </Form.Item>
          <Form.Item name="description" label="关系的业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal width={760} title="冻结的逻辑设计内容（只读）" open={snapshotOpen}
        footer={<Button onClick={() => setSnapshotOpen(false)}>关闭</Button>}
        onCancel={() => setSnapshotOpen(false)}>
        <pre style={{ maxHeight: 420, overflow: 'auto', whiteSpace: 'pre-wrap' }}>{snapshotText}</pre>
      </Modal>
    </div>
  );
}
