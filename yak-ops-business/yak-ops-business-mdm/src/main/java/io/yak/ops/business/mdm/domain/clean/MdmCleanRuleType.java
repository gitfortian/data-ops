package io.yak.ops.business.mdm.domain.clean;

/** 清洗规则类型:本期仅 DEDUP(去重),STANDARDIZE/COMPLETE 随 57 落地。 */
public enum MdmCleanRuleType {
  DEDUP,
  STANDARDIZE,
  COMPLETE
}
