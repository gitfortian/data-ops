package io.yak.ops.business.metric.catalog;

import io.yak.framework.common.PageData;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.repository.MetricRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read-side metric query service for stable business-context navigation. */
@Component
@RequiredArgsConstructor
public class MetricQueryService {

  private final MetricRepository repository;

  public PageData<Metric> page(
      int pageNo,
      int pageSize,
      Long domainId,
      Long processId,
      String metricType,
      String status,
      String keyword,
      String owner,
      List<Long> tagIds) {
    return repository.page(
        pageNo, pageSize, domainId, processId, metricType, status, keyword, owner, tagIds);
  }
}
