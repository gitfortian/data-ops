package io.yak.ops.business.mdm.domain.distribution;

/** 分发配置状态:DRAFT 草稿 → ACTIVE 生效 → DISABLED 停用(可回退 ACTIVE)。 */
public enum MdmDistributionStatus {
  DRAFT,
  ACTIVE,
  DISABLED
}
