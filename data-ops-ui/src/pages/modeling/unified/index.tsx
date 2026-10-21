import { history, useLocation, useParams } from '@umijs/max';
import { message, Modal, Tabs, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { useLatestOperation } from '@/hooks/useLatestOperation';
import { ArrowLeftRight, Columns3, Database, GitBranch, History, Info, Network, RefreshCw } from 'lucide-react';
import { YakButton, YakEmpty } from '@/components/ui';
import { MODELING_PUBLISHED_EVENT, MODELING_STATUS_LABELS } from '@/pages/modeling/constants';
import ModelBasicInfoPanel from '@/pages/modeling/components/ModelBasicInfoPanel';
import ModelPhysicalTablePanel from '@/pages/modeling/components/ModelPhysicalTablePanel';
import ModelLineagePanel from '@/pages/modeling/components/ModelLineagePanel';
import ModelVersionPanel from '@/pages/modeling/components/ModelVersionPanel';
import ModelingModelDetail from '@/pages/modeling/detail';
import LogicalPhysicalEvidencePanel from '@/pages/modeling/components/LogicalPhysicalEvidencePanel';
import type { ModelStructureEditorStatus } from '@/pages/modeling/detail';
import ModelLifecycleTab from '@/pages/data-lifecycle/components/ModelLifecycleTab';
import ModelingStudioHeader from '@/pages/modeling/components/ModelingStudioHeader';
import MappingPanel from '@/pages/modeling/mapping';
import { listDataSources } from '@/services/data-source/api';
import type { DataSourceRecord } from '@/services/data-source/types';
import { getModelingModel } from '@/services/modeling/api';
import { verifiedOdsModelSourceReturn } from '@/utils/liveSourceOdsJourney';
import type { ModelingModelRecord } from '@/services/modeling/types';
import { listSemanticLayers } from '@/services/semantic/api';
import type { SemanticLayerRecord } from '@/services/semantic/types';
import '../dwdWorkspace.less';

/** 模型统一视图(ticket 25):一页看清全貌;各维度空态优雅降级、懒加载。 */
const UnifiedModelView: React.FC = () => {
  const params = useParams<{ id?: string }>();
  const modelId = params.id;
  const location = useLocation();
  const queryTab = new URLSearchParams(location.search).get('tab');
  const { currentProject } = useSecurityProject();
  const modelScope = JSON.stringify([currentProject?.id, modelId]);
  const beginModelRead = useLatestOperation(modelScope);
  const [loadedModel, setLoadedModel] = useState<{ scope: string; model: ModelingModelRecord }>();
  const model = loadedModel?.scope === modelScope ? loadedModel.model : undefined;
  const hasLogicalBasis = model?.id != null && String(model.id) === modelId
    && Boolean(model.layerCode) && model.layerCode?.toUpperCase() !== 'ODS';
  const showPhysicalTable = !model || (String(model.id) === modelId && model.layerCode?.toUpperCase() === 'ODS');
  const tabKeys = ['overview', 'structure', 'mapping', 'lineage', 'lifecycle', 'version',
    ...(showPhysicalTable ? ['physical'] : []),
    ...(hasLogicalBasis ? ['logical'] : [])];
  const activeTab = queryTab && tabKeys.includes(queryTab) ? queryTab : 'structure';
  const [structureEditor, setStructureEditor] = useState<{
    modelId?: string; state: ModelStructureEditorStatus;
  }>();
  const reportStructureEditor = useCallback((state: ModelStructureEditorStatus) => {
    setStructureEditor({ modelId, state });
  }, [modelId]);
  const [mappingEditor, setMappingEditor] = useState<{ scope: string; state: ModelStructureEditorStatus }>();
  const reportMappingEditor = useCallback((state: ModelStructureEditorStatus) => {
    setMappingEditor({ scope: modelScope, state });
  }, [modelScope]);

  const sourceReturnPath = model?.id != null && String(model.id) === modelId
    ? verifiedOdsModelSourceReturn(model, new URLSearchParams(location.search))
    : null;
  const [layers, setLayers] = useState<SemanticLayerRecord[]>([]);
  const [datasources, setDatasources] = useState<DataSourceRecord[]>([]);
  const [loadError, setLoadError] = useState(false);
  const guardDraftAndNavigate = (navigate: () => void, returningToSource = false) => {
    const editingDwdMapping = model?.layerCode?.toUpperCase() === 'DWD' && activeTab === 'mapping';
    const editor = editingDwdMapping
      ? mappingEditor?.scope === modelScope ? mappingEditor.state : undefined
      : structureEditor && structureEditor.modelId === modelId ? structureEditor.state : undefined;
    if (editor?.loading || editor?.saving) {
      message.warning(editingDwdMapping ? '来源映射正在加载或保存，请完成后再离开。' : '模型表结构正在加载或保存，请完成后再离开。');
      return;
    }
    if (editor?.dirty) {
      Modal.confirm({
        title: editingDwdMapping ? '来源映射还有未保存的修改' : returningToSource ? '物理模型还有未保存的改动' : '当前物理表结构还有未保存改动',
        content: editingDwdMapping ? '离开会放弃当前未保存的映射修改。'
          : returningToSource
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
    const current = beginModelRead();
    getModelingModel(modelId)
      .then((data) => { if (current()) { setLoadedModel({ scope: modelScope, model: data }); setLoadError(false); } })
      .catch(() => {
        if (!current()) return;
        setLoadError(true);
        message.error('加载模型信息失败');
      });
  }, [modelId, modelScope, beginModelRead]);

  useEffect(() => {
    let active = true;
    if (!modelId) {
      return;
    }
    setLoadedModel(undefined); setLayers([]); setDatasources([]); setLoadError(false);
    reloadModel();
    // 概览"分层/目标库/数据源"经分层配置与数据源目录解析(2026-09-17),失败不阻断概览
    listSemanticLayers()
      .then((list) => { if (active) setLayers(list ?? []); })
      .catch(() => { if (active) setLayers([]); });
    listDataSources({ pageNo: 1, pageSize: 200 })
      .then((result) => { if (active) setDatasources(result.bizData ?? []); })
      .catch(() => { if (active) setDatasources([]); });
    return () => { active = false; };
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
  const isOdsModel = model?.layerCode?.toUpperCase() === 'ODS';
  const isDwdModel = model?.layerCode?.toUpperCase() === 'DWD';
  const databaseName = layer?.databaseName ?? '';
  const datasourceName = layer?.datasourceId
    ? (datasources.find((item) => item.id === layer.datasourceId)?.name ?? `#${layer.datasourceId}`)
    : '';

  // 来源数据源信息(用于血缘展示)
  const sourceDatasourceName = model?.sourceDatasourceId
    ? (datasources.find((item) => item.id === model.sourceDatasourceId)?.name ?? `#${model.sourceDatasourceId}`)
    : null;
  const sourceFullTable = model?.sourceTable
    ? [model.sourceDatabase, model.sourceSchema, model.sourceTable].filter(Boolean).join('.')
    : null;

  const overview = model ? (
    <ModelBasicInfoPanel model={model} layerLabel={layerLabel}
      databaseName={databaseName} datasourceName={datasourceName}
      sourceDatasourceName={sourceDatasourceName} sourceFullTable={sourceFullTable} />
  ) : (
    <YakEmpty
      compact
      title={loadError ? '加载失败' : '加载中…'}
      description={loadError ? '请返回列表后重试' : undefined}
    />
  );

  const openTab = (tab: string) => {
    if (tab === activeTab) return;
    const query = new URLSearchParams(location.search);
    query.set('tab', tab);
    guardDraftAndNavigate(() => history.replace({ pathname: location.pathname, search: '?' + query.toString() }));
  };

  // Keep legacy deep links, but only surface tasks that have real working panels.
  const sections = [
    { key: 'overview', name: '基本信息', tabs: ['overview'] },
    { key: 'design', name: '设计工作台', tabs: hasLogicalBasis
      ? ['structure', 'logical', 'mapping'] : ['structure', ...(showPhysicalTable ? ['physical'] : []), 'mapping'] },
    { key: 'history', name: '版本与关联', tabs: ['version', 'lineage', 'lifecycle'] },
  ];
  const section = sections.find((item) => item.tabs.includes(activeTab)) ?? sections[0];
  const items = [
    { key: 'overview', label: '基本信息', icon: <Info size={16} aria-hidden />,
      children: overview },
    { key: 'structure', label: '模型设计', icon: <Columns3 size={16} aria-hidden />,
      children: <ModelingModelDetail key={modelId} layerCode={model?.layerCode} onEditorStateChange={reportStructureEditor}
        onNavigateTab={isDwdModel ? openTab : undefined} /> },
    ...(showPhysicalTable ? [{ key: 'physical', label: '物理表', icon: <Database size={16} aria-hidden />,
      children: model && String(model.id) === modelId ? <ModelPhysicalTablePanel model={model}
        defaultTargetId={layer?.datasourceId} defaultDatabase={layer?.databaseName} />
        : <YakEmpty compact title={loadError ? '加载失败' : '加载模型中…'} /> }] : []),
    ...(hasLogicalBasis ? [{ key: 'logical', label: '业务逻辑依据', icon: <GitBranch size={16} aria-hidden />, children: <LogicalPhysicalEvidencePanel
      model={model?.id != null && String(model.id) === modelId ? model : undefined}
      search={location.search}
      onNavigateLogical={(path) => guardDraftAndNavigate(() => history.push(path))}
    /> }] : []),
    { key: 'mapping', label: '来源映射', icon: <ArrowLeftRight size={16} aria-hidden />,
      children: <MappingPanel layerCode={model?.layerCode} onEditorStateChange={isDwdModel ? reportMappingEditor : undefined} /> },
    { key: 'version', label: '版本管理', icon: <History size={16} aria-hidden />, children: <ModelVersionPanel /> },
    { key: 'lineage', label: '血缘关系', icon: <Network size={16} aria-hidden />, children: <ModelLineagePanel /> },
    { key: 'lifecycle', label: '生命周期', icon: <RefreshCw size={16} aria-hidden />,
      children: <ModelLifecycleTab modelId={modelId ? Number(modelId) : null} /> },
  ];
  const layerDescription: Record<string, string> = {
    ODS: '核对来源表、字段与物理结构，并保持保存后的来源回链。',
    DWD: '以逻辑设计和可信 ODS 来源为依据，完成明细映射与标准核验。',
    DIM: '确认维度业务键、标准字段与物理设计版本。',
    DWS: '在模型内完成可信来源、统计粒度、度量验证、发布审核和版本恢复。',
    ADS: '围绕应用目标、输出字段、指标口径和消费交接设计输出合同。',
  };

  return (
    <div className={'modeling-studio-page modeling-studio-detail' + (isOdsModel ? ' modeling-studio-detail-ods' : '')
      + (isDwdModel ? ' modeling-studio-detail-dwd' : '')}>
      <ModelingStudioHeader area="physical" title={model?.name || '物理模型设计工作台'}
        eyebrow={isOdsModel || isDwdModel ? null : '数据建设 / 数仓建模 / 模型设计'}
        description={isOdsModel || isDwdModel ? undefined : (layerDescription[(model?.layerCode || '').toUpperCase()] ||
          '在同一物理模型对象中核对业务依据、数据结构、映射与发布版本。')}
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

      {!isOdsModel && !isDwdModel ? <section className="modeling-studio-detail-context" aria-label="当前模型设计边界">
        <div><strong>当前对象</strong><div>{model?.name || '加载模型中'} · {model?.layerCode || '未配置分层'}</div></div>
        <div><strong>设计目标</strong><div>{model?.processName || '业务过程未关联'}</div></div>
        <div><strong>发布事实</strong><div>{model?.status ? (MODELING_STATUS_LABELS[model.status] || model.status) : '加载中'}</div></div>
        <span>设计发布不代表 SQL 运行、目标数据写入或消费授权</span>
      </section> : null}

      <Tabs id="modeling-detail" className="modeling-studio-workspace" tabPosition="left"
        activeKey={activeTab} onChange={openTab}
        items={items.filter((item) => section.tabs.includes(item.key))}
        renderTabBar={({ id }) => isDwdModel ? (
          <nav className="modeling-studio-dwd-nav" aria-label="DWD 建模导航">
            {items.map(item => <button key={item.key} type="button"
              id={`${id}-tab-${item.key}`} aria-current={activeTab === item.key ? 'page' : undefined}
              aria-controls={`${id}-panel-${item.key}`}
              className={'modeling-studio-nav-item' + (activeTab === item.key ? ' is-active' : '')}
              onClick={() => openTab(item.key)}>{item.icon}<span>{item.label}</span></button>)}
          </nav>
        ) : (
          <nav className="modeling-studio-detail-nav" aria-label="模型详情导航">
            <div className="modeling-studio-detail-nav-inner">
              {sections.map((group) => (
                <div key={group.key} className="modeling-studio-nav-group">
                  {group.key !== 'overview' ? <h2>{group.name}</h2> : null}
                  {items.filter((item) => group.tabs.includes(item.key)).map((item) => (
                    <button key={item.key} type="button" id={`${id}-tab-${item.key}`}
                      aria-current={activeTab === item.key ? 'page' : undefined}
                      aria-controls={`${id}-panel-${item.key}`}
                      className={'modeling-studio-nav-item' + (activeTab === item.key ? ' is-active' : '')}
                      onClick={() => openTab(item.key)}>
                      {item.icon}<span>{item.label}</span>
                    </button>
                  ))}
                </div>
              ))}
            </div>
          </nav>
        )} />
    </div>
  );

};

export default UnifiedModelView;
