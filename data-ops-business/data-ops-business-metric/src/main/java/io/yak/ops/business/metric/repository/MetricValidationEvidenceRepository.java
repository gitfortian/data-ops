package io.yak.ops.business.metric.repository;

import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import java.util.List;

/** 指标定义校验证据仓储。证据只追加，不更新历史记录。 */
public interface MetricValidationEvidenceRepository {

  MetricValidationEvidence append(MetricValidationEvidence evidence);

  List<MetricValidationEvidence> listByVersion(Long metricId, int metricVersion);

  /** Latest attempt, including failed/unavailable attempts; bounded independently of history size. */
  MetricValidationEvidence findLatest(Long metricId, int metricVersion);

  /** Latest PASSED evidence from a READY provider for the exact MetricVersion. */
  MetricValidationEvidence findLatestReady(Long metricId, int metricVersion);

  boolean hasEvidence(Long metricId);
}
