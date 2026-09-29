package io.yak.ops.business.mdm.domain.clean;

/**
 * 去重匹配方式:EXACT 字段原值精确相等;FUZZY 规范化(trim + 小写)后相等。
 * 相似度阈值等复杂匹配随 AI 匹配(requirement.md 九)演进,本期不落库。
 */
public enum MdmMatchType {
  EXACT,
  FUZZY
}
