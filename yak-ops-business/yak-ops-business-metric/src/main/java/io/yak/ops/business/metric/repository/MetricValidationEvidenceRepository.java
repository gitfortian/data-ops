package io.yak.ops.business.metric.repository;

import io.yak.ops.common.bean.po.metric.MetricValidationEvidencePO;
import java.util.List;

/** 指标定义校验证据仓储。证据只追加，不更新历史记录。 */
public interface MetricValidationEvidenceRepository {

  MetricValidationEvidencePO append(MetricValidationEvidencePO evidence);

  List<MetricValidationEvidencePO> listByVersion(Long metricId, int metricVersion);

  /** Publication 等后续 Gate 可消费的最近 READY evidence。 */
  MetricValidationEvidencePO findLatestReady(Long metricId, int metricVersion);
}
