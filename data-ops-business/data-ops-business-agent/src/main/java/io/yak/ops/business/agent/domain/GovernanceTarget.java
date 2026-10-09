package io.yak.ops.business.agent.domain;

/** Selected source and auxiliary task; neither is an authorization grant. */
public record GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId,
    String purpose, StandardMatchTarget standardMatch, ModelMappingTarget modelMapping, MetricExplanationTarget metricExplanation, MetricDraftTarget metricDraft, MetricChangeReviewTarget metricChangeReview,
    String qualityBaselineExecutionNo, ConsumerVersionImpactTarget consumerVersionImpact) {
  public GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId, String purpose,
      StandardMatchTarget standardMatch, ModelMappingTarget modelMapping, MetricExplanationTarget metricExplanation,
      MetricDraftTarget metricDraft, MetricChangeReviewTarget metricChangeReview, String qualityBaselineExecutionNo) {
    this(assetId, qualityExecutionNo, qualityMonitorId, purpose, standardMatch, modelMapping, metricExplanation,
        metricDraft, metricChangeReview, qualityBaselineExecutionNo, null);
  }
  public GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId, String purpose,
      StandardMatchTarget standardMatch, ModelMappingTarget modelMapping, MetricExplanationTarget metricExplanation,
      MetricDraftTarget metricDraft, MetricChangeReviewTarget metricChangeReview) {
    this(assetId, qualityExecutionNo, qualityMonitorId, purpose, standardMatch, modelMapping, metricExplanation,
        metricDraft, metricChangeReview, null);
  }
  public GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId, String purpose,
      StandardMatchTarget standardMatch, ModelMappingTarget modelMapping, MetricExplanationTarget metricExplanation, MetricDraftTarget metricDraft) {
    this(assetId, qualityExecutionNo, qualityMonitorId, purpose, standardMatch, modelMapping, metricExplanation, metricDraft, null);
  }
  public GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId, String purpose,
      StandardMatchTarget standardMatch, ModelMappingTarget modelMapping, MetricExplanationTarget metricExplanation) {
    this(assetId, qualityExecutionNo, qualityMonitorId, purpose, standardMatch, modelMapping, metricExplanation, null);
  }
  public GovernanceTarget(Long assetId, String qualityExecutionNo, Long qualityMonitorId, String purpose, StandardMatchTarget standardMatch, ModelMappingTarget modelMapping) {
    this(assetId, qualityExecutionNo, qualityMonitorId, purpose, standardMatch, modelMapping, null);
  }
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
    if (qualityBaselineExecutionNo != null && (qualityExecutionNo == null || purpose != null
        || !qualityBaselineExecutionNo.matches("[A-Za-z0-9_-]{1,128}")
        || qualityBaselineExecutionNo.equals(qualityExecutionNo))) {
      throw new IllegalArgumentException("请选择两个不同的质量历史执行");
    }
    if ((assetId == null ? 0 : 1) + (qualityExecutionNo == null ? 0 : 1)
        + (qualityMonitorId == null ? 0 : 1) + (standardMatch == null ? 0 : 1) + (modelMapping == null ? 0 : 1) + (metricExplanation == null ? 0 : 1) + (metricDraft == null ? 0 : 1) + (metricChangeReview == null ? 0 : 1) + (consumerVersionImpact == null ? 0 : 1) != 1) {
      throw new IllegalArgumentException("请选择一个明确的来源或草稿目标");
    }
    if (assetId != null && assetId <= 0) throw new IllegalArgumentException("资产编号无效");
    if (qualityMonitorId != null && qualityMonitorId <= 0) throw new IllegalArgumentException("监控编号无效");
    if (qualityExecutionNo != null && !qualityExecutionNo.matches("[A-Za-z0-9_-]{1,128}")) {
      throw new IllegalArgumentException("质量执行编号无效");
    }
    if (purpose != null && !("QUALITY_RULES".equals(purpose) && qualityMonitorId != null)
        && !("ASSET_DESCRIPTION".equals(purpose) && assetId != null)
        && !("ASSET_IMPACT".equals(purpose) && assetId != null)
        && !("STANDARD_MATCH".equals(purpose) && standardMatch != null)
        && !("MODEL_MAPPING".equals(purpose) && modelMapping != null)
        && !("METRIC_EXPLANATION".equals(purpose) && metricExplanation != null)
        && !("METRIC_DRAFT".equals(purpose) && metricDraft != null)
        && !("METRIC_CHANGE_REVIEW".equals(purpose) && metricChangeReview != null)
        && !("CONSUMER_VERSION_IMPACT".equals(purpose) && consumerVersionImpact != null)) {
      throw new IllegalArgumentException("辅助任务与目标不匹配");
    }
    if (qualityMonitorId != null && !"QUALITY_RULES".equals(purpose)) {
      throw new IllegalArgumentException("监控目标需要规则建议任务");
    }
    if (standardMatch != null && !"STANDARD_MATCH".equals(purpose)) {
      throw new IllegalArgumentException("字段目标需要标准匹配任务");
    }
    if (modelMapping != null && !"MODEL_MAPPING".equals(purpose)) throw new IllegalArgumentException("映射目标需要模型映射任务");
    if (metricChangeReview != null && !"METRIC_CHANGE_REVIEW".equals(purpose)) throw new IllegalArgumentException("版本对需要变更核对任务");
    if (metricDraft != null && !"METRIC_DRAFT".equals(purpose)) throw new IllegalArgumentException("指标草稿目标需要定义辅助任务");
    if (metricExplanation != null && !"METRIC_EXPLANATION".equals(purpose)) throw new IllegalArgumentException("指标目标需要口径解释任务");
    if (consumerVersionImpact != null && !"CONSUMER_VERSION_IMPACT".equals(purpose)) throw new IllegalArgumentException("消费版本需要影响说明任务");
  }
}
