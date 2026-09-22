import HttpUtils from '@/utils/HttpUtils';

import type { DashboardOverview, DashboardSummary } from './types';

const DASHBOARD_OVERVIEW_API = '/api/v1/dashboards/overview';

interface DashboardAssetWire {
  id: string;
  name: string;
  description?: string | null;
  currentVersionId?: string | null;
  currentVersionNo: number;
  publishedVersionId?: string | null;
  publishedVersionNo?: number;
  publishedTime?: string | null;
  createTime?: string;
  updateTime?: string;
}

interface DashboardOverviewWire {
  dashboardCount: number;
  publishedDashboardCount: number;
  recentDashboards?: DashboardAssetWire[];
}

const toDashboardSummary = (value: DashboardAssetWire): DashboardSummary => ({
  id: String(value.id),
  name: value.name,
  description: value.description || '',
  currentVersionId: value.currentVersionId
    ? String(value.currentVersionId)
    : undefined,
  currentVersionNo: value.currentVersionNo || 0,
  publishedVersionId: value.publishedVersionId
    ? String(value.publishedVersionId)
    : undefined,
  publishedVersionNo: value.publishedVersionNo || 0,
  publishedTime: value.publishedTime || undefined,
  createTime: value.createTime,
  updateTime: value.updateTime,
});

export const getDashboardOverview = async (
  limit = 4,
): Promise<DashboardOverview> => {
  const size = Math.max(1, Math.min(20, Math.trunc(limit)));
  const value = await HttpUtils.getData<DashboardOverviewWire>(
    `${DASHBOARD_OVERVIEW_API}?limit=${size}`,
  );
  return {
    dashboardCount: value.dashboardCount ?? 0,
    publishedDashboardCount: value.publishedDashboardCount ?? 0,
    recentDashboards: (value.recentDashboards || []).map(toDashboardSummary),
  };
};

export const fetchDashboardOverview = getDashboardOverview;
