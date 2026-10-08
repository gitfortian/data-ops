package io.yak.ops.business.agent.domain;

/** Selected source and auxiliary task; neither is an authorization grant. */
public record GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId,
    String purpose, StandardMatchTarget standardMatch, ModelMappingTarget modelMapping) {
  public GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId, String purpose, StandardMatchTarget standardMatch) {
    this(assetId, qualityExecutionNo, qualityMonitorId, purpose, standardMatch, null);
  }
  public GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId, String purpose) {
    this(assetId, qualityExecutionNo, qualityMonitorId, purpose, null);
  }
  public GovernanceTarget(Long assetId, String qualityExecutionNo) {
    this(assetId, qualityExecutionNo, null, null);
  }

  public GovernanceTarget {
    if ((assetId == null ? 0 : 1) + (qualityExecutionNo == null ? 0 : 1)
        + (qualityMonitorId == null ? 0 : 1) + (standardMatch == null ? 0 : 1) + (modelMapping == null ? 0 : 1) != 1) {
      throw new IllegalArgumentException("请选择一个资产、质量执行、监控或字段草稿");
    }
    if (assetId != null && assetId <= 0) throw new IllegalArgumentException("资产编号无效");
    if (qualityMonitorId != null && qualityMonitorId <= 0) throw new IllegalArgumentException("监控编号无效");
    if (qualityExecutionNo != null && !qualityExecutionNo.matches("[A-Za-z0-9_-]{1,128}")) {
      throw new IllegalArgumentException("质量执行编号无效");
    }
    if (purpose != null && !("QUALITY_RULES".equals(purpose) && qualityMonitorId != null)
        && !("ASSET_DESCRIPTION".equals(purpose) && assetId != null)
        && !("STANDARD_MATCH".equals(purpose) && standardMatch != null)
        && !("MODEL_MAPPING".equals(purpose) && modelMapping != null)) {
      throw new IllegalArgumentException("辅助任务与目标不匹配");
    }
    if (qualityMonitorId != null && !"QUALITY_RULES".equals(purpose)) {
      throw new IllegalArgumentException("监控目标需要规则建议任务");
    }
    if (standardMatch != null && !"STANDARD_MATCH".equals(purpose)) {
      throw new IllegalArgumentException("字段目标需要标准匹配任务");
    }
    if (modelMapping != null && !"MODEL_MAPPING".equals(purpose)) throw new IllegalArgumentException("映射目标需要模型映射任务");
  }
}
