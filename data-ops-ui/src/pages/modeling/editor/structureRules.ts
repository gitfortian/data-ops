import type { ModelingTypeOption } from "@/services/modeling/types";

export interface ColumnDraft {
  key: number;
  columnName: string;
  dataType: string;
  length?: number | null;
  scale?: number | null;
  nullable: boolean;
  defaultValue?: string;
  comment?: string;
  businessDescription?: string;
  /** 数据标准松散引用(ticket 30 预留 / 39 套用写入)。 */
  stdTypeId?: number | null;
  stdNamingId?: number | null;
  stdCodeSetCode?: string | null;
  stdUnitId?: number | null;
  stdCaliberId?: number | null;
  stdSecurityId?: number | null;
  /** 标准字段关联(38/44 写入;保存时必须回传,否则整表替换会抹掉治理结果)。 */
  stdFieldId?: number | null;
  /** 聚合层字段角色(DWS/ADS):DIMENSION 分组键 / MEASURE 度量;非聚合层不展示。 */
  fieldRole?: "DIMENSION" | "MEASURE" | null;
  /** 聚合函数(MEASURE 必填;SUM/COUNT/COUNT_DISTINCT/MAX/MIN/AVG)。 */
  aggregateFunc?: string | null;
  /** 口径/转换表达式(度量口径)。 */
  transformExpr?: string | null;
}

export interface IndexDraft {
  key: number;
  indexName: string;
  uniqueIndex: boolean;
  indexType?: string;
  columns: string[];
}

export interface PropertyDraft {
  key: number;
  propKey: string;
  propValue: string;
}

export const CODE_PATTERN = /^[A-Za-z0-9_][A-Za-z0-9_$]{0,127}$/;

/** 发布审批(01):flowCode/bizType 与后端 ApprovalFlowCodes.MODEL_PUBLISH、ModelPublishApprovalService.BIZ_TYPE 同源。 */
export const MODEL_PUBLISH_FLOW_CODE = "MODEL_PUBLISH";
export const MODEL_PUBLISH_BIZ_TYPE = "MODEL";
/** 审批中页面对象冻结(保存/发布/回滚禁用)，在途单终态轮询间隔。 */
export const PUBLISH_APPROVAL_POLL_MS = 15_000;

/** C5(2026-09-17):类型选择后自动带出的长度/精度默认值(用户可改)。 */
export const TYPE_DEFAULTS: Record<
  string,
  { length?: number; scale?: number }
> = {
  VARCHAR: { length: 128 },
  CHAR: { length: 32 },
  DECIMAL: { length: 18, scale: 2 },
  NUMERIC: { length: 18, scale: 2 },
  TINYINT: { length: 4 },
  SMALLINT: { length: 6 },
  INT: { length: 11 },
  INTEGER: { length: 11 },
  BIGINT: { length: 20 },
  DATE: {},
  DATETIME: {},
  TIMESTAMP: {},
};

/**
 * 标准字段里的逻辑类型名 → 物理类型候选,取该方言类型目录里第一个支持的。
 * 只收录「目录里大概率没有同名物理类型」的逻辑名;同名即可命中的走原样查询。
 */
const LOGICAL_TYPE_ALIASES: Record<string, string[]> = {
  STRING: ["VARCHAR", "TEXT", "CHAR"],
  INT: ["INT", "INTEGER"],
  INTEGER: ["INTEGER", "INT"],
  DATETIME: ["DATETIME", "TIMESTAMP"],
};

/** 取某类型在该方言目录里的约束;目录未下发约束时返回 undefined(按未知处理,不做破坏性清空)。 */
export const typeSpecOf = (
  catalog: ModelingTypeOption[],
  name: string | undefined | null
): ModelingTypeOption | undefined => {
  const normalized = (name ?? "").trim().toUpperCase();
  if (!normalized) return undefined;
  return catalog.find((item) => (item.name ?? "").toUpperCase() === normalized);
};

/** 由标准字段的类型名(可带精度,如 decimal(18,2))推出该方言可用的类型与长度精度;推不出返回 null。 */
export const resolveColumnType = (
  raw: string | undefined | null,
  catalog: ModelingTypeOption[]
): { dataType: string; length: number | null; scale: number | null } | null => {
  const matched =
    /^([A-Za-z0-9_]+)\s*(?:\(\s*(\d+)\s*(?:,\s*(\d+)\s*)?\))?$/.exec(
      (raw ?? "").trim()
    );
  if (!matched) return null;
  const supported = new Map(
    catalog.map(
      (item) => [(item.name ?? "").toUpperCase(), item.name!] as const
    )
  );
  const logical = matched[1].toUpperCase();
  const dataType =
    supported.get(logical) ??
    (LOGICAL_TYPE_ALIASES[logical] ?? [])
      .map((name) => supported.get(name))
      .find((name): name is string => !!name);
  if (!dataType) return null;
  const spec = typeSpecOf(catalog, dataType);
  const defaults = TYPE_DEFAULTS[dataType.toUpperCase()] ?? {};
  return {
    dataType,
    length: matched[2] ? Number(matched[2]) : defaults.length ?? null,
    // 该方言类型不支持小数位时硬塞一个,保存会被后端 ERROR 阻断
    scale:
      spec?.scaleAllowed === false
        ? null
        : matched[3]
        ? Number(matched[3])
        : defaults.scale ?? null,
  };
};

/** 各方言支持的分区类型;未列出的方言视为不支持分区(AC:按方言标注)。 */
export const PARTITION_TYPES_BY_DIALECT: Record<string, string[]> = {
  MYSQL: ["RANGE", "LIST", "HASH", "KEY"],
  POSTGRESQL: ["RANGE", "LIST", "HASH"],
  ORACLE: ["RANGE", "LIST", "HASH"],
  DORIS: ["RANGE", "LIST", "HASH"],
  STARROCKS: ["RANGE", "LIST"],
};

/** 聚合函数白名单(与后端 ModelStructureService.AGGREGATE_FUNCS 对齐)。 */
export const AGGREGATE_FUNC_OPTIONS = [
  "SUM",
  "COUNT",
  "COUNT_DISTINCT",
  "MAX",
  "MIN",
  "AVG",
];
/** 具备聚合语义的分层:仅这些分层展示 角色/聚合函数/口径 列。 */
export const AGGREGATE_LAYERS = new Set(["DWS", "ADS"]);
