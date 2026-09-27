package io.yak.ops.business.metric.catalog;

import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import org.springframework.stereotype.Service;

/**
 * Canonical lifecycle command boundary for Metric status/delete mutations.
 *
 * <p>Publication truth is independent from editable catalog state. An active Published Metric
 * Contract must be explicitly withdrawn before disabling the Metric, and any Metric with a durable
 * publication ledger cannot be physically deleted because that would destroy immutable
 * MetricVersion evidence referenced by the ledger.
 */
@Service
public class MetricLifecycleService {

  private final MetricCatalogService catalogService;
  private final MetricPublicationRepository publicationRepository;

  public MetricLifecycleService(
      MetricCatalogService catalogService,
      MetricPublicationRepository publicationRepository) {
    this.catalogService = catalogService;
    this.publicationRepository = publicationRepository;
  }

  public Metric changeStatus(Long metricId, String status, String operator) {
    if (status != null
        && "DISABLED".equalsIgnoreCase(status.trim())
        && publicationRepository.findActive(metricId) != null) {
      throw new MetricException(
          MetricErrorCode.ACTIVE_PUBLICATION_EXISTS,
          "请先显式 Withdraw 当前 Published Metric Contract");
    }
    return catalogService.changeStatus(metricId, status, operator);
  }

  public void delete(Long metricId) {
    if (publicationRepository.hasEvents(metricId)) {
      throw new MetricException(
          MetricErrorCode.PUBLICATION_HISTORY_EXISTS,
          "Publication ledger 为审计事实，不能随 Metric 物理删除");
    }
    catalogService.delete(metricId);
  }
}
