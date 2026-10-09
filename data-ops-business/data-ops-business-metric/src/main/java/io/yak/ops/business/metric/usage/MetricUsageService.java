package io.yak.ops.business.metric.usage;

import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.api.MetricUsageApi.MetricVersionRef;
import io.yak.ops.business.metric.domain.MetricUsage;
import io.yak.ops.business.metric.publication.MetricPublicationService;
import io.yak.ops.business.metric.publication.MetricPublicationService.PublishedMetricContract;
import io.yak.ops.business.metric.repository.MetricUsageRepository;
import io.yak.ops.business.metric.repository.MetricUsageRepository.UsageTypeCount;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Default usage SPI implementation: project-scoped reference writes
 * plus server-side count aggregation. Fail-open: record() catches all
 * exceptions and logs them instead of propagating.
 */
@Component
@Slf4j
public class MetricUsageService implements MetricUsageApi {

  private final MetricUsageRepository repository;
  private final CurrentProject currentProject;
  private final MetricPublicationService publicationService;

  public MetricUsageService(
      MetricUsageRepository repository,
      CurrentProject currentProject,
      MetricPublicationService publicationService) {
    this.repository = repository;
    this.currentProject = currentProject;
    this.publicationService = publicationService;
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void record(MetricUsageEvent event) {
    try {
      PublishedMetricContract active = publicationService.activeForBinding(event.metricId());
      if (active == null) return;
      Long projectId = currentProject.requireProjectId();
      if (event.projectId() != null && !event.projectId().equals(projectId)) {
        throw new IllegalArgumentException("Metric usage project does not match the current project");
      }
      repository.append(projectId, new MetricUsage(
          null, event.metricId(), active.metricVersion(), event.usageType(), event.usageId(), event.usageName(),
          LocalDateTime.now()));
    } catch (RuntimeException e) {
      log.warn("Metric usage record failed (fail-open): metricId={}, usageType={}, error={}",
          event.metricId(), event.usageType(), e.getMessage());
    }
  }

  @Override
  public void revoke(String usageType, Long usageId) {
    try {
      repository.deleteForConsumer(usageType, usageId);
    } catch (RuntimeException e) {
      log.warn("Metric usage revoke failed (fail-open): usageType={}, usageId={}, error={}",
          usageType, usageId, e.getMessage());
    }
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void syncBindings(String usageType, Long usageId, String usageName, List<Long> metricIds) {
    try {
      Long projectId = currentProject.requireProjectId();
      List<MetricVersionRef> references = (metricIds == null ? List.<Long>of() : metricIds).stream()
          .filter(java.util.Objects::nonNull)
          .distinct()
          .sorted()
          .map(metricId -> {
            PublishedMetricContract active = publicationService.activeForBinding(metricId);
            if (active == null) {
              throw new IllegalArgumentException(
                  "Metric " + metricId + " has no active Published Metric Contract");
            }
            return new MetricVersionRef(metricId, active.metricVersion());
          })
          .toList();
      repository.deleteForConsumer(usageType, usageId);
      LocalDateTime now = LocalDateTime.now();
      for (MetricVersionRef reference : references) {
        repository.append(projectId, new MetricUsage(
            null, reference.metricId(), reference.versionNo(), usageType, usageId, usageName, now));
      }
    } catch (RuntimeException e) {
      // Fail-open for callers, but never commit a deleted or partially rebuilt binding set.
      // This method is transactional: mark rollback-only before swallowing the exception.
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
      }
      log.warn("Metric usage syncBindings failed (fail-open): usageType={}, usageId={}, error={}",
          usageType, usageId, e.getMessage());
    }
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void syncPublishedBindings(
      String usageType, Long usageId, String usageName, List<MetricVersionRef> references) {
    if (usageType == null || usageType.isBlank() || usageId == null || usageId <= 0) {
      throw new IllegalArgumentException("usageType and usageId are required");
    }
    List<MetricVersionRef> normalized = references == null ? List.of() : references.stream()
        .filter(java.util.Objects::nonNull)
        .distinct()
        .toList();
    for (MetricVersionRef reference : normalized.stream()
        .sorted(java.util.Comparator.comparing(
            MetricVersionRef::metricId,
            java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
        .toList()) {
      if (reference.metricId() == null || reference.metricId() <= 0
          || reference.versionNo() == null || reference.versionNo() <= 0) {
        throw new IllegalArgumentException("A governed Metric reference requires a positive id and version");
      }
      PublishedMetricContract active = publicationService.activeForBinding(reference.metricId());
      if (active == null || !reference.versionNo().equals(active.metricVersion())) {
        throw new IllegalArgumentException(
            "Metric " + reference.metricId() + " version " + reference.versionNo()
                + " is not the active Published Metric Contract");
      }
    }

    Long projectId = currentProject.requireProjectId();
    repository.deleteForConsumer(usageType, usageId);
    LocalDateTime now = LocalDateTime.now();
    for (MetricVersionRef reference : normalized) {
      repository.append(projectId, new MetricUsage(
          null, reference.metricId(), reference.versionNo(), usageType, usageId, usageName, now));
    }
  }

  @Override
  public List<Long> boundMetricIds(String usageType, Long usageId) {
    return repository.listForConsumer(usageType, usageId).stream()
        .map(MetricUsage::metricId)
        .distinct()
        .toList();
  }

  @Override
  public List<MetricVersionRef> boundMetricVersionRefs(String usageType, Long usageId) {
    return repository.listForConsumer(usageType, usageId).stream()
        .map(row -> new MetricVersionRef(row.metricId(), row.metricVersion()))
        .distinct()
        .toList();
  }

  @Override
  public UsageSummary summary(Long metricId) {
    long total = repository.countByMetric(metricId);
    List<UsageTypeCount> rows = repository.countGroupByType(metricId);
    long report = 0, dataset = 0, dashboard = 0, api = 0, screen = 0;
    for (UsageTypeCount row : rows) {
      String type = row.usageType();
      long cnt = row.count();
      switch (type) {
        case "REPORT" -> report = cnt;
        case "DATASET" -> dataset = cnt;
        case "DASHBOARD" -> dashboard = cnt;
        case "API" -> api = cnt;
        case "SCREEN" -> screen = cnt;
        default -> { /* ignore unknown types */ }
      }
    }
    return new UsageSummary(metricId, total, report, dataset, dashboard, api, screen);
  }

  public List<MetricUsage> listByMetric(Long metricId) {
    return repository.listByMetric(metricId);
  }
}
