package io.yak.ops.business.metric.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.metric.domain.Metric;
import java.util.List;
import java.util.Optional;

/** 指标主表仓储接口。 */
public interface MetricRepository {

  Metric insert(Metric metric, String operator);

  Optional<Metric> findById(Long id);

  Optional<Metric> findByCode(String metricCode);

  boolean existsByCode(String metricCode);

  boolean update(Metric metric);

  boolean deleteById(Long id);

  /**
   * Backward-compatible catalog query. Existing callers that do not carry Business Process context
   * keep their original behavior while Phase 5 authoring can opt into the stable process filter.
   */
  default PageData<Metric> page(int pageNo, int pageSize, Long domainId, String metricType,
      String status, String keyword, String owner, List<Long> tagIds) {
    return page(pageNo, pageSize, domainId, null, metricType, status, keyword, owner, tagIds);
  }

  PageData<Metric> page(int pageNo, int pageSize, Long domainId, Long processId, String metricType,
      String status, String keyword, String owner, List<Long> tagIds);

  long count();

  long countByDomain(Long domainId);

  long countByType(String metricType);

  List<Metric> listByIds(List<Long> ids);

  /** 找出以 ref_metric_id 引用该指标的下级(派生)指标,供删除/停用引用阻断。 */
  List<Metric> listReferring(Long refMetricId);
}
