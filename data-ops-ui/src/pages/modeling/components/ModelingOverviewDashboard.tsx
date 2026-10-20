import { history } from '@umijs/max';
import { Alert, Button, Empty, Skeleton, Tag } from 'antd';
import { ArrowRight, Boxes, GitBranch, Layers3, RefreshCw, Workflow } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import usePermissionAccess from '@/hooks/usePermissionAccess';
import { pageModelingModels } from '@/services/modeling/api';
import { listLogicalDrafts } from '@/services/modeling/logical';
import type { ModelingModelRecord, ModelingPageResult } from '@/services/modeling/types';
import { listSemanticLayers, pageSemanticProcesses } from '@/services/semantic/api';
import { formatModelingTimeMinute, MODELING_STATUS_LABELS } from '@/pages/modeling/constants';
import ModelingStudioHeader from './ModelingStudioHeader';
import './ModelingOverviewDashboard.less';

const LAYERS = [
  { code: 'ODS', name: '贴源层' },
  { code: 'DWD', name: '明细层' },
  { code: 'DIM', name: '公共维度层' },
  { code: 'DWS', name: '汇总层' },
  { code: 'ADS', name: '应用层' },
] as const;
type LayerStats = { code: string; count?: number; configured?: boolean };
type OverviewData = {
  projectId: number;
  physicalCount?: number;
  processCount?: number;
  logicalCount?: number;
  draftCount?: number;
  recent: ModelingModelRecord[];
  drafts: ModelingModelRecord[];
  layerStats: LayerStats[];
  partialFailure: boolean;
};

const countOf = (page: ModelingPageResult | undefined): number | undefined => {
  const n = page?.pagination?.total;
  return typeof n === 'number' && Number.isFinite(n) && n >= 0 ? n : undefined;
};
const findRecords = (page: ModelingPageResult | undefined): ModelingModelRecord[] =>
  Array.isArray(page?.bizData) ? page.bizData : [];
const textCount = (count?: number): string => count == null ? '—' : String(count);
const target = (model: ModelingModelRecord): string =>
  model.id == null ? '/modeling?view=physical' : '/modeling/models/' + encodeURIComponent(String(model.id));
const filteredLayer = (code: string): string =>
  '/modeling?view=physical&layer=' + encodeURIComponent(code);

/** Read-only, project-scoped overview. Never infer project totals from a catalog page. */
export default function ModelingOverviewDashboard() {
  const { currentProject } = useSecurityProject();
  const { can } = usePermissionAccess();
  const projectId = currentProject?.id;
  const canReadSemantic = can('semantic:read');
  const epoch = useRef(0);
  const [data, setData] = useState<OverviewData>();
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const request = ++epoch.current;
    setLoading(true);
    if (projectId == null) {
      setData(undefined);
      setLoading(false);
      return;
    }
    const get = async <T,>(promise: Promise<T>): Promise<T | undefined> =>
      promise.catch(() => undefined);
    const [physical, drafts, logical, processes, configured] = await Promise.all([
      get(pageModelingModels({ pageNo: 1, pageSize: 5 })),
      get(pageModelingModels({ pageNo: 1, pageSize: 5, status: 'DRAFT' })),
      get(listLogicalDrafts()),
      canReadSemantic ? get(pageSemanticProcesses({ pageNo: 1, pageSize: 1 })) : Promise.resolve(undefined),
      canReadSemantic ? get(listSemanticLayers()) : Promise.resolve(undefined),
    ]);
    if (request !== epoch.current) return;
    const available = Array.isArray(configured)
      ? configured.filter(item => item.status === 'ENABLED').map(item => item.code)
      : [];
    const layerTotals = await Promise.all(LAYERS.map(async layer => {
      if (!Array.isArray(configured)) return { code: layer.code } as LayerStats;
      if (!available.includes(layer.code)) return { code: layer.code, configured: false } as LayerStats;
      const page = await get(pageModelingModels({ pageNo: 1, pageSize: 1, layerCode: layer.code }));
      return { code: layer.code, configured: true, count: countOf(page) } as LayerStats;
    }));
    if (request !== epoch.current) return;
    setData({
      projectId,
      physicalCount: countOf(physical),
      processCount: canReadSemantic && processes ? processes.pagination?.total : undefined,
      logicalCount: Array.isArray(logical) ? logical.length : undefined,
      draftCount: countOf(drafts),
      recent: findRecords(physical),
      drafts: findRecords(drafts),
      layerStats: layerTotals,
      partialFailure: !physical || !drafts || !logical || (canReadSemantic && (!processes || !configured))
        || layerTotals.some(item => item.configured && item.count == null),
    });
    setLoading(false);
  }, [projectId, canReadSemantic]);

  useEffect(() => {
    void refresh();
    return () => { epoch.current += 1; };
  }, [refresh]);

  // Hide all previous-project evidence immediately; never flash another project's counts.
  const snapshot = data?.projectId === projectId ? data : undefined;
  const cards = [
    { label: '业务过程', value: canReadSemantic ? snapshot?.processCount : undefined,
      note: canReadSemantic ? '已定义的业务过程' : '当前无语义读取权限',
      url: '/semantic/processes', icon: Workflow, allowed: canReadSemantic },
    { label: '逻辑模型', value: snapshot?.logicalCount, note: '当前项目逻辑草稿',
      url: '/modeling/logical', icon: GitBranch, allowed: true },
    { label: '物理模型', value: snapshot?.physicalCount, note: '全部数仓分层',
      url: '/modeling?view=physical', icon: Boxes, allowed: true },
    { label: '待继续设计', value: snapshot?.draftCount, note: '物理模型草稿',
      url: '/modeling?view=physical&status=DRAFT', icon: Layers3, allowed: true },
  ];

  return (
    <div className="modeling-studio-page modeling-overview">
      <div className="modeling-studio-catalog-body">
        <ModelingStudioHeader area="catalog" title="模型总览"
          eyebrow={null}
          description="查看当前项目的建模进展，接续未完成的设计。模型查询、筛选和创建请进入物理模型。"
          actions={<Button icon={<RefreshCw size={14} />} onClick={() => void refresh()} loading={loading}>刷新概况</Button>} />
        {projectId == null ? (
          <Alert showIcon type="info" message="请先选择项目" description="选择项目后查看该项目的模型建设情况。" />
        ) : (
          <>
            {snapshot?.partialFailure ? (
              <Alert className="modeling-overview-alert" showIcon type="warning"
                message="部分建模概况暂不可读取"
                description="无法确认的数据用「—」表示，不能当作零。你仍可进入对应工作台继续操作。" />
            ) : null}
            <section className="modeling-overview-metrics" aria-label="建模概况">
              {cards.map(item => (
                <button key={item.label} type="button" className="modeling-overview-stat"
                  disabled={!item.allowed} onClick={() => history.push(item.url)}>
                  <span className="modeling-overview-stat-label"><item.icon size={16} aria-hidden />{item.label}</span>
                  <strong>{loading || !snapshot ? <Skeleton.Input active size="small" style={{ width: 60 }} /> : textCount(item.value)}</strong>
                  <span>{item.note}</span>
                  <ArrowRight size={15} className="modeling-overview-stat-arrow" aria-hidden />
                </button>
              ))}
            </section>
            <div className="modeling-overview-work-grid">
              <section className="modeling-overview-panel" aria-label="待继续的物理模型草稿">
                <div className="modeling-overview-panel-title">
                  <div><h2>继续上次的建模工作</h2><p>仅列出当前项目最近更新的物理模型草稿</p></div>
                  <Button type="link" onClick={() => history.push('/modeling?view=physical&status=DRAFT')}>
                    查看全部草稿 <ArrowRight size={14} aria-hidden />
                  </Button>
                </div>
                {loading || !snapshot ? <Skeleton active paragraph={{ rows: 4 }} /> :
                  snapshot.draftCount == null ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="草稿数据暂不可读取" /> :
                  snapshot.drafts.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                    description="当前没有未完成的物理模型草稿" /> :
                  <div className="modeling-overview-work-list">
                    {snapshot.drafts.map(item => (
                      <button type="button" className="modeling-overview-work-item"
                        key={String(item.id)} onClick={() => history.push(target(item))}>
                        <span className="modeling-overview-item-main"><strong>{item.name || item.code || '未命名模型'}</strong>
                          <small>{item.code || '—'} · {item.layerCode || '未分层'}</small></span>
                        <span className="modeling-overview-item-time">{formatModelingTimeMinute(item.updateTime)}</span>
                        <ArrowRight size={15} aria-hidden />
                      </button>
                    ))}
                  </div>}
              </section>
              <section className="modeling-overview-panel" aria-label="数仓分层建设分布">
                <div className="modeling-overview-panel-title">
                  <div><h2>数仓分层建设</h2><p>按已配置分层查询真实物理模型数量</p></div>
                </div>
                <div className="modeling-overview-layer-list">
                  {LAYERS.map(layer => {
                    const item = snapshot?.layerStats.find(x => x.code === layer.code);
                    const enabled = Boolean(item?.configured);
                    return <button key={layer.code} type="button" className="modeling-overview-layer"
                      disabled={loading || !snapshot || !enabled}
                      onClick={() => history.push(filteredLayer(layer.code))}>
                      <span className="modeling-overview-layer-key">{layer.code}</span>
                      <span className="modeling-overview-layer-name">{layer.name}</span>
                      <span className="modeling-overview-layer-count">{loading || !snapshot ? '—'
                        : item?.configured === false ? '未配置' : !enabled ? '—' : textCount(item?.count)}</span>
                      <ArrowRight size={14} aria-hidden />
                    </button>;
                  })}
                </div>
              </section>
            </div>
            <section className="modeling-overview-panel" aria-label="最近更新的物理模型">
              <div className="modeling-overview-panel-title">
                <div><h2>最近更新</h2><p>最近变更的物理模型，最多显示 5 条</p></div>
                <Button type="link" onClick={() => history.push('/modeling?view=physical')}>
                  进入物理模型 <ArrowRight size={14} aria-hidden />
                </Button>
              </div>
              {loading || !snapshot ? <Skeleton active paragraph={{ rows: 2 }} /> :
                snapshot.physicalCount == null ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="最近模型暂不可读取" /> :
                snapshot.recent.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="当前项目尚无物理模型" /> :
                <div className="modeling-overview-recent-list">
                  {snapshot.recent.map(item => (
                    <button type="button" className="modeling-overview-recent-item" key={String(item.id)}
                      onClick={() => history.push(target(item))}>
                      <span className="modeling-overview-recent-name">{item.name || item.code || '未命名模型'}</span>
                      <span className="modeling-overview-recent-code">{item.code || '—'}</span>
                      <Tag>{item.layerCode || '未分层'}</Tag>
                      <span>{MODELING_STATUS_LABELS[item.status || ''] || item.status || '—'}</span>
                      <time>{formatModelingTimeMinute(item.updateTime)}</time>
                      <ArrowRight size={14} aria-hidden />
                    </button>
                  ))}
                </div>}
            </section>
            <p className="modeling-overview-footnote">
              本页只展示模型定义与草稿进度，不代表物理表已经部署、发布审批已通过或数据已同步。
            </p>
          </>
        )}
      </div>
    </div>
  );
}
