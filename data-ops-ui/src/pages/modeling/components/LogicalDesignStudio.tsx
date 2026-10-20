import { Alert, Descriptions, Input, Segmented, Space, Table, Tag, Typography, theme } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import ReactFlow, { Background, Controls, Handle, MarkerType, Position, useNodesState } from 'reactflow';
import type { Edge, NodeProps, ReactFlowInstance } from 'reactflow';
import 'reactflow/dist/style.css';
import { YakButton, YakEmpty } from '@/components/ui';
import type { LogicalDraftDetail, LogicalId } from '@/services/modeling/logical';
import { parseLogicalId } from '@/services/modeling/logicalIdentity';
import type { SemanticFieldRecord } from '@/services/semantic/types';
import { LOGICAL_CANVAS_LIMIT, LOGICAL_CARD_ATTRIBUTE_LIMIT, LOGICAL_CARDINALITY,
  logicalDesignReminders, logicalDesignView, logicalEntityLabel, logicalEntityPositions } from '../editor/logicalDesign';
import type { LogicalObjectSelection } from '../editor/logicalDesign';

type EntityItem = LogicalDraftDetail['entities'][number];
type EntityNodeData = {
  item: EntityItem;
  activeAttributeId?: LogicalId;
  onSelect: (entityId: LogicalId, attributeId?: LogicalId) => void;
};

function LogicalEntityNode({ data, selected }: NodeProps<EntityNodeData>) {
  const { token } = theme.useToken();
  return <article aria-label={`实体 ${logicalEntityLabel(data.item.entity)}`} className="w-[270px] overflow-hidden rounded-xl border bg-white shadow-sm"
    style={{ borderColor: selected ? token.colorPrimary : '#dce2eb', boxShadow: selected ? `0 0 0 2px ${token.colorPrimaryBg}` : undefined }}>
    <Handle type="target" position={Position.Left} isConnectable={false} />
    <div className="border-b border-[#eef1f5] bg-[#f8fafc] px-3 py-3">
      <div className="text-[13px] font-semibold text-[#344054]">{logicalEntityLabel(data.item.entity)}</div>
      <div className="mt-1 text-[11px] text-[#98a2b3]">{data.item.entity.code}</div>
    </div>
    <div className="min-h-[42px] py-1">
      {data.item.attributes.slice(0, LOGICAL_CARD_ATTRIBUTE_LIMIT).map(attribute => <button key={attribute.id} type="button"
        className="nodrag flex w-full items-center justify-between gap-2 px-3 py-1.5 text-left hover:bg-[#f8fafc]"
        style={data.activeAttributeId === attribute.id ? { background: '#f2f4f7' } : undefined}
        aria-label={`定位属性 ${data.item.entity.code}.${attribute.code}`}
        onClick={event => { event.stopPropagation(); data.onSelect(data.item.entity.id, attribute.id); }}>
        <span className="min-w-0 truncate text-[12px] text-[#475467]">{attribute.primaryFlag && <span title="已声明业务主标识" className="mr-1 text-[#667085]">◇</span>}{attribute.name}</span>
        <span className="shrink-0 text-[10px] text-[#98a2b3]">{attribute.logicalType || '类型待补'}</span>
      </button>)}
      {!data.item.attributes.length && <div className="px-3 py-3 text-[12px] text-[#98a2b3]">尚无业务属性</div>}
    </div>
    <button type="button" className="nodrag w-full border-0 border-t border-solid border-[#eef1f5] bg-white px-3 py-2 text-left text-[11px] text-[#667085]"
      onClick={event => { event.stopPropagation(); data.onSelect(data.item.entity.id); }}>
      {data.item.attributes.length > LOGICAL_CARD_ATTRIBUTE_LIMIT
        ? `另有 ${data.item.attributes.length - LOGICAL_CARD_ATTRIBUTE_LIMIT} 个属性 · 查看完整属性`
        : `${data.item.attributes.length} 个属性 · 查看属性面板`}
    </button>
    <Handle type="source" position={Position.Right} isConnectable={false} />
  </article>;
}
const nodeTypes = { logicalEntity: LogicalEntityNode };

interface Props {
  detail: LogicalDraftDetail;
  standards: SemanticFieldRecord[];
  canEdit: boolean;
  busy: boolean;
  onEditEntity: (id?: LogicalId) => void;
  onEditAttribute: (entityId: LogicalId, id?: LogicalId) => void;
  onEditRelation: (id?: LogicalId, sourceEntityId?: LogicalId) => void;
}

/** Canvas and tables consume the same owner detail. Layout and selection stay in this session. */
export default function LogicalDesignStudio({ detail, standards, canEdit, busy, onEditEntity, onEditAttribute, onEditRelation }: Props) {
  const { token } = theme.useToken();
  const [mode, setMode] = useState<'canvas' | 'table'>('canvas');
  const [keyword, setKeyword] = useState('');
  const [selection, setSelection] = useState<LogicalObjectSelection>();
  const [focusId, setFocusId] = useState<LogicalId>();
  const [canvasSession, setCanvasSession] = useState(0);
  const [flow, setFlow] = useState<ReactFlowInstance>();
  const [nodes, setNodes, onNodesChange] = useNodesState<EntityNodeData>([]);
  const view = useMemo(() => logicalDesignView(detail, focusId), [detail, focusId]);
  const reminders = useMemo(() => logicalDesignReminders(detail), [detail]);
  const activeSelection = selection?.kind === 'entity'
    ? detail.entities.some(item => item.entity.id === selection.id) ? selection : undefined
    : selection && detail.relations.some(item => item.id === selection.id) ? selection : undefined;
  const selectedEntity = activeSelection?.kind === 'entity'
    ? detail.entities.find(item => item.entity.id === activeSelection.id) : undefined;
  const selectedAttribute = activeSelection?.kind === 'entity'
    ? selectedEntity?.attributes.find(item => item.id === activeSelection.attributeId) : undefined;
  const selectedRelation = activeSelection?.kind === 'relation'
    ? detail.relations.find(item => item.id === activeSelection.id) : undefined;
  const selectEntity = useCallback((id: LogicalId, attributeId?: LogicalId) => setSelection({ kind: 'entity', id, attributeId }), []);
  const nameOf = (id: LogicalId) => {
    const item = detail.entities.find(row => row.entity.id === id);
    return item ? logicalEntityLabel(item.entity) : `实体 #${id}（当前未解析）`;
  };
  const overBudget = view.entities.length > LOGICAL_CANVAS_LIMIT;
  const search = keyword.trim().toLowerCase();
  const matches = detail.entities.map(item => ({ ...item, hits: item.attributes.filter(attribute =>
    `${attribute.name} ${attribute.code}`.toLowerCase().includes(search)) }))
    .filter(item => !search || `${logicalEntityLabel(item.entity)} ${item.entity.code}`.toLowerCase().includes(search) || item.hits.length);
  const canWrite = canEdit && !busy;

  useEffect(() => {
    const positions = logicalEntityPositions(view.entities);
    setNodes(previous => view.entities.map(item => ({
      ...previous.find(node => node.id === String(item.entity.id)),
      id: String(item.entity.id), type: 'logicalEntity',
      position: previous.find(node => node.id === String(item.entity.id))?.position || positions.get(item.entity.id)!,
      selected: selection?.kind === 'entity' && selection.id === item.entity.id,
      data: { item, onSelect: selectEntity,
        activeAttributeId: selection?.kind === 'entity' && selection.id === item.entity.id ? selection.attributeId : undefined },
    })));
  }, [view.entities, selectEntity, selection, setNodes]);

  const edges: Edge[] = view.relations.map(relation => ({
    id: String(relation.id), source: String(relation.sourceEntityId), target: String(relation.targetEntityId),
    type: 'smoothstep', label: LOGICAL_CARDINALITY[relation.cardinality] || '基数待确认',
    selected: selectedRelation?.id === relation.id,
    markerEnd: { type: MarkerType.ArrowClosed, color: selectedRelation?.id === relation.id ? token.colorPrimary : '#98a2b3' },
    style: { stroke: selectedRelation?.id === relation.id ? token.colorPrimary : '#98a2b3', strokeWidth: 1.5,
      strokeDasharray: relation.cardinality === 'UNKNOWN' ? '5 4' : undefined },
    labelStyle: { fill: '#475467', fontSize: 11 },
    labelBgStyle: { fill: '#fff', fillOpacity: 0.95 },
    ariaLabel: `${nameOf(relation.sourceEntityId)} → ${nameOf(relation.targetEntityId)} · ${LOGICAL_CARDINALITY[relation.cardinality]}`,
  }));
  const focusEntity = (id: LogicalId) => { setFocusId(id); selectEntity(id); setCanvasSession(value => value + 1); };
  const restoreAll = () => { setFocusId(undefined); setCanvasSession(value => value + 1); };
  const arrange = () => {
    const positions = logicalEntityPositions(view.entities);
    setNodes(previous => previous.map(node => ({ ...node, position: positions.get(parseLogicalId(node.id)!)! })));
    setCanvasSession(value => value + 1);
  };
  const standardLabel = (id?: number) => {
    if (id == null) return '尚未关联标准';
    const field = standards.find(item => item.id === id);
    return field ? `${field.name}（${field.code}）` : `已保存引用 #${id}，当前未解析名称`;
  };

  return <section aria-label="逻辑设计工作区" className="overflow-hidden rounded-xl border border-[#e4e9f0] bg-white">
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#eef1f5] px-4 py-3">
      <Space wrap><span className="text-[14px] font-semibold">实体与关系</span>
        <span className="text-[12px] text-[#667085]">{detail.entities.length} 个实体 · {detail.entities.reduce((sum, item) => sum + item.attributes.length, 0)} 个属性 · {detail.relations.length} 条关系</span>
      </Space>
      <Space wrap>
        <Segmented aria-label="逻辑设计视图" value={mode} options={[{ value: 'canvas', label: '关系画布' }, { value: 'table', label: '表格设计' }]}
          onChange={value => setMode(value as 'canvas' | 'table')} />
        <YakButton disabled={!canWrite} onClick={() => onEditEntity()}>添加实体</YakButton>
        <YakButton disabled={!canWrite || detail.entities.length < 2}
          onClick={() => onEditRelation(undefined, selectedEntity?.entity.id)}>定义关系</YakButton>
      </Space>
    </div>
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-[#eef1f5] bg-[#f8fafc] px-4 py-2 text-[12px]">
      <span className="text-[#667085]">设计提醒</span>
      <button type="button" disabled={!reminders.missingStandards.length} className="text-[#475467] disabled:text-[#98a2b3]"
        onClick={() => { const item = reminders.missingStandards[0]; selectEntity(item.entityId, item.attributeId); }}>
        待关联标准 {reminders.missingStandards.length}
      </button>
      <button type="button" disabled={!reminders.missingPrimary.length} className="text-[#475467] disabled:text-[#98a2b3]"
        onClick={() => selectEntity(reminders.missingPrimary[0].entity.id)}>待声明主标识 {reminders.missingPrimary.length}</button>
      <button type="button" disabled={!reminders.pendingRelations.length} className="text-[#475467] disabled:text-[#98a2b3]"
        onClick={() => setSelection({ kind: 'relation', id: reminders.pendingRelations[0].id })}>基数待确认 {reminders.pendingRelations.length}</button>
      <span className="text-[11px] text-[#98a2b3]">提醒供人工核对，冻结仍按原有规则处理</span>
    </div>
    <div className="grid min-w-0 grid-cols-1 lg:grid-cols-[190px_minmax(0,1fr)] 2xl:grid-cols-[210px_minmax(0,1fr)_300px]">
      <aside aria-label="逻辑对象导航" className="border-b border-[#eef1f5] p-3 lg:border-b-0 lg:border-r">
        <Input.Search placeholder="查找实体或属性" allowClear value={keyword} onChange={event => setKeyword(event.target.value)} />
        <div className="mb-2 mt-3 text-[11px] text-[#98a2b3]">{search ? `匹配 ${matches.length} 个实体，画布范围不变` : '当前逻辑草稿的业务实体'}</div>
        <div className="max-h-[500px] space-y-1 overflow-auto">
          {matches.map(item => <div key={item.entity.id}>
            <button type="button" aria-label={`选择实体 ${logicalEntityLabel(item.entity)}`}
              aria-pressed={selectedEntity?.entity.id === item.entity.id}
              className="w-full rounded-lg px-2.5 py-2 text-left hover:bg-[#f8fafc]"
              style={selectedEntity?.entity.id === item.entity.id ? { background: '#f2f4f7', boxShadow: `inset 2px 0 ${token.colorPrimary}` } : undefined}
              onClick={() => selectEntity(item.entity.id)}>
              <span className="block truncate text-[13px] font-medium text-[#344054]">{logicalEntityLabel(item.entity)}</span>
              <span className="mt-1 block truncate text-[11px] text-[#98a2b3]">{item.entity.code} · {item.attributes.length} 属性</span>
            </button>
            {search && item.hits.map(attribute => <button type="button" key={attribute.id}
              className="block w-full truncate py-1 pl-5 pr-2 text-left text-[12px] text-[#667085] hover:bg-[#f8fafc]"
              aria-label={`定位属性 ${item.entity.code}.${attribute.code}`}
              onClick={() => selectEntity(item.entity.id, attribute.id)}>{attribute.name} · {attribute.code}</button>)}
          </div>)}
          {!matches.length && <div className="py-5 text-[12px] text-[#98a2b3]">{search ? '没有匹配的实体或属性' : '从添加实体开始设计'}</div>}
        </div>
      </aside>
      <div className="min-w-0">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#eef1f5] px-3 py-2">
          <span className="text-[11px] text-[#667085]">{focusId === undefined ? '当前草稿全部实体' : `聚焦 ${nameOf(focusId)} 的直接关联`} · {view.entities.length} 个实体</span>
          <Space size={4} wrap>
            <YakButton size="small" disabled={!selectedEntity} onClick={() => selectedEntity && focusEntity(selectedEntity.entity.id)}>聚焦关联</YakButton>
            {focusId !== undefined && <YakButton size="small" onClick={restoreAll}>显示全部</YakButton>}
            {mode === 'canvas' && <><YakButton size="small" disabled={!view.entities.length || overBudget} onClick={arrange}>自动排列</YakButton>
              <YakButton size="small" disabled={!flow || overBudget} onClick={() => void flow?.fitView({ padding: 0.2, duration: 200 })}>适应画布</YakButton></>}
          </Space>
        </div>
        {mode === 'canvas' ? <div className="h-[580px] bg-[#f8fafc]" aria-label="逻辑实体关系画布">
          {overBudget ? <div className="p-6"><Alert type="info" showIcon message={`当前范围有 ${view.entities.length} 个实体，超过画布 ${LOGICAL_CANVAS_LIMIT} 个的显示预算`}
            description="对象导航与表格保留全部实体；选择一个实体后聚焦直接关联，或使用表格设计。没有截断后隐藏的关系全图。" />
            <YakButton className="!mt-4" onClick={() => setMode('table')}>使用表格设计</YakButton></div>
            : !detail.entities.length ? <div className="p-8"><YakEmpty compact title="先定义一个业务实体"
              description="围绕当前业务过程明确实体、属性与业务主标识，再人工定义关系。" /></div>
              : <ReactFlow key={`${canvasSession}:${view.entities.map(item => item.entity.id).join(',')}`}
                nodes={nodes} edges={edges} nodeTypes={nodeTypes} onNodesChange={onNodesChange} onInit={setFlow}
                onNodeClick={(_, node) => selectEntity(parseLogicalId(node.id)!)}
                onEdgeClick={(_, edge) => setSelection({ kind: 'relation', id: parseLogicalId(edge.id)! })}
                nodesConnectable={false} edgesUpdatable={false} deleteKeyCode={null} fitView minZoom={0.2} maxZoom={1.5}
                fitViewOptions={{ padding: 0.2, maxZoom: 0.95 }}>
                <Background gap={20} color="#e1e6ee" />
                <Controls showInteractive={false} />
              </ReactFlow>}
        </div> : <div className="min-h-[580px] space-y-5 p-4">
          <div><div className="mb-2 text-[13px] font-medium">业务实体</div>
            <Table<EntityItem> rowKey={item => item.entity.id} size="small" dataSource={view.entities} pagination={{ pageSize: 8, showSizeChanger: false, showTotal: total => `共 ${total} 个实体` }}
              columns={[
                { title: '实体', render: (_, item) => <button type="button" className="text-left" onClick={() => selectEntity(item.entity.id)}>
                  <span className="block text-[#344054]">{logicalEntityLabel(item.entity)}</span><span className="text-[11px] text-[#98a2b3]">{item.entity.code}</span></button> },
                { title: '属性', render: (_, item) => item.attributes.length },
                { title: '业务主标识', render: (_, item) => item.attributes.filter(attribute => attribute.primaryFlag).map(attribute => attribute.name).join('、') || '待声明' },
                { title: '操作', render: (_, item) => <YakButton size="small" onClick={() => selectEntity(item.entity.id)}>查看属性</YakButton> },
              ]} />
          </div>
          <div><div className="mb-2 text-[13px] font-medium">实体关系</div>
            <Table size="small" rowKey="id" dataSource={[...view.relations, ...view.unresolved]} pagination={{ pageSize: 8, showSizeChanger: false }}
              columns={[
                { title: '来源 → 目标', render: (_, relation) => `${nameOf(relation.sourceEntityId)} → ${nameOf(relation.targetEntityId)}` },
                { title: '基数', render: (_, relation) => LOGICAL_CARDINALITY[relation.cardinality] },
                { title: '操作', render: (_, relation) => <YakButton size="small" onClick={() => setSelection({ kind: 'relation', id: relation.id })}>查看关系</YakButton> },
              ]} />
          </div>
        </div>}
        <p className="m-0 border-t border-[#eef1f5] px-3 py-2 text-[11px] text-[#98a2b3]">拖动和排列只改变本次布局。连线表达实体与基数；字段对、JOIN 和加载逻辑需另行设计。</p>
      </div>
      <aside aria-label="逻辑对象属性" className="min-w-0 border-t border-[#eef1f5] p-4 lg:col-span-2 2xl:col-span-1 2xl:border-l 2xl:border-t-0">
        {!canEdit && <Tag className="!mb-3">当前只读</Tag>}
        {selectedEntity ? <>
          <div className="mb-1 text-[11px] text-[#98a2b3]">业务实体 · #{selectedEntity.entity.id}</div>
          <div className="break-all text-[16px] font-semibold">{logicalEntityLabel(selectedEntity.entity)}</div>
          <div className="mb-3 mt-1 break-all text-[12px] text-[#98a2b3]">{selectedEntity.entity.code}</div>
          <p className="text-[12px] text-[#667085]">{selectedEntity.entity.description || '尚无业务说明'}</p>
          <Space wrap><YakButton size="small" disabled={!canWrite} onClick={() => onEditEntity(selectedEntity.entity.id)}>编辑实体</YakButton>
            <YakButton size="small" disabled={!canWrite} onClick={() => onEditAttribute(selectedEntity.entity.id)}>添加属性</YakButton></Space>
          <div className="mb-2 mt-5 text-[13px] font-medium">完整属性 · {selectedEntity.attributes.length}</div>
          <Table size="small" rowKey="id" dataSource={selectedEntity.attributes} pagination={{ pageSize: 8, showSizeChanger: false }}
            columns={[{ title: '属性', render: (_, attribute) => <button type="button" className="text-left"
              aria-label={`查看属性 ${selectedEntity.entity.code}.${attribute.code}`}
              onClick={() => selectEntity(selectedEntity.entity.id, attribute.id)}>
              <span className="block text-[12px]">{attribute.primaryFlag ? '◇ ' : ''}{attribute.name}</span>
              <span className="break-all text-[10px] text-[#98a2b3]">{attribute.code}</span></button> },
            { title: '类型', dataIndex: 'logicalType', render: value => <span className="text-[11px]">{value || '待补'}</span> }]} />
          {selectedAttribute && <section aria-label="当前逻辑属性" className="mt-4 rounded-lg border border-[#e4e9f0] bg-[#f8fafc] p-3">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2"><span className="text-[13px] font-semibold">{selectedAttribute.name}</span>
              <YakButton size="small" disabled={!canWrite} onClick={() => onEditAttribute(selectedEntity.entity.id, selectedAttribute.id)}>编辑属性</YakButton></div>
            <Descriptions size="small" column={1} items={[
              { key: 'code', label: '编码', children: selectedAttribute.code },
              { key: 'type', label: '逻辑类型', children: selectedAttribute.logicalType || '待补充' },
              { key: 'pk', label: '主标识', children: selectedAttribute.primaryFlag ? '已声明' : '未声明' },
              { key: 'null', label: '可空', children: selectedAttribute.nullable == null ? '尚未声明' : selectedAttribute.nullable ? '是' : '否' },
              { key: 'std', label: '标准', children: standardLabel(selectedAttribute.stdFieldId) },
              { key: 'desc', label: '业务说明', children: selectedAttribute.description || '尚未填写' },
            ]} />
          </section>}
        </> : selectedRelation ? <>
          <div className="mb-2 text-[11px] text-[#98a2b3]">实体关系 · #{selectedRelation.id}</div>
          <div className="break-all text-[14px] font-medium">{nameOf(selectedRelation.sourceEntityId)} → {nameOf(selectedRelation.targetEntityId)}</div>
          <Tag className="!my-3" color={selectedRelation.cardinality === 'UNKNOWN' ? 'warning' : 'default'}>{LOGICAL_CARDINALITY[selectedRelation.cardinality]}</Tag>
          <p className="text-[12px] text-[#667085]">{selectedRelation.description || '尚无关系业务说明'}</p>
          <YakButton size="small" disabled={!canWrite} onClick={() => onEditRelation(selectedRelation.id)}>编辑关系</YakButton>
          <div className="mt-5 space-y-2">
            {[['来源', selectedRelation.sourceEntityId], ['目标', selectedRelation.targetEntityId]].map(([label, id]) => <div key={label}>
              <YakButton size="small" disabled={!detail.entities.some(item => item.entity.id === id)}
                onClick={() => selectEntity(parseLogicalId(id)!)}>查看{label}实体</YakButton>
            </div>)}
          </div>
          <Typography.Paragraph type="secondary" className="!mt-4 !text-[12px]">基数按来源 → 目标解读。关系不证明外键约束或加工 JOIN 已实现。</Typography.Paragraph>
        </> : <div className="py-10"><YakEmpty compact title="选择一个设计对象" description="点击实体、属性或关系，查看当前定义并继续编辑。" /></div>}
        <p className="mb-0 mt-4 border-t border-[#eef1f5] pt-3 text-[11px] text-[#98a2b3]">此处展示当前草稿；标准有效性与历史差异在冻结版本的语义审阅中核验。</p>
      </aside>
    </div>
    {!!view.unresolved.length && <Alert type="warning" showIcon message={`${view.unresolved.length} 条关系的端点在当前草稿中未解析，画布未绘制这些连线，请在表格中核对。`} />}
  </section>;
}
