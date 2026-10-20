import { history, useLocation, useParams } from '@umijs/max';
import { Descriptions, message, Modal, Tabs, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { YakButton, YakEmpty } from '@/components/ui';
import { MODELING_DIALECT_LABELS, MODELING_PUBLISHED_EVENT, MODELING_STATUS_LABELS } from '@/pages/modeling/constants';
import ModelLineagePanel from '@/pages/modeling/components/ModelLineagePanel';
import ModelVersionPanel from '@/pages/modeling/components/ModelVersionPanel';
import ModelingModelDetail from '@/pages/modeling/detail';
import LogicalPhysicalEvidencePanel from '@/pages/modeling/components/LogicalPhysicalEvidencePanel';
import type { ModelStructureEditorStatus } from '@/pages/modeling/detail';
import ModelLifecycleTab from '@/pages/data-lifecycle/components/ModelLifecycleTab';
import ModelingStudioHeader from '@/pages/modeling/components/ModelingStudioHeader';
import LayerFieldMappingPage from '@/pages/modeling/layer-mapping';
import MappingPanel from '@/pages/modeling/mapping';
import { listDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { getModelingModel } from '@/services/modeling/api';
import { verifiedOdsModelSourceReturn } from '@/utils/liveSourceOdsJourney';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { listSemanticLayers } from '@/services/semantic/api';
import type { SemanticLayerRecord } from '@/services/semantic/types';

/** 模型统一视图(ticket 25):一页看清全貌;各维度空态优雅降级、懒加载。 */
const UnifiedModelView: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const modelId = params.id;
  const location = useLocation();
  const queryTab = new URLSearchParams(location.search).get('tab');
  const tabKeys = ['overview', 'logical', 'structure', 'mapping', 'layer-mapping', 'lineage', 'lifecycle', 'version'];
  const activeTab = queryTab && tabKeys.includes(queryTab) ? queryTab : 'structure';
  const [model, setModel] = useState<ModelingModelRecord>();
  const [structureEditor, setStructureEditor] = useState<{
    modelId?: string; state: ModelStructureEditorStatus;
  }>();
  const reportStructureEditor = useCallback((state: ModelStructureEditorStatus) => {
    setStructureEditor({ modelId, state });
  }, [modelId]);

  const sourceReturnPath = model?.id != null && String(model.id) === modelId
    ? verifiedOdsModelSourceReturn(model, new URLSearchParams(location.search))
    : null;
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [loadError, setLoadError] = useState(false);
  const guardDraftAndNavigate = (navigate: () => void, returningToSource = false) => {
    const editor = structureEditor && structureEditor.modelId === modelId ? structureEditor.state : undefined;
    if (editor?.loading || editor?.saving) {
      message.warning('模型表结构正在加载或保存，请完成后再离开。');
      return;
    }
    if (editor?.dirty) {
      Modal.confirm({
        title: returningToSource ? '物理模型还有未保存的改动' : '当前物理表结构还有未保存改动',
        content: returningToSource
          ? '离开会放弃当前未保存的字段、主键、索引和表属性修改；请先保存，或者明确放弃后返回来源表。'
          : '离开当前字段编辑界面会丢弃未保存的字段、主键、索引和表属性修改。请先保存，或明确放弃后继续。',
        okText: returningToSource ? '放弃未保存改动并返回' : '放弃修改并继续', okType: 'danger',
        cancelText: '继续编辑', onOk: navigate,
      });
      return;
    }
    navigate();
  };
  const returnToVerifiedSource = () => {
    if (!sourceReturnPath) return;
    guardDraftAndNavigate(() => history.push(sourceReturnPath), true);
  };

  const reloadModel = useCallback(() => {
    if (!modelId) {
      return;
    }
    getModelingModel(modelId)
      .then((data) => setModel(data))
      .catch(() => {
        setLoadError(true);
        message.error('加载模型信息失败');
      });
  }, [modelId]);

  useEffect(() => {
    if (!modelId) {
      return;
    }
    reloadModel();
    // 概览"分层/目标库/数据源"经分层配置与数据源目录解析(2026-09-17),失败不阻断概览
    listSemanticLayers()
      .then((list) => setLayers(list ?? []))
      .catch(() => setLayers([]));
    listDataSources({ pageNo: 1, pageSize: 200 })
      .then((result) => setDatasources(result.bizData ?? []))
      .catch(() => setDatasources([]));
  }, [modelId, reloadModel]);

  // 发布联动(01):直发或审批回调发布后,头部状态 Tag 与基本信息 Tab 同步刷新
  useEffect(() => {
    const onPublished = (event: Event) => {
      const detail = (event as CustomEvent<{ modelId?: string | number }>).detail;
      if (detail?.modelId != null && String(detail.modelId) !== String(modelId)) return;
      reloadModel();
    };
    window.addEventListener(MODELING_PUBLISHED_EVENT, onPublished);
    return () => window.removeEventListener(MODELING_PUBLISHED_EVENT, onPublished);
  }, [modelId, reloadModel]);

  // 模型关联分层 → 目标库/数据源展示名(只读解析,不改后端)
  const layer = model?.layerCode ? layers.find((item) => item.code === model.layerCode) : undefined;
  const layerLabel = layer ? `${layer.name}（${layer.code}）` : (model?.layerCode ?? '未关联');
  const databaseName = layer?.databaseName ?? '-';
  const datasourceName = layer?.datasourceId
    ? (datasources.find((item) => item.id === layer.datasourceId)?.name ?? `#${layer.datasourceId}`)
    : '-';

  // 来源数据源信息(用于血缘展示)
  const sourceDatasourceName = model?.sourceDatasourceId
    ? (datasources.find((item) => item.id === model.sourceDatasourceId)?.name ?? `#${model.sourceDatasourceId}`)
    : null;
  const sourceFullTable = model?.sourceDatabase && model?.sourceTable
    ? `${model.sourceDatabase}.${model.sourceTable}`
    : model?.sourceTable ?? null;

  const overview = model ? (
    <div className="max-w-[880px]">
      <Descriptions
        bordered
        size="small"
        column={2}
        items={[
          { key: 'code', label: '模型编码', children: model.code },
          { key: 'name', label: '模型名称', children: model.name },
          { key: 'layer', label: '模型分层', children: layerLabel },
          {
            key: 'domain',
            label: '业务域',
            children: model.domainName || '未关联',
          },
          {
            key: 'process',
            label: '业务过程',
            children: model.processName || '未关联',
          },
          { key: 'database', label: '目标库', children: databaseName },
          { key: 'datasource', label: '数据源', children: datasourceName },
          {
            key: 'dialect',
            label: '方言',
            children: MODELING_DIALECT_LABELS[model.dialect || ''] || model.dialect || '-',
          },
          {
            key: 'status',
            label: '状态',
            children: MODELING_STATUS_LABELS[model.status || ''] || model.status || '-',
          },
          { key: 'description', label: '描述', children: model.description || '-', span: 2 },
          // 来源信息(其他)
          ...(sourceDatasourceName || sourceFullTable
            ? [
                { key: 'sourceHeader', label: '来源信息', children: '', span: 2 },
                { key: 'sourceDatasource', label: '来源数据源', children: sourceDatasourceName || '-' },
                { key: 'sourceTable', label: '来源表', children: sourceFullTable || '-' },
              ]
            : []),
        ]}
      />
    </div>
  ) : (
    <YakEmpty
      compact
      title={loadError ? '加载失败' : '加载中…'}
      description={loadError ? '请返回列表后重试' : undefined}
    />
  );

  const openTab = (tab: string) => {
    const query = new URLSearchParams(location.search);
    query.set('tab', tab);
    guardDraftAndNavigate(() => history.replace({ pathname: location.pathname, search: '?' + query.toString() }));
  };

  // Keep legacy deep links, but only surface tasks that have real working panels.
  const sections = [
    { key: 'design', name: '设计工作台', description: '结构、逻辑与映射', tabs: ['structure', 'logical', 'mapping', 'layer-mapping'], defaultTab: 'structure' },
    { key: 'history', name: '版本与关联', description: '版本、血缘和生命周期', tabs: ['version', 'lineage', 'lifecycle'], defaultTab: 'version' },
    { key: 'overview', name: '模型资料', description: '业务与技术归属', tabs: ['overview'], defaultTab: 'overview' },
  ];
  const section = sections.find((item) => item.tabs.includes(activeTab)) ?? sections[0];
  const items = [
    { key: 'structure', label: '模型设计', children: <ModelingModelDetail key={modelId} onEditorStateChange={reportStructureEditor} /> },
    { key: 'logical', label: '业务逻辑依据', children: <LogicalPhysicalEvidencePanel
      model={model?.id != null && String(model.id) === modelId ? model : undefined}
      search={location.search}
      onNavigateLogical={(path) => guardDraftAndNavigate(() => history.push(path))}
    /> },
    { key: 'mapping', label: '来源映射', children: <MappingPanel /> },
    { key: 'layer-mapping', label: '字段分层映射', children: <LayerFieldMappingPage /> },
    { key: 'version', label: '设计版本', children: <ModelVersionPanel /> },
    { key: 'lineage', label: '血缘关系', children: <ModelLineagePanel /> },
    { key: 'lifecycle', label: '生命周期', children: <ModelLifecycleTab modelId={modelId ? Number(modelId) : null} /> },
    { key: 'overview', label: '模型基本信息', children: overview },
  ];
  const layerDescription: Record<string, string> = {
    ODS: '核对来源表、字段与物理结构，并保持保存后的来源回链。',
    DWD: '以逻辑设计和可信 ODS 来源为依据，完成明细映射与标准核验。',
    DIM: '确认维度业务键、标准字段与物理设计版本。',
    DWS: '在模型内完成可信来源、统计粒度、度量验证、发布审核和版本恢复。',
    ADS: '围绕应用目标、输出字段、指标口径和消费交接设计输出合同。',
  };

  return (
    <div className="modeling-studio-page modeling-studio-detail">
      <ModelingStudioHeader area="physical" title={model?.name || '物理模型设计工作台'}
        eyebrow="数据建设 / 数仓建模 / 模型设计"
        description={layerDescription[(model?.layerCode || '').toUpperCase()] ||
          '在同一物理模型对象中核对业务依据、数据结构、映射与发布版本。'}
        context={<>
          <span>分层：{layerLabel}</span>
          {model?.code ? <span>编码：{model.code}</span> : null}
          {model?.processName ? <span>业务过程：{model.processName}</span> : null}
          {model?.status ? <Tag>{MODELING_STATUS_LABELS[model.status] || model.status}</Tag> : null}
        </>}
        actions={<>
          {sourceReturnPath ? <YakButton onClick={returnToVerifiedSource}>返回来源表</YakButton> : null}
          <YakButton onClick={() => guardDraftAndNavigate(() => history.push('/modeling'))}>返回模型库</YakButton>
        </>} />

      <section className="modeling-studio-detail-context" aria-label="当前模型设计边界">
        <div><strong>当前对象</strong><div>{model?.name || '加载模型中'} · {model?.layerCode || '未配置分层'}</div></div>
        <div><strong>设计目标</strong><div>{model?.processName || '业务过程未关联'}</div></div>
        <div><strong>发布事实</strong><div>{model?.status ? (MODELING_STATUS_LABELS[model.status] || model.status) : '加载中'}</div></div>
        <span>设计发布不代表 SQL 运行、目标数据写入或消费授权</span>
      </section>

      <nav className="modeling-studio-group-switch" aria-label="模型详情工作区域">
        {sections.map((entry) => (
          <button key={entry.key} type="button"
            aria-current={section.key === entry.key ? 'page' : undefined}
            className={'modeling-studio-group-button' + (section.key === entry.key ? ' is-active' : '')}
            onClick={() => openTab(entry.defaultTab)}>
            <strong>{entry.name}</strong><span>{entry.description}</span>
          </button>
        ))}
      </nav>
      <section className="modeling-studio-content" aria-label={section.name}>
        <Tabs activeKey={activeTab} onChange={openTab}
          items={items.filter((item) => section.tabs.includes(item.key))} />
      </section>
    </div>
  );

};

export default UnifiedModelView;
