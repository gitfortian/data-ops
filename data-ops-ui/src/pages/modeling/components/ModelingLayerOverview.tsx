import { Button, Select, Skeleton, Tag, theme } from 'antd';
import type { CSSProperties } from 'react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { YakEmpty } from '@/components/ui';
import { MODELING_STATUS_LABELS } from '@/pages/modeling/constants';
import { pageModelingModels } from '@/services/modeling/api';
import type { ModelingMainlineCoverage, ModelingModelId, ModelingModelRecord } from '@/services/modeling/types';
import { getModelingMainline } from '@/services/modeling/view';

const LAYERS = [
  { code: 'ODS', name: '贴源接入', description: '确认授权来源、字段结构与来源映射。', next: '来源表 → 贴源模型' },
  { code: 'DWD', name: '明细事实', description: '明确业务粒度，设计事实字段和维度引用。', next: '逻辑设计 → 明细模型' },
  { code: 'DWS', name: '主题汇总', description: '核对统计周期、分组维度和度量聚合。', next: '明细模型 → 汇总模型' },
  { code: 'ADS', name: '应用结果', description: '面向应用设计结果结构，核对上游粒度。', next: '汇总 / 明细 → 应用模型' },
  { code: 'DIM', name: '公共维度', description: '管理维度业务键、属性和版本，为事实与汇总设计提供复用基础。', next: '业务语义 → 维度设计' },
] as const;
type LayerCode = typeof LAYERS[number]['code'];
interface LayerPage {
  models: ModelingModelRecord[];
  total: number;
  error: boolean;
}

interface Props {
  projectId?: number;
  configuredLayers: string[];
  configurationAvailable: boolean;
  domainId?: number;
  domainName?: string;
  processId?: number;
  processName?: string;
  processReady: boolean;
  canCreate: boolean;
  onSelectProcess: (id?: number) => void;
  onOpenLayer: (code: string) => void;
  onCreateLayer: (code: string) => void;
  onOpenModel: (id: ModelingModelId) => void;
  onOpenLogical: () => void;
  onOpenSource: () => void;
  onOpenMainline: () => void;
  onOpenLayerConfig: () => void;
}

function ModelRow({ model, onOpen }: { model: ModelingModelRecord; onOpen: Props['onOpenModel'] }) {
  return <button type="button" disabled={model.id == null}
    title={`${model.name || model.code || '未命名模型'} · ${model.code || ''}`}
    className="flex w-full items-center justify-between gap-2 rounded-lg border border-[#e6ebf0] bg-white px-3 py-2 text-left transition-colors hover:border-[var(--modeling-accent-border)] hover:bg-[var(--modeling-accent-bg)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--modeling-accent)]"
    onClick={() => { if (model.id != null) onOpen(model.id); }}>
    <span className="min-w-0">
      <span className="block truncate text-[13px] font-medium text-[#344054]">{model.name || model.code || '未命名模型'}</span>
      <span className="block truncate text-[11px] text-[#98a2b3]">{model.code}</span>
    </span>
    <Tag className="!m-0 shrink-0 !text-[11px]" color={model.status === 'PUBLISHED' ? 'green' : 'default'}>
      {MODELING_STATUS_LABELS[model.status || ''] || model.status || '状态未知'}
    </Tag>
  </button>;
}

/** Bounded model pages include unassigned models; process coverage is a separate Owner read. */
export default function ModelingLayerOverview({
  projectId, configuredLayers, configurationAvailable, domainId, domainName, processId, processName, processReady,
  canCreate, onSelectProcess, onOpenLayer, onCreateLayer, onOpenModel,
  onOpenLogical, onOpenSource, onOpenMainline, onOpenLayerConfig,
}: Props) {
  const { token } = theme.useToken();
  const themeVariables = { '--modeling-accent': token.colorPrimary, '--modeling-accent-bg': token.colorPrimaryBg,
    '--modeling-accent-border': token.colorPrimaryBorder } as CSSProperties;
  const [coverage, setCoverage] = useState<ModelingMainlineCoverage[]>([]);
  const [pages, setPages] = useState<Partial<Record<LayerCode, LayerPage>>>({});
  const [loading, setLoading] = useState(true);
  const [coverageError, setCoverageError] = useState(false);
  const requestEpoch = useRef(0);
  const loadOverview = useCallback(async () => {
    const epoch = ++requestEpoch.current;
    setLoading(true);
    setPages({});
    setCoverage([]);
    setCoverageError(false);
    const results = await Promise.allSettled([
      ...LAYERS.map(layer => pageModelingModels({
        pageNo: 1, pageSize: 2, layerCode: layer.code, processId, domainId,
      })),
      getModelingMainline(),
    ]);
    if (epoch !== requestEpoch.current) return;
    const next: Partial<Record<LayerCode, LayerPage>> = {};
    LAYERS.forEach((layer, index) => {
      const result = results[index];
      if (result.status === 'fulfilled' && result.value && !Array.isArray(result.value)
          && Number.isFinite(result.value.pagination?.total)) {
        next[layer.code] = { models: result.value.bizData ?? [], total: result.value.pagination?.total ?? 0, error: false };
      } else {
        next[layer.code] = { models: [], total: 0, error: true };
      }
    });
    const mainline = results[LAYERS.length];
    if (mainline.status === 'fulfilled' && Array.isArray(mainline.value)) setCoverage(mainline.value);
    else setCoverageError(true);
    setPages(next);
    setLoading(false);
  }, [processId, domainId]);

  useEffect(() => {
    setPages({});
    setCoverage([]);
    if (projectId !== undefined && processReady) void loadOverview();
    else setLoading(false);
    return () => { requestEpoch.current += 1; };
  }, [projectId, processReady, loadOverview]);

  const processOptions = useMemo(() => {
    const options = coverage.map(item => ({ value: item.processId, label: `${item.processName} · ${item.processCode}` }));
    if (processId && !options.some(item => item.value === processId)) {
      options.push({ value: processId, label: processName || `业务过程 #${processId}` });
    }
    return options;
  }, [coverage, processId, processName]);
  const scope = processName || domainName || '当前项目全部模型';

  const layerCard = (layer: typeof LAYERS[number], shared = false) => {
    const page = pages[layer.code];
    const configured = configuredLayers.some(code => code.toUpperCase() === layer.code);
    return <article key={layer.code} aria-label={`${layer.code} ${layer.name}`}
      className={`flex min-w-0 flex-col rounded-xl border border-[#e4e9f0] bg-white p-4 ${shared ? 'mt-3 sm:flex-row sm:items-start sm:gap-5' : ''}`}>
      <div className={shared ? 'sm:w-[240px] sm:shrink-0' : ''}>
        <div className="flex items-start justify-between gap-2">
          <div>
            <div className="text-[11px] font-semibold tracking-wide text-[var(--modeling-accent)]">{layer.code}{shared ? ' · 跨层复用基础' : ''}</div>
            <h3 className="mb-0 mt-1 text-[15px] font-semibold text-[#344054]">{layer.name}</h3>
          </div>
          <span aria-label={`${layer.code} 模型数`} className="rounded-md bg-[#f2f4f7] px-2 py-1 text-[12px] font-medium text-[#475467]">
            {page?.error || !page ? '—' : page.total}
          </span>
        </div>
        <p className="mb-3 mt-2 text-[12px] leading-[19px] text-[#667085]">{layer.description}</p>
      </div>
      <div className={`mb-3 flex min-h-[112px] flex-col gap-2 ${shared ? 'min-w-0 flex-1 sm:mb-0 sm:min-h-0' : ''}`}>
        {page?.error ? <div role="alert" className="rounded-lg border border-dashed border-[#f5d5d4] px-3 py-3 text-[12px] text-[#b42318]">此层模型暂时无法读取，请刷新重试</div>
          : (page?.models ?? []).map(model => <ModelRow key={model.id ?? model.code} model={model} onOpen={onOpenModel} />)}
        {page && !page.error && !page.total && <div className="rounded-lg border border-dashed border-[#dfe6ed] px-3 py-3 text-[12px] text-[#98a2b3]">
          {configured ? '当前范围暂无此层模型，可从新建设计开始'
            : configurationAvailable ? '当前项目尚未配置此分层，请先维护数仓分层' : '当前范围暂无模型，分层配置暂时无法读取'}
        </div>}
        {(page?.total ?? 0) > 2 && <span className="text-[11px] text-[#98a2b3]">展示前 2 个，查看模型可浏览全部</span>}
      </div>
      <div className={shared ? 'shrink-0 sm:w-[160px]' : 'mt-auto border-t border-[#f0f2f5] pt-3'}>
        <div className="mb-2 text-[11px] text-[#98a2b3]">{layer.next}</div>
        <div className="flex flex-wrap gap-x-3 gap-y-1">
          <Button type="link" size="small" className="!h-auto !p-0" onClick={() => onOpenLayer(layer.code)}>查看模型</Button>
          <Button type="link" size="small" className="!h-auto !p-0" disabled={!canCreate || !configured} onClick={() => onCreateLayer(layer.code)}>新建设计</Button>
          {!configured && configurationAvailable && <Button type="link" size="small" className="!h-auto !p-0" onClick={onOpenLayerConfig}>设置分层</Button>}
        </div>
      </div>
    </article>;
  };

  return <section aria-label="数仓分层全景" style={themeVariables} className="modeling-overview mb-4 rounded-xl border border-[#dfe6ed] bg-[#f8fafc] p-4 sm:p-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <div className="text-[16px] font-semibold text-[#242731]">从业务设计到分层落地</div>
        <p className="mb-0 mt-1 text-[12px] leading-5 text-[#667085]">ODS → DWD → DWS → ADS，公共维度跨层复用。各层模型分别设计、保存与核对版本。</p>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button size="small" onClick={onOpenSource}>从数据源开始</Button>
        <Button size="small" onClick={onOpenLogical}>从逻辑设计开始</Button>
      </div>
    </div>
    <div className="mt-4 flex flex-wrap items-center gap-2 border-t border-[#e6ebf0] pt-4">
      <span className="text-[12px] font-medium text-[#475467]">业务过程</span>
      <Select allowClear showSearch optionFilterProp="label" size="small" className="min-w-[240px] max-w-full"
        placeholder="全部业务过程" value={processReady ? processId : undefined}
        loading={loading || !processReady} disabled={coverageError || !processReady}
        options={processOptions} onChange={onSelectProcess} />
      <span className="text-[12px] text-[#98a2b3]">{scope} · 数量来自当前筛选范围</span>
      <Button size="small" type="link" disabled={projectId === undefined || !processReady || loading}
        onClick={() => void loadOverview()}>刷新全景</Button>
    </div>
    {projectId === undefined ? <YakEmpty compact title="请先选择项目空间" description="模型与分层配置按当前项目读取" />
      : loading || !processReady ? <div className="mt-4"><Skeleton active paragraph={{ rows: 5 }} /></div> : <>
        <div className="modeling-overview-grid mt-4 grid gap-4">{LAYERS.filter(layer => layer.code !== 'DIM').map(layer => layerCard(layer))}</div>
        {layerCard(LAYERS[4], true)}
        <div className="mt-4 rounded-lg border border-[#e6ebf0] bg-white p-3">
          <div className="mb-2 flex items-center justify-between gap-2 text-[12px] font-medium text-[#475467]">
            <span>项目业务过程覆盖</span><Button type="link" size="small" onClick={onOpenMainline}>打开覆盖矩阵</Button>
          </div>
          {coverageError ? <div role="alert" className="text-[12px] text-[#b42318]">业务过程覆盖暂时无法读取，各层模型仍可独立查看。</div>
            : coverage.length ? <div className="flex flex-wrap gap-2">
              {coverage.slice(0, 8).map(item => <Button key={item.processId} size="small" onClick={() => onSelectProcess(item.processId)}>
                {item.processName}<span className="ml-1 text-[#98a2b3]">{item.totalModels}</span>
              </Button>)}
              {coverage.length > 8 && <Button size="small" type="link" onClick={onOpenMainline}>查看全部过程</Button>}
            </div> : <p className="mb-0 text-[12px] text-[#98a2b3]">当前范围暂无业务过程覆盖。未关联过程的模型仍包含在上方各层中。</p>}
        </div>
        <p className="mb-0 mt-3 text-[11px] leading-5 text-[#98a2b3]">全景展示模型设计与过程归属，详情中核对真实来源、映射与版本；已发布模型不代表物理部署或数据运行完成。</p>
      </>}
  </section>;
}
