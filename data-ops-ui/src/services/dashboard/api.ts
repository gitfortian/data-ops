import type { AnalysisSpec, Scalar } from '@/components/analysis/model';
import HttpUtils from '@/utils/HttpUtils';

import { normalizeDashboardDocument } from './document';
import type {
  DashboardDocument,
  DashboardGlobalFilter,
  DashboardInteraction,
  DashboardServerDetail,
  DashboardSummary,
  DashboardTheme,
  DashboardVersionDetail,
  DashboardVersionSummary,
  DashboardWidget,
  FilterOperator,
} from './types';

const DASHBOARD_API = '/api/v1/dashboards';

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

interface DashboardVersionWire {
  id: string;
  dashboardId: string;
  versionNo: number;
  name: string;
  description?: string | null;
  activeDatasetId?: string | null;
  createTime?: string;
}

interface DashboardWidgetWire {
  id: string;
  dashboardVersionId: string;
  widgetKey: string;
  analysisId?: string | null;
  title?: string | null;
  inlineAnalysis?: AnalysisSpec | null;
  x: number;
  y: number;
  w: number;
  h: number;
  minW?: number | null;
  minH?: number | null;
  sortOrder: number;
}

interface DashboardGlobalFilterWire {
  filterKey: string;
  name: string;
  operator: 'EQ' | 'NE' | 'CONTAINS' | 'GT' | 'GTE' | 'LT' | 'LTE';
  defaultValue?: Scalar;
  bindings: Array<{
    widgetKey: string;
    fieldId: string;
    sortOrder: number;
  }>;
  sortOrder: number;
}

interface DashboardInteractionWire {
  interactionKey: string;
  event: 'SELECT';
  sourceWidgetKey: string;
  sourceFieldId: string;
  targetFilterKey: string;
  sortOrder: number;
}

interface DashboardDetailWire {
  dashboard: DashboardAssetWire;
  currentVersion?: DashboardVersionWire | null;
  theme?: DashboardTheme | null;
  versions: DashboardVersionWire[];
  widgets: DashboardWidgetWire[];
  globalFilters?: DashboardGlobalFilterWire[];
  interactions?: DashboardInteractionWire[];
}

interface DashboardVersionDetailWire {
  dashboard: DashboardAssetWire;
  version: DashboardVersionWire;
  theme?: DashboardTheme | null;
  widgets: DashboardWidgetWire[];
  globalFilters?: DashboardGlobalFilterWire[];
  interactions?: DashboardInteractionWire[];
}

const dashboardPath = (dashboardId: string) =>
  `${DASHBOARD_API}/${encodeURIComponent(dashboardId)}`;

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

const toDashboardVersionSummary = (
  value: DashboardVersionWire,
): DashboardVersionSummary => ({
  id: String(value.id),
  dashboardId: String(value.dashboardId),
  versionNo: value.versionNo,
  name: value.name,
  description: value.description || '',
  activeDatasetId: value.activeDatasetId
    ? String(value.activeDatasetId)
    : undefined,
  createTime: value.createTime,
});

const toDashboardWidget = (value: DashboardWidgetWire): DashboardWidget => ({
  id: value.widgetKey,
  analysisId: value.analysisId ? String(value.analysisId) : undefined,
  title: value.title || undefined,
  inlineAnalysis: value.inlineAnalysis || undefined,
  x: value.x,
  y: value.y,
  w: value.w,
  h: value.h,
  minW: value.minW ?? undefined,
  minH: value.minH ?? undefined,
});

const operatorFromWire = (
  value: DashboardGlobalFilterWire['operator'],
): FilterOperator =>
  ({
    EQ: 'eq',
    NE: 'neq',
    CONTAINS: 'contains',
    GT: 'gt',
    GTE: 'gte',
    LT: 'lt',
    LTE: 'lte',
  })[value] as FilterOperator;

const operatorToWire = (
  value: FilterOperator,
): DashboardGlobalFilterWire['operator'] =>
  ({
    eq: 'EQ',
    neq: 'NE',
    contains: 'CONTAINS',
    gt: 'GT',
    gte: 'GTE',
    lt: 'LT',
    lte: 'LTE',
  })[value] as DashboardGlobalFilterWire['operator'];

const toDashboardGlobalFilter = (
  value: DashboardGlobalFilterWire,
): DashboardGlobalFilter => ({
  id: value.filterKey,
  name: value.name,
  operator: operatorFromWire(value.operator),
  defaultValue: value.defaultValue,
  bindings: (value.bindings || []).map((binding) => ({
    widgetId: binding.widgetKey,
    field: binding.fieldId,
  })),
});

const toDashboardInteraction = (
  value: DashboardInteractionWire,
): DashboardInteraction => ({
  id: value.interactionKey,
  event: 'select',
  sourceWidgetId: value.sourceWidgetKey,
  sourceField: value.sourceFieldId,
  targetFilterId: value.targetFilterKey,
});

export const toDashboardServerDetail = (
  value: DashboardDetailWire,
): DashboardServerDetail => ({
  dashboard: toDashboardSummary(value.dashboard),
  currentVersion: value.currentVersion
    ? toDashboardVersionSummary(value.currentVersion)
    : undefined,
  theme: value.theme || undefined,
  versions: (value.versions || []).map(toDashboardVersionSummary),
  widgets: (value.widgets || []).map(toDashboardWidget),
  globalFilters: (value.globalFilters || []).map(toDashboardGlobalFilter),
  interactions: (value.interactions || []).map(toDashboardInteraction),
});

const toDashboardVersionDetail = (
  value: DashboardVersionDetailWire,
): DashboardVersionDetail => {
  const dashboard = toDashboardSummary(value.dashboard);
  const version = toDashboardVersionSummary(value.version);
  const normalized = normalizeDashboardDocument({
    version: 1,
    id: dashboard.id,
    name: version.name,
    description: version.description,
    activeDatasetId: version.activeDatasetId || '',
    theme: value.theme || undefined,
    widgets: (value.widgets || []).map(toDashboardWidget),
    globalFilters: (value.globalFilters || []).map(toDashboardGlobalFilter),
    interactions: (value.interactions || []).map(toDashboardInteraction),
    currentVersionNo: version.versionNo,
    currentVersionId: version.id,
    publishedVersionNo: dashboard.publishedVersionNo,
    publishedVersionId: dashboard.publishedVersionId,
    publishedAt: dashboard.publishedTime,
    updatedAt: dashboard.updateTime,
  });

  return {
    dashboard,
    version,
    theme: normalized.theme,
    widgets: normalized.widgets,
    globalFilters: normalized.globalFilters,
    interactions: normalized.interactions,
  };
};

export const toDashboardDocument = (
  detail: DashboardServerDetail,
): DashboardDocument =>
  normalizeDashboardDocument({
    version: 1,
    id: detail.dashboard.id,
    name: detail.currentVersion?.name || detail.dashboard.name,
    description:
      detail.currentVersion?.description || detail.dashboard.description,
    activeDatasetId: detail.currentVersion?.activeDatasetId || '',
    theme: detail.theme,
    widgets: detail.widgets,
    globalFilters: detail.globalFilters,
    interactions: detail.interactions,
    currentVersionNo: detail.dashboard.currentVersionNo,
    currentVersionId: detail.dashboard.currentVersionId,
    publishedVersionNo: detail.dashboard.publishedVersionNo,
    publishedVersionId: detail.dashboard.publishedVersionId,
    publishedAt: detail.dashboard.publishedTime,
    updatedAt: detail.dashboard.updateTime,
  });

const toDashboardPayload = (document: DashboardDocument) => {
  const normalized = normalizeDashboardDocument(document);
  return {
    name: normalized.name,
    description: normalized.description || undefined,
    activeDatasetId: normalized.activeDatasetId || undefined,
    theme: normalized.theme,
    widgets: normalized.widgets.map((item) => ({
      widgetKey: item.id,
      analysisId: item.analysisId,
      title: item.title,
      inlineAnalysis: item.inlineAnalysis,
      x: item.x,
      y: item.y,
      w: item.w,
      h: item.h,
      minW: item.minW,
      minH: item.minH,
    })),
    globalFilters: normalized.globalFilters.map((filter) => ({
      filterKey: filter.id,
      name: filter.name,
      operator: operatorToWire(filter.operator),
      defaultValue: filter.defaultValue,
      bindings: filter.bindings.map((binding) => ({
        widgetKey: binding.widgetId,
        fieldId: binding.field,
      })),
    })),
    interactions: normalized.interactions.map((item) => ({
      interactionKey: item.id,
      event: 'SELECT',
      sourceWidgetKey: item.sourceWidgetId,
      sourceFieldId: item.sourceField,
      targetFilterKey: item.targetFilterId,
    })),
  };
};

export const listDashboards = async (): Promise<DashboardSummary[]> => {
  const values = await HttpUtils.getData<DashboardAssetWire[]>(DASHBOARD_API);
  return (values || []).map(toDashboardSummary);
};

/** Compatibility alias retained for existing editor and viewer call sites. */
export const fetchDashboards = listDashboards;

export const getDashboard = async (
  dashboardId: string,
): Promise<DashboardServerDetail> =>
  toDashboardServerDetail(
    await HttpUtils.getData<DashboardDetailWire>(dashboardPath(dashboardId)),
  );

export const fetchDashboard = getDashboard;

export const getDashboardVersion = async (
  dashboardId: string,
  versionNo: number,
): Promise<DashboardVersionDetail> =>
  toDashboardVersionDetail(
    await HttpUtils.getData<DashboardVersionDetailWire>(
      `${dashboardPath(dashboardId)}/versions/${versionNo}`,
    ),
  );

export const fetchDashboardVersion = getDashboardVersion;

export const getPublishedDashboard = async (
  dashboardId: string,
): Promise<DashboardVersionDetail> =>
  toDashboardVersionDetail(
    await HttpUtils.getData<DashboardVersionDetailWire>(
      `${dashboardPath(dashboardId)}/published`,
    ),
  );

export const fetchPublishedDashboard = getPublishedDashboard;

export const createDashboard = async (
  document: DashboardDocument,
): Promise<DashboardServerDetail> =>
  toDashboardServerDetail(
    await HttpUtils.postData<DashboardDetailWire>(
      DASHBOARD_API,
      toDashboardPayload(document),
    ),
  );

export const saveDashboardVersion = async (
  dashboardId: string,
  document: DashboardDocument,
): Promise<DashboardServerDetail> =>
  toDashboardServerDetail(
    await HttpUtils.postData<DashboardDetailWire>(
      `${dashboardPath(dashboardId)}/versions`,
      toDashboardPayload(document),
    ),
  );

export const publishDashboard = async (
  dashboardId: string,
): Promise<DashboardServerDetail> =>
  toDashboardServerDetail(
    await HttpUtils.postData<DashboardDetailWire>(
      `${dashboardPath(dashboardId)}/publish`,
      {},
    ),
  );

export const restoreDashboardVersion = async (
  dashboardId: string,
  versionNo: number,
): Promise<DashboardServerDetail> =>
  toDashboardServerDetail(
    await HttpUtils.postData<DashboardDetailWire>(
      `${dashboardPath(dashboardId)}/restore/${versionNo}`,
      {},
    ),
  );

export const activateDashboardVersion = restoreDashboardVersion;

export const deleteDashboard = async (dashboardId: string): Promise<void> => {
  await HttpUtils.deleteData<boolean>(dashboardPath(dashboardId));
};
