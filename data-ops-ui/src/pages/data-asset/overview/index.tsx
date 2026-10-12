import { Alert, Button, Tag } from 'antd';
import { ArrowRight, ClipboardCheck, Clock3, Database, Layers3, RefreshCw, ShieldCheck, TriangleAlert } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { classifyAssetListFailure, type AssetListReadFailure } from '../read-state';
import { useLatestAssetListRequest } from '../useLatestAssetListRequest';
import { YakEmpty } from '@/components/ui';
import { getAssetOverview } from '@/services/data-asset/api';
import type { AssetOverviewData, DistributionRow } from '@/services/data-asset/types';
import { ASSET_SOURCE_TYPE_LABELS, ASSET_TYPE_LABELS, ASSET_STATUS_BAR_COLORS, distributionLabel, formatAssetTime, healthGradeColor } from '../constants';
import { AssetMetric, AssetPageHeader, AssetPanel, AssetWorkspace } from '../asset-ui';

const KPI_CARDS: { key: keyof AssetOverviewData['kpis']; label: string; note: string; percent?: boolean; type?: 'warning' | 'success' }[] = [
  { key: 'total', label: '台账总量', note: '当前项目可见的资产总数' },
  { key: 'published', label: '已上架', note: '已进入资产消费目录', type: 'success' },
  { key: 'pending', label: '待上架', note: '需要治理后才能正式发布', type: 'warning' },
  { key: 'added30d', label: '近 30 天新增', note: '新纳入资产台账的对象' },
  { key: 'ownerCoverage', label: '负责人覆盖率', note: '已明确治理联系人的资产比例', percent: true },
  { key: 'classifiedRate', label: '安全定级率', note: '已有安全等级快照的资产比例', percent: true },
  { key: 'gradeACount', label: 'A 级资产', note: '健康度评级 A 的资产', type: 'success' },
  { key: 'gradeDCount', label: 'D 级资产', note: '健康度评级 D 的资产', type: 'warning' },
];

const TODO_ITEMS: { key: keyof AssetOverviewData['todos']; label: string; description: string; to: string; priority: 'warning' | 'normal' }[] = [
  { key: 'pendingPublish', label: '资产待上架', description: '完善负责人、目录、描述后进入预检', to: '/data-asset/inventory?tab=publish', priority: 'normal' },
  { key: 'openChanges', label: '变更待确认', description: '比较源域变化，决定更新或忽略', to: '/data-asset/inventory?tab=changes', priority: 'warning' },
  { key: 'noOwner', label: '无负责人资产', description: '责任人未配置，无法有效治理', to: '/data-asset/catalog', priority: 'normal' },
  { key: 'gradeD', label: 'D 级低健康资产', description: '检查治理缺口及健康指标', to: '/data-asset/catalog?grades=D', priority: 'warning' },
  { key: 'sourceGone', label: '源已消失资产', description: '核对来源证据，避免误删除', to: '/data-asset/catalog?statuses=SOURCE_GONE', priority: 'warning' },
];

const DistributionCard = ({ title, group, rows }: {
  title: string; group: 'status' | 'grade' | 'type' | 'layer'; rows: DistributionRow[];
}) => {
  const max = Math.max(1, ...rows.map((row) => row.c));
  const barColor = (key: string) => {
    if (group === 'grade') return healthGradeColor(key);
    if (group === 'status') return ASSET_STATUS_BAR_COLORS[key as keyof typeof ASSET_STATUS_BAR_COLORS] ?? '#6988b0';
    return '#7894b6';
  };
  return (
    <AssetPanel title={title} description="当前项目资产台账分布 · 最多显示 8 项" bodyClassName="px-5 pb-5 pt-4">
      {rows.length === 0 ? <YakEmpty compact title="暂无数据" description="当前没有可统计的目录资产" /> : (
        <div className="space-y-3">
          {rows.slice(0, 8).map((row) => (
            <div key={row.k} className="grid grid-cols-[100px_minmax(0,1fr)_52px] items-center gap-3">
              <span className="truncate text-[12px] text-[#64748a]" title={distributionLabel(group, row.k)}>{distributionLabel(group, row.k)}</span>
              <div className="h-[7px] overflow-hidden rounded-full bg-[#f0f3f7]" role="img"
                aria-label={distributionLabel(group, row.k) + '：' + row.c + ' 个'}>
                <div className="h-full rounded-full" style={{ width: Math.max(3, row.c / max * 100) + '%', backgroundColor: barColor(row.k) }} />
              </div>
              <span className="text-right text-[12px] font-medium tabular-nums text-[#34455e]">{row.c.toLocaleString()}</span>
            </div>
          ))}
        </div>
      )}
    </AssetPanel>
  );
};

const RecentList = ({ title, rows }: { title: string; rows: AssetOverviewData['recentListed'] }) => (
  <AssetPanel title={title} description="点击资产查看当前台账及来源证据" bodyClassName="px-2 py-2">
    {rows.length === 0 ? <YakEmpty compact title="暂无动态" description="暂无可核实的上架或下架记录" /> : (
      <div className="divide-y divide-[#edf0f4]">
        {rows.map((row) => (
          <button type="button" key={row.assetId + ':' + (row.at || '')}
            onClick={() => history.push('/data-asset/detail/' + row.assetId)}
            className="flex w-full items-center justify-between gap-3 border-0 bg-white px-4 py-3 text-left transition-colors hover:bg-[#f7f9fc] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#7894b6]">
            <div className="min-w-0">
              <div className="truncate text-[13px] font-medium text-[#2d3d53]">{row.name}</div>
              <div className="mt-1 text-[11px] text-[#8e9baf]">{row.assetType ? ASSET_TYPE_LABELS[row.assetType as keyof typeof ASSET_TYPE_LABELS] ?? ASSET_SOURCE_TYPE_LABELS[row.assetType as keyof typeof ASSET_SOURCE_TYPE_LABELS] ?? row.assetType : '资产'} · {formatAssetTime(row.at)}</div>
            </div>
            <ArrowRight size={15} className="shrink-0 text-[#9aabba]" />
          </button>
        ))}
      </div>
    )}
  </AssetPanel>
);

const AssetOverviewPage = () => {
  const [data, setData] = useState<AssetOverviewData | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<AssetListReadFailure | null>(null);
  const beginLatestRequest = useLatestAssetListRequest();

  const load = useCallback(async () => {
    const isCurrent = beginLatestRequest();
    setLoading(true);
    setData(null);
    setLoadError(null);
    try {
      const result = await getAssetOverview();
      if (isCurrent()) setData(result);
    } catch (error) {
      if (isCurrent()) setLoadError(classifyAssetListFailure(error));
    } finally {
      if (isCurrent()) setLoading(false);
    }
  }, [beginLatestRequest]);

  useEffect(() => { void load(); }, [load]);

  const metricIcon = (key: string) => key === 'total' ? <Database size={18}/> : key === 'pending' || key === 'gradeDCount'
    ? <TriangleAlert size={18}/> : key === 'ownerCoverage' || key === 'classifiedRate'
      ? <ShieldCheck size={18}/> : key === 'added30d' ? <Clock3 size={18}/> : <Layers3 size={18}/>;

  return (
    <AssetWorkspace>
      <AssetPageHeader
        title="资产工作台"
        description={<>基于真实资产台账识别治理缺口、检查发布状态，并快速进入对应处理流程。
          {data?.generatedAt ? <span className="ml-2 text-[#9ba6b6]">数据更新时间：{formatAssetTime(data.generatedAt)}</span> : null}</>}
        extra={
          <>
            <Button onClick={() => void load()} disabled={loading}><RefreshCw size={14} className="mr-1 inline" />刷新</Button>
            <Button type="primary" onClick={() => history.push('/data-asset/catalog')}>进入资产目录 <ArrowRight size={14} className="ml-1 inline" /></Button>
          </>
        }
      />
      {loadError ? (
        <Alert className="!mt-5 !rounded-xl" type={loadError === 'FORBIDDEN' ? 'warning' : 'error'} showIcon
          message={loadError === 'FORBIDDEN' ? '无权读取资产概览' : '资产概览读取失败'}
          description="无法确认当前项目的资产总量与治理待办；失败不代表没有资产或待办。"
          action={<Button size="small" onClick={() => void load()}>重试</Button>} />
      ) : null}
      {data ? (
        <div className="mt-6 space-y-5">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
            {KPI_CARDS.map((item) => {
              const raw = Number(data.kpis[item.key] ?? 0);
              return (
                <AssetMetric key={item.key} label={item.label}
                  value={item.percent ? (Math.round(raw * 1000) / 10) + '%' : raw.toLocaleString()}
                  helper={item.note} icon={metricIcon(item.key)} emphasis={item.type || 'default'} />
              );
            })}
          </div>

          <AssetPanel title="需要处理的治理工作"
            description="这里显示待办数量；点击后会进入对应目录或处理台，不会自动执行变更。"
            action={<Button type="link" onClick={() => history.push('/data-asset/inventory?tab=publish')}>打开盘点上架 <ArrowRight size={14} className="ml-1 inline" /></Button>}
            bodyClassName="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 xl:grid-cols-5">
            {TODO_ITEMS.map((item) => {
              const count = Number(data.todos[item.key] ?? 0);
              return (
                <button type="button" key={item.key} onClick={() => history.push(item.to)}
                  className="min-w-0 rounded-xl border border-solid border-[#e8edf3] bg-[#fafbfd] p-4 text-left transition-colors hover:border-[#b8cbe1] hover:bg-[#f4f8fd] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#7894b6]">
                  <span className="block text-[12px] font-medium text-[#64748a]">{item.label} {count}</span>
                  <span className={'mt-2 block text-[24px] font-semibold leading-7 tabular-nums ' + (count > 0 && item.priority === 'warning' ? 'text-[#ad6333]' : 'text-[#29394f]')}>{count.toLocaleString()}</span>
                  <span className="mt-2 block text-[11px] leading-5 text-[#95a1b2]">{item.description}</span>
                </button>
              );
            })}
          </AssetPanel>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            <DistributionCard title="资产生命周期" group="status" rows={data.distributions.status} />
            <DistributionCard title="资产健康分布" group="grade" rows={data.distributions.grade} />
            <DistributionCard title="资产类型构成" group="type" rows={data.distributions.type} />
            <DistributionCard title="数仓分层构成" group="layer" rows={data.distributions.layer} />
          </div>
          <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            <RecentList title="最近上架" rows={data.recentListed} />
            <RecentList title="最近下架" rows={data.recentOffline} />
          </div>
          <div className="flex items-start gap-2 rounded-xl border border-solid border-[#e5ebf2] bg-[#f0f4f9] px-4 py-3 text-[12px] leading-5 text-[#738399]">
            <ClipboardCheck size={15} className="mt-0.5 shrink-0" />
            资产台账的治理状态独立于元数据采集运行。待上架不代表已发布，源对象变化也必须经过可核验的变更处理。
          </div>
        </div>
      ) : loading ? (
        <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4" aria-label="正在读取资产概览">
          {Array.from({ length: 8 }, (_, index) => (
            <div key={index} className="h-[142px] animate-pulse rounded-2xl border border-solid border-[#e7ebf1] bg-white p-5">
              <div className="h-4 w-2/3 rounded bg-[#edf1f5]" /><div className="mt-5 h-9 w-1/3 rounded bg-[#edf1f5]" />
            </div>
          ))}
        </div>
      ) : null}
    </AssetWorkspace>
  );
};

/** A Project or permission change must discard metrics, pending responses and old actions. */
export default function ScopedAssetOverviewPage() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <AssetOverviewPage key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}
