import { history } from '@umijs/max';
import {
  Alert, Button, Card, Empty, Form, Input, Modal, Select, Space, Table, Tag, Typography, message,
} from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { listSemanticProcessFields, pageSemanticProcesses } from '@/services/semantic/api';
import { pageModelingModels } from '@/services/modeling/api';
import type { ModelingModelRecord } from '@/services/modeling/types';
import type { SemanticFieldRecord, SemanticProcessRecord } from '@/services/semantic/types';
import {
  addLogicalAttribute, addLogicalEntity, addLogicalRelation, createLogicalDraft,
  freezeLogicalDraft, getLogicalDraft, getLogicalVersionSnapshot, listLogicalDrafts,
  listLogicalVersions, updateLogicalEntity, updateLogicalAttribute, updateLogicalRelation,
  previewLogicalPhysical, reviewLogicalPhysical,
  type LogicalPhysicalPreview, type LogicalPhysicalReview,
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
  const [editEntityId, setEditEntityId] = useState<number>();
  const [editAttributeId, setEditAttributeId] = useState<number>();
  const [editRelationId, setEditRelationId] = useState<number>();
  const [attributeEntityId, setAttributeEntityId] = useState<number>();
  const [relationOpen, setRelationOpen] = useState(false);
  const [snapshotOpen, setSnapshotOpen] = useState(false);
  const [snapshotText, setSnapshotText] = useState('');
  const [physicalModelId, setPhysicalModelId] = useState<number | null>(null);
  const [physicalSearch, setPhysicalSearch] = useState('');
  const [physicalTargets, setPhysicalTargets] = useState<ModelingModelRecord[]>([]);
  const [physicalTargetsLoading, setPhysicalTargetsLoading] = useState(false);
  const [handoffPreview, setHandoffPreview] = useState<LogicalPhysicalPreview>();
  const [handoffLoading, setHandoffLoading] = useState(false);
  const [mappingSelections, setMappingSelections] = useState<Record<number, number>>({});
  const [reviewResult, setReviewResult] = useState<LogicalPhysicalReview>();
  const [reviewSelectionSnapshot, setReviewSelectionSnapshot] = useState('');
  const [reviewLoading, setReviewLoading] = useState(false);
  const [createForm] = Form.useForm<CreateValues>();
  const [entityForm] = Form.useForm<EntityValues>();
  const [attributeForm] = Form.useForm<AttributeValues>();
  const [relationForm] = Form.useForm<RelationValues>();

  const reload = useCallback(async (id?: number) => {
    setHandoffPreview(undefined);
    setReviewResult(undefined);
    setMappingSelections({});
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

  // Reuse Modeling's project-scoped catalog rather than expecting builders to know
  // physical-model database IDs. Server-side search also covers later pages.
  useEffect(() => {
    if (!detail) {
      setPhysicalTargets([]);
      setPhysicalTargetsLoading(false);
      return;
    }
    let active = true;
    setPhysicalTargetsLoading(true);
    const timeout = setTimeout(() => {
      void pageModelingModels({
        pageNo: 1, pageSize: 50, keyword: physicalSearch.trim() || undefined,
      }).then((page) => {
        if (active) setPhysicalTargets(page.bizData ?? []);
      }).catch(() => {
        if (active) {
          setPhysicalTargets([]);
          message.error('物理模型列表加载失败，请检查当前项目和读取权限');
        }
      }).finally(() => {
        if (active) setPhysicalTargetsLoading(false);
      });
    }, 250);
    return () => { active = false; clearTimeout(timeout); };
  }, [detail?.model.id, physicalSearch]);

  useEffect(() => {
    if (!detail?.process?.id) { setFields([]); return; }
    let active = true;
    listSemanticProcessFields(detail.process.id)
      .then((result) => { if (active) setFields((result || []).filter((field) => field.status === 'ENABLED')); })
      .catch(() => { if (active) setFields([]); });
    return () => { active = false; };
  }, [detail?.process?.id]);

  const selectModel = useCallback(async (id: number) => {
    setHandoffPreview(undefined);
    setReviewResult(undefined);
    setMappingSelections({});
    setPhysicalModelId(null);
    setPhysicalSearch('');
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

  const perform = async (action: () => Promise<LogicalDraftDetail>, successText: string,
    close: () => void): Promise<LogicalDraftDetail | undefined> => {
    setSaving(true);
    try {
      const data = await action();
      setDetail(data);
      setHandoffPreview(undefined);
      setReviewResult(undefined);
      setMappingSelections({});
      close();
      message.success(successText);
      const next = await listLogicalDrafts();
      setModels(next);
      return data;
    } catch (err) {
      message.error(errorText(err));
      return undefined;
    } finally { setSaving(false); }
  };

  const entities = useMemo(() => detail?.entities.map((item) => item.entity) ?? [], [detail]);

  const create = async () => {
    const values = await createForm.validateFields();
    const created = await perform(() => createLogicalDraft(values), '已创建逻辑设计草稿，尚未发布或生成物理表', () => {
      setCreateOpen(false);
      createForm.resetFields();
    });
    if (created) await reload(created.model.id);
  };
  const addEntity = async () => {
    if (selected == null || !detail) return;
    const values = await entityForm.validateFields();
    await perform(() => editEntityId == null
      ? addLogicalEntity(selected, values, detail.model.draftRevision)
      : updateLogicalEntity(selected, editEntityId, values, detail.model.draftRevision),
    '已保存逻辑实体', () => {
      setEntityOpen(false); setEditEntityId(undefined); entityForm.resetFields();
    });
  };
  const addAttribute = async () => {
    if (selected == null || attributeEntityId == null || !detail) return;
    const values = await attributeForm.validateFields();
    await perform(() => editAttributeId == null
      ? addLogicalAttribute(selected, attributeEntityId, values, detail.model.draftRevision)
      : updateLogicalAttribute(selected, attributeEntityId, editAttributeId, values, detail.model.draftRevision),
    '已保存业务属性', () => {
      setAttributeEntityId(undefined); setEditAttributeId(undefined); attributeForm.resetFields();
    });
  };
  const addRelation = async () => {
    if (selected == null || !detail) return;
    const values = await relationForm.validateFields();
    await perform(() => editRelationId == null
      ? addLogicalRelation(selected, values, detail.model.draftRevision)
      : updateLogicalRelation(selected, editRelationId, values, detail.model.draftRevision),
    '已保存待确认或已确认的逻辑关系', () => {
      setRelationOpen(false); setEditRelationId(undefined); relationForm.resetFields();
    });
  };
  const editEntity = (id?: number) => {
    setEditEntityId(id);
    entityForm.resetFields();
    const current = detail?.entities.find(x => x.entity.id === id)?.entity;
    if (current) entityForm.setFieldsValue(current);
    setEntityOpen(true);
  };
  const editAttribute = (entityId: number, id?: number) => {
    setAttributeEntityId(entityId);
    setEditAttributeId(id);
    attributeForm.resetFields();
    const current = detail?.entities.find(x => x.entity.id === entityId)
      ?.attributes.find(x => x.id === id);
    if (current) attributeForm.setFieldsValue(current);
  };
  const editRelation = (id?: number) => {
    setEditRelationId(id);
    relationForm.resetFields();
    const current = detail?.relations.find(x => x.id === id);
    if (current) relationForm.setFieldsValue(current);
    setRelationOpen(true);
  };

  const freeze = async () => {
    if (selected == null) return;
    setSaving(true);
    try {
      const version = await freezeLogicalDraft(selected, detail?.model.draftRevision ?? -1);
      setVersions(await listLogicalVersions(selected));
      message.success(`已冻结第 ${version.versionNo} 个逻辑设计草稿快照；未发布、未部署`);
    } catch (err) { message.error(errorText(err)); }
    finally { setSaving(false); }
  };
  const previewPhysical = async (versionNo: number) => {
    if (selected == null || physicalModelId == null) {
      message.warning('请先从项目模型目录中选择要对照的物理模型');
      return;
    }
    setHandoffPreview(undefined);
    setReviewResult(undefined);
    setMappingSelections({});
    setHandoffLoading(true);
    try {
      setHandoffPreview(await previewLogicalPhysical(selected, versionNo, physicalModelId));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setHandoffLoading(false);
    }
  };

  const logicalReviewOptions = useMemo(() => {
    const values = new Map<number, { id: number; stdFieldId?: number; label: string }>();
    handoffPreview?.columns.forEach((row) => {
      if (row.logicalAttributeId != null) {
        values.set(row.logicalAttributeId, {
          id: row.logicalAttributeId,
          stdFieldId: row.physicalStdFieldId,
          label: `${row.logicalEntity || '未命名实体'} · ${row.logicalAttribute || '未命名属性'} (#${row.logicalAttributeId})`,
        });
      }
    });
    return [...values.values()];
  }, [handoffPreview]);

  const validateExplicitReview = async () => {
    if (selected == null || physicalModelId == null || !handoffPreview) return;
    setReviewResult(undefined);
    setReviewSelectionSnapshot('');
    setReviewLoading(true);
    const selectionSnapshot = JSON.stringify(mappingSelections);
    try {
      const receipt = await reviewLogicalPhysical(selected, handoffPreview.logicalVersionNo, physicalModelId, {
        logicalSnapshotSha256: handoffPreview.logicalSnapshotSha256,
        physicalStructureSha256: handoffPreview.physicalStructureSha256,
        selections: Object.entries(mappingSelections).map(([physicalColumnId, logicalAttributeId]) => ({
          physicalColumnId: Number(physicalColumnId), logicalAttributeId,
        })),
      });
      setReviewSelectionSnapshot(selectionSnapshot);
      setReviewResult(receipt);
      if (!receipt.evidenceUnchanged) {
        setHandoffPreview(undefined);
        setMappingSelections({});
        message.warning('版本证据已变化，请重新预检并选择映射');
      }
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setReviewLoading(false);
    }
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
                  <Button onClick={() => editEntity()}>添加业务实体</Button>
                  <Button disabled={entities.length < 2} onClick={() => editRelation()}>定义实体关系</Button>
                  <Button onClick={() => void selectModel(detail.model.id)}>刷新草稿</Button>
                  <Tag>修订 {detail.model.draftRevision}</Tag>
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
                  extra={<Space>
                    <Button onClick={() => editEntity(entity.id)}>编辑实体</Button>
                    <Button onClick={() => editAttribute(entity.id)}>添加属性</Button>
                  </Space>}>
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
                      { title: '操作', key: 'op', render: (_, row) =>
                        <Button type="link" onClick={() => editAttribute(entity.id, row.id)}>编辑</Button> },
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
                    { title: '操作', key: 'op', render: (_, row) =>
                      <Button type="link" onClick={() => editRelation(row.id)}>编辑</Button> },
                  ]} />
              </Card>
              <Card title="独立设计快照（不是部署版本）"
                extra={<Space wrap>
                  <Typography.Text type="secondary">对照项目内已有物理模型</Typography.Text>
                  <Select<number>
                    className="min-w-[280px]"
                    style={{ width: 320 }}
                    showSearch
                    allowClear
                    filterOption={false}
                    loading={physicalTargetsLoading}
                    placeholder="按物理模型名称或编码搜索"
                    notFoundContent={physicalTargetsLoading ? '正在查询项目物理模型' : '无匹配模型，请输入更多关键词'}
                    value={physicalModelId ?? undefined}
                    onSearch={setPhysicalSearch}
                    onClear={() => {
                      setPhysicalModelId(null); setHandoffPreview(undefined);
                      setReviewResult(undefined); setMappingSelections({});
                    }}
                    onChange={(value) => {
                      setPhysicalModelId(value ?? null); setHandoffPreview(undefined);
                      setReviewResult(undefined); setMappingSelections({});
                    }}
                    options={physicalTargets
                      .filter((target) => target.id != null
                        && Number.isSafeInteger(Number(target.id)) && Number(target.id) > 0)
                      .map((target) => ({
                        value: Number(target.id),
                        label: `${target.name || target.code}（${target.code || target.id}） · ${target.layerCode || '未分层'} · ${target.status || '未知状态'}`,
                      }))}
                  />
                  <Button disabled={physicalModelId == null}
                    onClick={() => history.push(`/modeling/models/${physicalModelId}`)}>
                    打开物理模型
                  </Button>
                </Space>}>
                <Table size="small" rowKey="id" pagination={false} dataSource={versions}
                  columns={[
                    { title: '快照版本', dataIndex: 'versionNo', key: 'versionNo' },
                    { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag>{s}</Tag> },
                    { title: '冻结人', dataIndex: 'createdBy', key: 'createdBy' },
                    { title: '查看', key: 'action', render: (_, version: LogicalDraftVersion) =>
                      <Space>
                        <Button type="link" onClick={() => void showSnapshot(version.versionNo)}>查看冻结快照</Button>
                        <Button loading={handoffLoading} type="link"
                          onClick={() => void previewPhysical(version.versionNo)}>预检物理关联</Button>
                      </Space> },
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
      <Modal open={entityOpen} title={editEntityId == null ? '新增业务实体' : '编辑业务实体'} onOk={() => void addEntity()}
        okText="保存实体" okButtonProps={{ loading: saving }} onCancel={() => { setEntityOpen(false); setEditEntityId(undefined); }} destroyOnHidden>
        <Form layout="vertical" form={entityForm}>
          <Form.Item name="code" label="实体编码" rules={[{ required: true }]}><Input placeholder="order" disabled={editEntityId != null} /></Form.Item>
          <Form.Item name="name" label="实体名称" rules={[{ required: true }]}><Input placeholder="Order" /></Form.Item>
          <Form.Item name="businessName" label="业务名称"><Input placeholder="订单" /></Form.Item>
          <Form.Item name="description" label="业务说明"><Input.TextArea rows={2} /></Form.Item>
        </Form>
      </Modal>
      <Modal open={attributeEntityId != null} title={editAttributeId == null ? '新增业务属性' : '编辑业务属性'} okText="保存属性"
        okButtonProps={{ loading: saving }} onOk={() => void addAttribute()}
        onCancel={() => { setAttributeEntityId(undefined); setEditAttributeId(undefined); }} destroyOnHidden>
        <Form layout="vertical" form={attributeForm} initialValues={{ primaryFlag: false, nullable: true }}>
          <Form.Item label="属性编码" name="code" rules={[{ required: true }]}><Input placeholder="order_id" disabled={editAttributeId != null} /></Form.Item>
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
      <Modal open={relationOpen} title={editRelationId == null ? '明确业务关系（绝不自动推断）' : '编辑业务关系'} okText="保存关系"
        okButtonProps={{ loading: saving }} onOk={() => void addRelation()}
        onCancel={() => { setRelationOpen(false); setEditRelationId(undefined); }} destroyOnHidden>
        <Form form={relationForm} layout="vertical">
          <Form.Item name="sourceEntityId" label="来源实体" rules={[{ required: true }]}>
            <Select disabled={editRelationId != null} options={entities.map(e => ({ label: e.businessName || e.name, value: e.id }))} />
          </Form.Item>
          <Form.Item name="targetEntityId" label="目标实体" rules={[{ required: true }]}>
            <Select disabled={editRelationId != null} options={entities.map(e => ({ label: e.businessName || e.name, value: e.id }))} />
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

      {handoffPreview && handoffPreview.logicalModelId === selected
        && handoffPreview.physicalModelId === physicalModelId
        && <Card title={`逻辑 v${handoffPreview.logicalVersionNo} → 物理 #${handoffPreview.physicalModelId} · 只读候选预检`}>
        <Alert type={handoffPreview.blockers.length ? 'warning' : 'info'} showIcon
          message={handoffPreview.blockers.length ? '仍有交接阻断项' : '仅已达到人工审阅条件'}
          description="字段的 stdFieldId 一致只代表可核对候选，不代表 SQL 映射、逻辑版本已发布、物理模型已生成或数据已执行。" />
        <div className="mt-3 flex flex-wrap gap-4">
          <Typography.Text type="secondary">冻结逻辑 v{handoffPreview.logicalVersionNo} · SHA-256：
            <Typography.Text copyable={{ text: handoffPreview.logicalSnapshotSha256 }}>
              {handoffPreview.logicalSnapshotSha256.slice(0, 16)}…
            </Typography.Text>
          </Typography.Text>
          <Typography.Text type="secondary">物理 {handoffPreview.physicalStatus} 当前结构 · SHA-256：
            <Typography.Text copyable={{ text: handoffPreview.physicalStructureSha256 }}>
              {handoffPreview.physicalStructureSha256.slice(0, 16)}…
            </Typography.Text>
          </Typography.Text>
        </div>
        <Typography.Paragraph className="!mt-2" type="secondary">
          指纹只标识本次读取的内容，不是物理模型正式版本。任何未来的人工确认必须重新读取并比对两端指纹，发生变化则拒绝提交。
        </Typography.Paragraph>
        {handoffPreview.blockers.map((blocker, i) => (
          <Typography.Paragraph key={i} type="danger" className="!mt-2 !mb-0">{blocker}</Typography.Paragraph>
        ))}
        <Table className="mt-3" size="small"
          rowKey={(row) => row.physicalColumnId == null
            ? `logical-${row.logicalAttributeId ?? row.logicalAttribute}`
            : `physical-${row.physicalColumnId}`}
          pagination={false}
          dataSource={handoffPreview.columns} columns={[
            { title: '物理字段', dataIndex: 'physicalColumn', render: (v?: string) => v || '未覆盖' },
            { title: '物理列 ID', dataIndex: 'physicalColumnId', render: (v?: number) => v ?? '—' },
            { title: '标准字段 ID', dataIndex: 'physicalStdFieldId', render: (id?: number) => id ?? '缺失' },
            { title: '逻辑实体', dataIndex: 'logicalEntity', render: (v?: string) => v || '待确认' },
            { title: '逻辑属性', dataIndex: 'logicalAttribute', render: (v?: string) => v || '待确认' },
            { title: '逻辑属性 ID', dataIndex: 'logicalAttributeId', render: (v?: number) => v ?? '—' },
            { title: '状态', dataIndex: 'result', render: (v: string) => <Tag>{v}</Tag> },
            { title: '原因', dataIndex: 'reason' },
            { title: '人工选择（仅本次浏览器会话）', key: 'selection',
              render: (_, row) => row.physicalColumnId == null ? '—' : (
                <Select<number> showSearch optionFilterProp="label" allowClear
                  placeholder="选择逻辑属性" className="min-w-[240px]" style={{ width: 260 }}
                  value={mappingSelections[row.physicalColumnId]}
                  options={logicalReviewOptions
                    .filter((a) => a.stdFieldId != null && a.stdFieldId === row.physicalStdFieldId)
                    .map((a) => ({ label: a.label, value: a.id }))}
                  onChange={(value) => {
                    setMappingSelections((prev) => {
                      const next = { ...prev };
                      if (value == null) delete next[row.physicalColumnId!];
                      else next[row.physicalColumnId!] = value;
                      return next;
                    });
                    setReviewResult(undefined);
                  }}
                />
              ) },
          ]} />
        <Space wrap className="mt-3">
          <Button type="primary" loading={reviewLoading}
            disabled={Object.keys(mappingSelections).length === 0}
            onClick={() => void validateExplicitReview()}>
            校验本次人工选择（只读，不保存）
          </Button>
          <Typography.Text type="secondary">
            已选 {Object.keys(mappingSelections).length} 项。本阶段仅可审阅、复查、修正，不创建正式映射或发布版本。
          </Typography.Text>
        </Space>
        {reviewResult
          && reviewSelectionSnapshot === JSON.stringify(mappingSelections)
          && reviewResult.logicalSnapshotSha256 === handoffPreview.logicalSnapshotSha256
          && reviewResult.physicalStructureSha256 === handoffPreview.physicalStructureSha256
          && reviewResult.logicalModelId === handoffPreview.logicalModelId
          && reviewResult.physicalModelId === handoffPreview.physicalModelId
          && (
          <div className="mt-4">
            <Alert showIcon type={reviewResult.readyForManualDesign ? 'info' : 'warning'}
              message={reviewResult.readyForManualDesign
                ? '本次人工选择通过只读校验（不是正式确认或发布）'
                : '本次选择存在阻断项，尚不能作为正式设计依据'}
              description={`校验状态：${reviewResult.status} · 检查通过的字段关联：${reviewResult.reviewedMappings.length} 项。服务端已重新校验逻辑与物理证据。`} />
            {reviewResult.blockers.map((blocker, idx) => (
              <Typography.Paragraph key={idx} type="danger" className="!mt-2 !mb-0">
                {blocker}
              </Typography.Paragraph>
            ))}
          </div>
        )}
      </Card>}
      <Modal width={760} title="冻结的逻辑设计内容（只读）" open={snapshotOpen}
        footer={<Button onClick={() => setSnapshotOpen(false)}>关闭</Button>}
        onCancel={() => setSnapshotOpen(false)}>
        <pre style={{ maxHeight: 420, overflow: 'auto', whiteSpace: 'pre-wrap' }}>{snapshotText}</pre>
      </Modal>
    </div>
  );
}
