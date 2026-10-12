import { Alert, Button, Card, Space, Tag, Tooltip } from 'antd';
import { AssetWorkspaceHeading, ASSET_SURFACE } from '../components/AssetWorkspace';
import { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';
import { useSecurityProject } from '@/contexts/SecurityProjectContext';
import { usePermissionAccess } from '@/hooks/usePermissionAccess';
import { classifyAssetListFailure, type AssetListReadFailure } from '../read-state';
import { useLatestAssetListRequest } from '../useLatestAssetListRequest';

import { YakButton, YakEmpty } from '@/components/ui';
import { getAssetOverview } from '@/services/data-asset/api';
import type { AssetOverviewData, DistributionRow } from '@/services/data-asset/types';
import {
  ASSET_SOURCE_TYPE_LABELS,
  ASSET_STATUS_BAR_COLORS,
  distributionLabel,
  formatAssetTime,
  healthGradeColor,
} from '../constants';

const KPI_CARDS: {
  key: keyof AssetOverviewData['kpis'];
  label: string;
  hint?: string;
  percent?: boolean;
  color?: string;
}[] = [
  { key: 'total', label: '台账总量' },
  { key: 'published', label: '已上架', color: '#52c41a' },
  { key: 'pending', label: '待上架', color: '#fa8c16' },
  { key: 'added30d', label: '近 30 天新增' },
  { key: 'ownerCoverage', label: '负责人覆盖率', percent: true, hint: '有负责人的资产占比' },
  { key: 'classifiedRate', label: '安全定级率', percent: true, hint: '已有安全等级快照的资产占比' },
  { key: 'gradeACount', label: 'A 级资产', color: '#52c41a' },
  { key: 'gradeDCount', label: 'D 级资产', color: '#f5222d' },
];

const TODO_ITEMS: {
  key: keyof AssetOverviewData['todos'];
  label: string;
  to: string;
}[] = [
  { key: 'pendingPublish', label: '资产待上架', to: '/data-asset/inventory?tab=publish' },
  { key: 'openChanges', label: '变更待确认', to: '/data-asset/inventory?tab=changes' },
  { key: 'noOwner', label: '无负责人资产', to: '/data-asset/catalog' },
  { key: 'gradeD', label: 'D 级低健康资产', to: '/data-asset/catalog?grades=D' },
  { key: 'sourceGone', label: '源已消失资产', to: '/data-asset/catalog?statuses=SOURCE_GONE' },
];

const DistributionCard = ({
  title,
  group,
  rows,
}: {
  title: string;
  group: 'status' | 'grade' | 'type' | 'layer';
  rows: DistributionRow[];
}) => {
  const max = Math.max(1, ...rows.map((row) => row.c));
  const barColor = (key: string) => {
    if (group === 'grade') {
      return healthGradeColor(key);
    }
    if (group === 'status') {
      return ASSET_STATUS_BAR_COLORS[key as keyof typeof ASSET_STATUS_BAR_COLORS] ?? '#FE2C55';
    }
    return '#718caf';
  };
  return (
    <Card title={title} size="small" className="!mb-0 !rounded-xl !border-[#e8ecf2] !shadow-none">
      {rows.length === 0 ? (
        <YakEmpty compact title="暂无数据" description="对账产出资产后自动聚合" />
      ) : (
        <Space direction="vertical" className="w-full" size={6}>
          {rows.slice(0, 8).map((row) => (
            <div key={row.k} className="flex items-center gap-2 text-[12px]">
              <span className="w-[90px] shrink-0 truncate text-[#667085]">
                {distributionLabel(group, row.k)}
              </span>
              <div className="h-[10px] flex-1 overflow-hidden rounded-full bg-[#f2f4f7]">
                <div
                  className="h-full rounded-full"
                  style={{
                    width: `${Math.max(4, (row.c / max) * 100)}%`,
                    background: barColor(row.k),
                  }}
                />
              </div>
              <span className="w-[46px] shrink-0 text-right font-medium">{row.c}</span>
            </div>
          ))}
        </Space>
      )}
    </Card>
  );
};

const RecentList = ({ title, rows }: { title: string; rows: AssetOverviewData['recentListed'] }) => (
  <Card title={title} size="small" className="!mb-0 !rounded-xl !border-[#e8ecf2] !shadow-none">
    {rows.length === 0 ? (
      <YakEmpty compact title="暂无动态" />
    ) : (
      <Space direction="vertical" className="w-full" size={2}>
        {rows.map((row) => (
          <button
            key={`${row.assetId}-${row.at ?? ''}`}
            className="flex w-full cursor-pointer items-center justify-between gap-2 rounded-lg border-0 bg-transparent px-3 py-2.5 text-left transition hover:bg-[#f4f7fb] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[#89a6ce]"
            type="button"
            onClick={() => history.push(`/data-asset/detail/${row.assetId}`)}
          >
            <span className="min-w-0 truncate text-[13px]">{row.name}</span>
            <Space size={6} className="shrink-0 text-[12px] text-[#98a2b3]">
              {row.assetType && <Tag>{ASSET_SOURCE_TYPE_LABELS[row.assetType as keyof typeof ASSET_SOURCE_TYPE_LABELS] ?? row.assetType}</Tag>}
              <span>{formatAssetTime(row.at)}</span>
            </Space>
          </button>
        ))}
      </Space>
    )}
  </Card>
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

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <main className={ASSET_SURFACE}>
      <AssetWorkspaceHeading title="资产概览"
        description={<>查看资产台账、待处理治理事项与发布状态。{data?.generatedAt ? ` 数据更新于 ${formatAssetTime(data.generatedAt)}` : ''}</>}
        actions={<Space wrap>
          <YakButton onClick={load} loading={loading}>刷新</YakButton>
          <YakButton type="primary" className="!text-white" onClick={() => history.push('/data-asset/catalog')}>进入资产目录</YakButton>
        </Space>}
      />

      {loadError && (
        <Alert className="!mt-4" type={loadError === 'FORBIDDEN' ? 'warning' : 'error'} showIcon
          message={loadError === 'FORBIDDEN' ? '无权读取资产概览' : '资产概览读取失败'}
          description="无法确认当前项目的资产总量与治理待办；失败不代表没有资产或待办。"
          action={<YakButton size="small" onClick={() => void load()}>重试</YakButton>} />
      )}
      {data ? (
        <div className="mt-6 space-y-5">
          <section aria-label="核心资产指标">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {KPI_CARDS.slice(0, 4).map((item) => {
                const raw = Number(data.kpis[item.key] ?? 0);
                return (
                  <Card key={item.key} size="small" className="!rounded-2xl !border-[#e6eaf0] !shadow-[0_4px_20px_rgba(15,23,42,0.025)]">
                    <Tooltip title={item.hint}>
                      <div className="text-[13px] font-medium text-[#718095]">{item.label}</div>
                    </Tooltip>
                    <div className="mt-3 text-[31px] font-semibold leading-10 tabular-nums tracking-tight text-[#27374c]">
                      {raw.toLocaleString()}
                    </div>
                    <div className="mt-2 text-[12px] text-[#93a0b0]">
                      {item.key === 'total' ? '当前项目已纳入台账' :
                        item.key === 'published' ? '已通过人工上架流程' :
                        item.key === 'pending' ? '需要治理与发布预检' : '过去 30 天新增'}
                    </div>
                  </Card>
                );
              })}
            </div>
          </section>

          <section className="rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5 shadow-[0_4px_20px_rgba(15,23,42,0.025)]">
            <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="m-0 text-[16px] font-semibold text-[#26354a]">治理待办</h2>
                <p className="mb-0 mt-1 text-[12px] text-[#8794a6]">从这里直接进入需要处理的资产队列，不在概览中执行不可撤销的治理动作。</p>
              </div>
              <span className="text-[12px] text-[#9aa5b3]">当前项目 · 待处理</span>
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5">
              {TODO_ITEMS.map((item) => {
                const count = Number(data.todos[item.key] ?? 0);
                const urgent = count > 0 && (item.key === 'gradeD' || item.key === 'sourceGone');
                return (
                  <Button key={item.key} size="small"
                    danger={urgent} type="default"
                    onClick={() => history.push(item.to)}
                    className="!flex !h-auto !min-h-[84px] !flex-col !items-start !justify-center !gap-2 !rounded-xl !border-[#e8edf4] !px-4 !py-3 !text-left hover:!border-[#c9d7e8]">
                    <span className="text-[12px] font-medium text-[#738297]">{item.label} {count}</span>
                    <span className={'text-[23px] font-semibold tabular-nums ' + (urgent ? 'text-[#bd5c47]' : 'text-[#29394e]')}>{count.toLocaleString()}</span>
                  </Button>
                );
              })}
            </div>
          </section>

          <div className="grid grid-cols-1 gap-5 xl:grid-cols-[1.6fr_1fr]">
            <section className="min-w-0 rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5 shadow-[0_4px_20px_rgba(15,23,42,0.025)]">
              <h2 className="m-0 text-[16px] font-semibold text-[#26354a]">资产构成</h2>
              <p className="mb-4 mt-1 text-[12px] text-[#8794a6]">按生命周期状态、健康等级及资产类型查看台账分布。</p>
              <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
                <DistributionCard title="按状态" group="status" rows={data.distributions.status} />
                <DistributionCard title="按健康等级" group="grade" rows={data.distributions.grade} />
                <DistributionCard title="按资产类型" group="type" rows={data.distributions.type} />
                <DistributionCard title="按数仓分层" group="layer" rows={data.distributions.layer} />
              </div>
            </section>
            <section className="min-w-0 rounded-2xl border border-solid border-[#e6eaf0] bg-white p-5 shadow-[0_4px_20px_rgba(15,23,42,0.025)]">
              <h2 className="m-0 text-[16px] font-semibold text-[#26354a]">治理基础覆盖</h2>
              <p className="mb-4 mt-1 text-[12px] text-[#8794a6]">衡量资产治理资料的完备程度，不直接代表资产已经通过发布审批。</p>
              <div className="grid grid-cols-2 gap-3">
                {KPI_CARDS.slice(4).map((item) => {
                  const value = Number(data.kpis[item.key] ?? 0);
                  const display = item.percent ? `${Math.round(value * 1000) / 10}%` : value.toLocaleString();
                  return (
                    <div key={item.key} className="rounded-xl border border-solid border-[#edf0f4] bg-[#f8fafc] p-4">
                      <Tooltip title={item.hint}><div className="text-[12px] text-[#718095]">{item.label}</div></Tooltip>
                      <div className="mt-2 text-[24px] font-semibold leading-8 tabular-nums text-[#2b3b50]">{display}</div>
                    </div>
                  );
                })}
              </div>
              <div className="mt-4 rounded-xl bg-[#f4f7fb] px-4 py-3 text-[12px] leading-5 text-[#8190a3]">
                缺少负责人、安全等级或健康度较低的资产，建议进入目录按状态筛选后逐项治理。
              </div>
            </section>
          </div>

          <section>
            <div className="mb-3">
              <h2 className="m-0 text-[16px] font-semibold text-[#26354a]">最近资产动态</h2>
              <p className="mb-0 mt-1 text-[12px] text-[#8794a6]">历史上架、下架记录与当前状态分开呈现。</p>
            </div>
            <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
              <RecentList title="最近上架" rows={data.recentListed} />
              <RecentList title="最近下架" rows={data.recentOffline} />
            </div>
          </section>
        </div>
      ) : loading ? (
        <Card className="!mt-4" loading />
      ) : null}
    </main>
  );
};

/** A Project or permission change must discard metrics, pending responses and old actions. */
export default function ScopedAssetOverviewPage() {
  const { currentProject } = useSecurityProject();
  const { permissionCodes } = usePermissionAccess();
  return <AssetOverviewPage key={JSON.stringify([currentProject?.id, permissionCodes])} />;
}
