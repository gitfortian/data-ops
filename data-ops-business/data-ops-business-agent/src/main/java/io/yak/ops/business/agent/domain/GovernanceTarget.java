package io.yak.ops.business.agent.domain;

/** Selected source and auxiliary task; neither is an authorization grant. */
public record GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId,
    String purpose) {
  public GovernanceTarget(Long assetId, String qualityExecutionNo) {
    this(assetId, qualityExecutionNo, null, null);
  }

  public GovernanceTarget {
    if ((assetId == null ? 0 : 1) + (qualityExecutionNo == null ? 0 : 1)
        + (qualityMonitorId == null ? 0 : 1) != 1) {
      throw new IllegalArgumentException("请选择一个资产、质量执行或监控");
    }
    if (assetId != null && assetId <= 0) throw new IllegalArgumentException("资产编号无效");
    if (qualityMonitorId != null && qualityMonitorId <= 0) throw new IllegalArgumentException("监控编号无效");
    if (qualityExecutionNo != null && !qualityExecutionNo.matches("[A-Za-z0-9_-]{1,128}")) {
      throw new IllegalArgumentException("质量执行编号无效");
    }
    if (purpose != null && !("QUALITY_RULES".equals(purpose) && qualityMonitorId != null)
        && !("ASSET_DESCRIPTION".equals(purpose) && assetId != null)) {
      throw new IllegalArgumentException("辅助任务与目标不匹配");
    }
    if (qualityMonitorId != null && !"QUALITY_RULES".equals(purpose)) {
      throw new IllegalArgumentException("监控目标需要规则建议任务");
    }
  }
}
