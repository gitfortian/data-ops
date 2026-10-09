import type {
  DevelopmentDataServiceDefinition,
  DevelopmentDataServiceNodeContext,
  DevelopmentDataServiceParameter,
} from "@/services/data-development";
import type { DevelopmentId, DevelopmentResourceNode } from "../../types";

export const normalizeId = (value: unknown): DevelopmentId => {
  if (value === undefined || value === null || String(value).trim() === "")
    return "0";
  return String(value);
};

export const safeArray = <T>(value: T[] | null | undefined): T[] =>
  Array.isArray(value) ? value.filter(Boolean) : [];

export const parseNamedParameters = (sql: string) => {
  const names: string[] = [];
  const seen = new Set<string>();
  const matcher = /(^|[^:]):([A-Za-z_][A-Za-z0-9_]*)/g;
  let match: RegExpExecArray | null;
  while ((match = matcher.exec(sql)) !== null) {
    const name = match[2];
    const key = name.toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    names.push(name);
  }
  return names;
};

export const mergeParameters = (
  names: string[],
  current: DevelopmentDataServiceParameter[]
) => {
  const previous = new Map(
    current
      .filter((item) => item?.name)
      .map((item) => [item.name.toLowerCase(), item])
  );
  return names.map((name) => {
    const item = previous.get(name.toLowerCase());
    return item
      ? {
          ...item,
          name,
          type: item.type === "OBJECT" ? ("STRING" as const) : item.type,
          required: true,
        }
      : {
          name,
          type: "STRING" as const,
          required: true,
        };
  });
};

export const normalizeContext = (
  raw: DevelopmentDataServiceNodeContext,
  node: DevelopmentResourceNode
): DevelopmentDataServiceNodeContext => {
  const rawDraft = raw?.draft;
  const rawDefinition = rawDraft?.definition;
  const nodeId = normalizeId(raw?.nodeId || node.id);
  const definition: DevelopmentDataServiceDefinition = {
    sourceTaskAssetId: rawDefinition?.sourceTaskAssetId,
    sourceTaskRevisionId: rawDefinition?.sourceTaskRevisionId,
    sourceTaskRevisionNo: Number(rawDefinition?.sourceTaskRevisionNo || 0),
    dataSourceId: normalizeId(rawDefinition?.dataSourceId),
    sql: rawDefinition?.sql || "",
    serviceName: rawDefinition?.serviceName || raw?.nodeName || node.name,
    path: rawDefinition?.path || `/query/${nodeId}`,
    method: "GET",
    parameters: safeArray(rawDefinition?.parameters),
    responseFields: safeArray(rawDefinition?.responseFields),
    maxRows: Number(rawDefinition?.maxRows || 1000),
    timeoutSeconds: Number(rawDefinition?.timeoutSeconds || 30),
    description: rawDefinition?.description || undefined,
    paginationEnabled: Boolean(rawDefinition?.paginationEnabled),
    autoParseParameters: rawDefinition?.autoParseParameters !== false,
  };

  return {
    nodeId,
    nodeName: raw?.nodeName || node.name,
    configured: Boolean(raw?.configured),
    draft: {
      nodeId: normalizeId(rawDraft?.nodeId || nodeId),
      definition,
      draftRevision: Number(rawDraft?.draftRevision || 0),
      createTime: rawDraft?.createTime,
      updateTime: rawDraft?.updateTime,
    },
    latestPublishedRevision: raw?.latestPublishedRevision || null,
    revisions: safeArray(raw?.revisions),
  };
};
