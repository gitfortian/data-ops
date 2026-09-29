package io.yak.ops.business.metric.domain;

import java.time.LocalDateTime;

/** 指标域对象。 */
public record Metric(
    Long id,
    String metricCode,
    String metricName,
    Long domainId,
    Long processId,
    MetricType metricType,
    Long caliberId,
    String calRule,
    String measureExpr,
    String filterExpr,
    String dimModelIds,
    Long refMetricId,
    String dimConstraint,
    String qualifiersJson,
    Long modelId,
    String statDimensions,
    StatPeriod statPeriod,
    Long unitId,
    String businessDesc,
    String owner,
    MetricStatus status,
    int version,
    String createdBy,
    String updatedBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public Metric withPersisted(Long id, String operator, LocalDateTime time) {
    return new Metric(id, metricCode, metricName, domainId, processId, metricType,
        caliberId, calRule, measureExpr, filterExpr, dimModelIds, refMetricId, dimConstraint,
        qualifiersJson,
        modelId, statDimensions, statPeriod, unitId, businessDesc, owner, status, version,
        operator, operator, time, time);
  }

  public Metric withEditable(
      String metricName, Long domainId, Long processId, MetricType metricType,
      Long caliberId, String calRule, String measureExpr, String filterExpr,
      String dimModelIds, Long refMetricId, String dimConstraint, String qualifiersJson,
      Long modelId, String statDimensions, StatPeriod statPeriod, Long unitId,
      String businessDesc, String owner, String updatedBy, LocalDateTime updateTime) {
    return new Metric(id, metricCode, metricName, domainId, processId, metricType,
        caliberId, calRule, measureExpr, filterExpr, dimModelIds, refMetricId, dimConstraint,
        qualifiersJson,
        modelId, statDimensions, statPeriod, unitId, businessDesc, owner, status, version + 1,
        createdBy, updatedBy, createTime, updateTime);
  }

  public Metric withStatus(MetricStatus newStatus, String updatedBy, LocalDateTime updateTime) {
    return new Metric(id, metricCode, metricName, domainId, processId, metricType,
        caliberId, calRule, measureExpr, filterExpr, dimModelIds, refMetricId, dimConstraint,
        qualifiersJson,
        modelId, statDimensions, statPeriod, unitId, businessDesc, owner, newStatus, version + 1,
        createdBy, updatedBy, createTime, updateTime);
  }
}
