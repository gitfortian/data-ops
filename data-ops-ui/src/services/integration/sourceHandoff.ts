export type IntegrationCreateKind = "batch" | "realtime";

export interface IntegrationSourceHandoff {
  dataSourceId: string;
  dbType?: string;
}

const normalizeSourceHandoff = (
  value?: Partial<IntegrationSourceHandoff> | null
): IntegrationSourceHandoff | undefined => {
  const dataSourceId = String(value?.dataSourceId || "").trim();
  if (!dataSourceId) return undefined;

  const dbType = String(value?.dbType || "").trim();
  return {
    dataSourceId,
    ...(dbType ? { dbType } : {}),
  };
};

export const buildIntegrationCreatePath = (
  kind: IntegrationCreateKind,
  source: IntegrationSourceHandoff
): string => {
  const normalized = normalizeSourceHandoff(source);
  if (!normalized) {
    return kind === "batch" ? "/sync/batch-link-up" : "/sync/realtime";
  }

  const params = new URLSearchParams();
  params.set("create", "1");
  params.set("sourceDataSourceId", normalized.dataSourceId);
  if (normalized.dbType) params.set("sourceDbType", normalized.dbType);

  const basePath = kind === "batch" ? "/sync/batch-link-up" : "/sync/realtime";
  return `${basePath}?${params.toString()}`;
};

export const parseIntegrationSourceHandoff = (
  search: string
): IntegrationSourceHandoff | undefined => {
  const params = new URLSearchParams(search);
  if (params.get("create") !== "1") return undefined;

  return normalizeSourceHandoff({
    dataSourceId: params.get("sourceDataSourceId") || "",
    dbType: params.get("sourceDbType") || undefined,
  });
};

export const stripIntegrationCreateHandoff = (search: string): string => {
  const params = new URLSearchParams(search);
  params.delete("create");
  params.delete("sourceDataSourceId");
  params.delete("sourceDbType");
  const next = params.toString();
  return next ? `?${next}` : "";
};
