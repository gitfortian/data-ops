import type {
  MdmCleanRuleRecord,
  MdmCleanRuleType,
  MdmMatchType,
} from "@/services/mdm/types";

export interface MatchFieldRow {
  attrCode?: string;
  matchType: MdmMatchType;
}

/** 标准化的一行映射：属性 + 原值 → 目标值；同一属性可有多行，提交时折叠成一个对象。 */
export interface MappingRow {
  attrCode?: string;
  fromValue?: string;
  toValue?: string;
}

/** 补全的一行：属性 + 默认值。 */
export interface DefaultRow {
  attrCode?: string;
  defaultValue?: string;
}

/** 三种类型共用一个表单：各自只用其中一段字段，提交前按 ruleType 组装成对应 JSON。 */
export interface RuleFormValues {
  ruleType: MdmCleanRuleType;
  ruleName: string;
  condition: "AND" | "OR";
  fields: MatchFieldRow[];
  mappings: MappingRow[];
  defaults: DefaultRow[];
}

/** 规则类型：'ALL' 只用于筛选，不是后端的类型值。 */
export const parseExpr = (raw?: string): Record<string, unknown> => {
  if (!raw) {
    return {};
  }
  try {
    return JSON.parse(raw) as Record<string, unknown>;
  } catch {
    return {};
  }
};

const asObject = (value: unknown): Record<string, unknown> =>
  value && typeof value === "object" ? (value as Record<string, unknown>) : {};

/** {属性:{原值:目标值}} → 平铺行；一个原值一行，比嵌套编辑器少两层点击。 */
export const mappingRows = (value: unknown) =>
  Object.entries(asObject(value)).flatMap(([attrCode, map]) =>
    Object.entries(asObject(map)).map(([fromValue, toValue]) => ({
      attrCode,
      fromValue,
      toValue: String(toValue ?? ""),
    }))
  );

export const defaultRows = (value: unknown) =>
  Object.entries(asObject(value)).map(([attrCode, defaultValue]) => ({
    attrCode,
    defaultValue: String(defaultValue ?? ""),
  }));

/** 表单行 → 后端 rule_expr：三种类型的形状只在这一处组装。 */
export const buildRuleExpr = (values: RuleFormValues): string => {
  if (values.ruleType === "DEDUP") {
    return JSON.stringify({
      fields: (values.fields ?? []).map((field) => ({
        attrCode: field.attrCode,
        matchType: field.matchType,
      })),
      condition: values.condition ?? "AND",
    });
  }
  if (values.ruleType === "STANDARDIZE") {
    const fields: Record<string, Record<string, string>> = {};
    (values.mappings ?? []).forEach((row) => {
      fields[row.attrCode as string] = {
        ...(fields[row.attrCode as string] ?? {}),
        [String(row.fromValue)]: String(row.toValue),
      };
    });
    return JSON.stringify({ fields });
  }
  const defaults: Record<string, string> = {};
  (values.defaults ?? []).forEach((row) => {
    defaults[row.attrCode as string] = String(row.defaultValue);
  });
  return JSON.stringify({ defaults });
};

/** 列表里的标准化/补全规则摊成人话（gender：M→1、F→2），不让运维去猜原始 JSON。 */
export const transformChips = (rule: MdmCleanRuleRecord): string[] => {
  const expr = parseExpr(rule.rawExpr);
  if (rule.ruleType === "STANDARDIZE") {
    return Object.entries(asObject(expr.fields)).map(
      ([attrCode, map]) =>
        `${attrCode}：${Object.entries(asObject(map))
          .map(([fromValue, toValue]) => `${fromValue}→${toValue}`)
          .join("、")}`
    );
  }
  if (rule.ruleType === "COMPLETE") {
    return Object.entries(asObject(expr.defaults)).map(
      ([attrCode, defaultValue]) => `${attrCode} 空值填「${defaultValue}」`
    );
  }
  return [];
};

/** 预览明细只展示真正被改掉的属性，整条快照摊开会淹没变更点。 */
export const changedAttrs = (
  before: Record<string, unknown>,
  after: Record<string, unknown>
) =>
  Object.entries(after ?? {}).filter(
    ([key, value]) => String(before?.[key] ?? "") !== String(value ?? "")
  );
