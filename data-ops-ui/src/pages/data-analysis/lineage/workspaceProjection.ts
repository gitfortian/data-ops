import type { LineageAsset, LineageRelation } from "./types";

export const formatTime = (value?: string) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  })
    .format(date)
    .replaceAll("/", "-");
};

export const formatValue = (value: unknown) => {
  if (value == null) return "-";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean")
    return String(value);
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
};

export const assetLocation = (asset: LineageAsset) =>
  [asset.databaseName, asset.schemaName, asset.tableName, asset.columnName]
    .filter(Boolean)
    .join(".") || "-";

export const businessLink = (
  asset: LineageAsset
): { label: string; path: string } | undefined => {
  if (asset.assetType === "SQL_TASK" && asset.sourceId) {
    return {
      label: "打开开发任务",
      path: `/data-development/task/${encodeURIComponent(asset.sourceId)}`,
    };
  }
  if (asset.assetType === "DASHBOARD" && asset.sourceId) {
    return {
      label: "打开仪表盘",
      path: `/dashboard/${encodeURIComponent(asset.sourceId)}`,
    };
  }
  if (asset.assetType === "DATASET" || asset.assetType === "DATASET_FIELD") {
    return { label: "打开数据目录", path: "/data-analysis/data-catalog" };
  }
  if (asset.assetType === "CHART") {
    return { label: "打开仪表盘", path: "/dashboard" };
  }
  if (asset.assetType === "TABLE" || asset.assetType === "COLUMN") {
    return { label: "打开数据源", path: "/data-source" };
  }
  if (
    asset.assetType === "DATABASE_SERVICE" ||
    asset.assetType === "DATABASE"
  ) {
    return { label: "打开数据源", path: "/data-source" };
  }
  if (asset.assetType === "METRIC") {
    const metricId = asset.sourceId || asset.assetKey.replace(/^metric:/, "");
    return metricId
      ? {
          label: "打开指标",
          path: `/metric/manage/${encodeURIComponent(metricId)}`,
        }
      : undefined;
  }
  return undefined;
};

export const assetPropertyEntries = (asset?: LineageAsset) =>
  Object.entries(asset?.properties || {})
    .filter(([, value]) => value !== undefined && value !== null)
    .slice(0, 18);

export const relationPropertyEntries = (relation?: LineageRelation) =>
  Object.entries(relation?.properties || {})
    .filter(([, value]) => value !== undefined && value !== null)
    .slice(0, 18);
