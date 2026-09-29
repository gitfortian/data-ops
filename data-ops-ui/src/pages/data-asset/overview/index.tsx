import { Button, Card, Space, Tag, Tooltip } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { history } from '@umijs/max';

import { YakButton, YakEmpty } from '@/components/ui';
import { getAssetOverview } from '@/services/data-asset/api';
import type { AssetOverviewData, DistributionRow } from '@/services/data-asset/types';
import {
  distributionLabel,
  formatAssetTime,
  healthGradeColor,
  ASSET_SOURCE_TYPE_LABELS,
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
  return (
    <Card title={title} size="small" className="!mb-4">
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
                    background: group === 'grade' ? healthGradeColor(row.k) : '#FE2C55',
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
  <Card title={title} size="small" className="!mb-4">
    {rows.length === 0 ? (
      <YakEmpty compact title="暂无动态" />
    ) : (
      <Space direction="vertical" className="w-full" size={2}>
        {rows.map((row) => (
          <div
            key={`${row.assetId}-${row.at ?? ''}`}
            className="flex cursor-pointer items-center justify-between gap-2 rounded px-1 py-1.5 hover:bg-[#f8f9fa]"
            onClick={() => history.push(`/data-asset/detail/${row.assetId}`)}
          >
            <span className="min-w-0 truncate text-[13px]">{row.name}</span>
            <Space size={6} className="shrink-0 text-[12px] text-[#98a2b3]">
              {row.assetType && <Tag>{ASSET_SOURCE_TYPE_LABELS[row.assetType as keyof typeof ASSET_SOURCE_TYPE_LABELS] ?? row.assetType}</Tag>}
              <span>{formatAssetTime(row.at)}</span>
            </Space>
          </div>
        ))}
      </Space>
    )}
  </Card>
);

const AssetOverviewPage = () => {
  const [data, setData] = useState<AssetOverviewData | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getAssetOverview());
    } catch {
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="min-h-[calc(100dvh-64px)] bg-white px-6 pb-6 pt-5 text-[#242731] max-md:px-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="text-[20px] font-semibold leading-7">资产概览</div>
          <div className="mt-1 text-[13px] text-[#667085]">
            治理驾驶舱：KPI + 分布 + 待办 + 最近动态，服务端固定 ≤8 次查询聚合{data?.generatedAt ? `，生成于 ${formatAssetTime(data.generatedAt)}` : ''}
          </div>
        </div>
        <Space>
          <YakButton onClick={load} loading={loading}>
            刷新
          </YakButton>
          <YakButton type="primary" className="!text-white" onClick={() => history.push('/data-asset/catalog')}>
            进入资产目录
          </YakButton>
        </Space>
      </div>

      <div className="mt-4 grid grid-cols-4 gap-3 max-lg:grid-cols-2 max-md:grid-cols-1">
        {KPI_CARDS.map((item) => {
          const raw = data ? Number(data.kpis[item.key] ?? 0) : 0;
          const display = item.percent ? `${Math.round(raw * 1000) / 10}%` : raw;
          return (
            <Card key={item.key} size="small" loading={loading}>
              <Tooltip title={item.hint}>
                <div className="text-[12px] text-[#667085]">{item.label}</div>
              </Tooltip>
              <div className="mt-1 text-[24px] font-semibold leading-8" style={{ color: item.color }}>
                {data ? display : '-'}
              </div>
            </Card>
          );
        })}
      </div>

      <Card title="治理待办" size="small" className="!my-4" loading={loading}>
        <Space size={8} wrap>
          {TODO_ITEMS.map((item) => {
            const count = data ? Number(data.todos[item.key] ?? 0) : 0;
            return (
              <Button
                key={item.key}
                size="small"
                danger={count > 0}
                type={count > 0 ? 'primary' : 'default'}
                onClick={() => history.push(item.to)}
              >
                {item.label} {count}
              </Button>
            );
          })}
        </Space>
      </Card>

      <div className="grid grid-cols-2 gap-4 max-md:grid-cols-1">
        <div>
          <DistributionCard title="按状态分布" group="status" rows={data?.distributions.status ?? []} />
          <DistributionCard title="按健康度分布" group="grade" rows={data?.distributions.grade ?? []} />
        </div>
        <div>
          <DistributionCard title="按类型分布" group="type" rows={data?.distributions.type ?? []} />
          <DistributionCard title="按分层分布" group="layer" rows={data?.distributions.layer ?? []} />
        </div>
      </div>

      <div className="mt-0 grid grid-cols-2 gap-4 max-md:grid-cols-1">
        <RecentList title="最近上架" rows={data?.recentListed ?? []} />
        <RecentList title="最近下架" rows={data?.recentOffline ?? []} />
      </div>
    </div>
  );
};

export default AssetOverviewPage;
