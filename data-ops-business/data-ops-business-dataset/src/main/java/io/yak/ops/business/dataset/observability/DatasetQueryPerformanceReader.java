package io.yak.ops.business.dataset.observability;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.DatasetQueryStatus;
import io.yak.ops.business.dataset.repository.DatasetQueryPerformanceStore;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Reads persisted cross-instance diagnostics and merges any local persistence fallback. */
@Component
public class DatasetQueryPerformanceReader {

  public static final int MAX_QUERY_LIMIT = 200;
  private static final Logger LOG = LoggerFactory.getLogger(DatasetQueryPerformanceReader.class);

  private final DatasetQueryPerformanceBuffer buffer;
  private final ObjectProvider<DatasetQueryPerformanceStore> storeProvider;
  private final CurrentProject currentProject;

  @Autowired
  public DatasetQueryPerformanceReader(
      DatasetQueryPerformanceBuffer buffer,
      ObjectProvider<DatasetQueryPerformanceStore> storeProvider,
      CurrentProject currentProject) {
    this.buffer = buffer;
    this.storeProvider = storeProvider;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped reads fail closed. */
  public DatasetQueryPerformanceReader(DatasetQueryPerformanceBuffer buffer) {
    this(buffer, null, Optional::<ProjectContext>empty);
  }

  public List<DatasetQueryPerformance> recent(Set<Long> datasetIds, int requestedLimit) {
    return recent(datasetIds, Set.of(), Set.of(), null, requestedLimit);
  }

  public List<DatasetQueryPerformance> recent(
      Set<Long> datasetIds, Set<String> queryIds, int requestedLimit) {
    return recent(datasetIds, queryIds, Set.of(), null, requestedLimit);
  }

  public List<DatasetQueryPerformance> recent(
      Set<Long> datasetIds,
      Set<String> queryIds,
      Set<DatasetQueryStatus> statuses,
      Long minTotalMillis,
      int requestedLimit) {
    int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_LIMIT));
    Set<Long> normalizedDatasetIds = datasetIds == null ? Set.of() : Set.copyOf(datasetIds);
    Set<String> normalizedQueryIds = queryIds == null ? Set.of() : Set.copyOf(queryIds);
    Set<DatasetQueryStatus> normalizedStatuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    Long normalizedMin = minTotalMillis == null ? null : Math.max(0L, minTotalMillis);
    Long projectId = currentProject.requireProjectId();

    List<DatasetQueryPerformance> combined = new ArrayList<>();
    DatasetQueryPerformanceStore store = storeProvider == null ? null : storeProvider.getIfAvailable();
    if (store != null) {
      try {
        combined.addAll(store.recent(
            projectId,
            normalizedDatasetIds,
            normalizedQueryIds,
            normalizedStatuses,
            normalizedMin,
            limit));
      } catch (RuntimeException exception) {
        LOG.warn("Reading persisted Dataset query diagnostics failed; using local fallback", exception);
      }
    }

    for (DatasetQueryPerformanceBuffer.BufferedTrace buffered : buffer.traces()) {
      if (!Objects.equals(projectId, buffered.projectId())) continue;
      DatasetQueryPerformance trace = buffered.trace();
      if (!matches(trace, normalizedDatasetIds, normalizedQueryIds, normalizedStatuses, normalizedMin)) {
        continue;
      }
      combined.add(trace);
    }

    Comparator<DatasetQueryPerformance> newestFirst = Comparator.comparing(
        DatasetQueryPerformance::startedAt,
        Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed();
    Map<String, DatasetQueryPerformance> unique = new LinkedHashMap<>();
    combined.stream().sorted(newestFirst).forEach(trace -> unique.putIfAbsent(trace.queryId(), trace));
    return unique.values().stream().limit(limit).toList();
  }

  /**
   * Reconciliation must read durable source evidence: the local diagnostics buffer is
   * intentionally allowed for the observability UI but cannot prove complete Usage
   * coverage across instances or after a restart.
   *
   * Fail closed when the persisted provider cannot be read. Consumption can then
   * preserve known normalized usage while accurately reporting incomplete coverage.
   */
  public List<DatasetQueryPerformance> recentPersisted(
      Set<Long> datasetIds, Set<DatasetQueryStatus> statuses, int requestedLimit) {
    Long projectId = currentProject.requireProjectId();
    int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_LIMIT));
    Set<Long> ids = datasetIds == null ? Set.of() : Set.copyOf(datasetIds);
    Set<DatasetQueryStatus> states = statuses == null ? Set.of() : Set.copyOf(statuses);
    DatasetQueryPerformanceStore store = storeProvider == null ? null : storeProvider.getIfAvailable();
    if (store == null) {
      throw new IllegalStateException("Persisted Dataset query diagnostics are unavailable");
    }
    return Objects.requireNonNull(
        store.recent(projectId, ids, Set.of(), states, null, limit),
        "Persisted Dataset query diagnostics response is unavailable");
  }

  /**
   * Source-owned persisted success for one immutable DatasetVersion, even
   * when newer versions occupy the ordinary Dataset-wide recent window.
   * Missing persistence fails closed; in-process buffers are not audit truth.
   */
  public List<DatasetQueryPerformance> recentSuccessfulByDatasetAndVersion(
      long datasetId, long datasetVersionId, int requestedLimit) {
    if (datasetId <= 0L || datasetVersionId <= 0L) {
      throw new IllegalArgumentException("Dataset and DatasetVersion IDs must be positive");
    }
    Long projectId = currentProject.requireProjectId();
    int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_LIMIT));
    DatasetQueryPerformanceStore store = storeProvider == null ? null : storeProvider.getIfAvailable();
    if (store == null) {
      throw new IllegalStateException("Persisted Dataset query diagnostics are unavailable");
    }
    return Objects.requireNonNull(
        store.successfulByDatasetAndVersion(projectId, datasetId, datasetVersionId, limit),
        "Persisted exact-version Dataset query diagnostics response is unavailable");
  }

  /**
   * Bounded historical success recovery, ordered by persisted audit ID.
   * Never use local diagnostics buffer as proof of durable historical Usage.
   */
  public List<io.yak.ops.business.dataset.DatasetSuccessfulQueryAudit> successfulPageByDatasetAndVersion(
      long datasetId, long datasetVersionId, Long beforeAuditId, int requestedLimit) {
    if (datasetId <= 0L || datasetVersionId <= 0L
        || (beforeAuditId != null && beforeAuditId <= 0L)) {
      throw new IllegalArgumentException("Dataset, DatasetVersion and cursor IDs must be positive");
    }
    Long projectId = currentProject.requireProjectId();
    int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_LIMIT));
    DatasetQueryPerformanceStore store = storeProvider == null ? null : storeProvider.getIfAvailable();
    if (store == null) {
      throw new IllegalStateException("Persisted Dataset query diagnostics are unavailable");
    }
    return Objects.requireNonNull(
        store.successfulPageByDatasetAndVersion(
            projectId, datasetId, datasetVersionId, beforeAuditId, limit),
        "Persisted Dataset successful audit page is unavailable");
  }

  private boolean matches(
      DatasetQueryPerformance trace,
      Set<Long> datasetIds,
      Set<String> queryIds,
      Set<DatasetQueryStatus> statuses,
      Long minTotalMillis) {
    if (!datasetIds.isEmpty() && !datasetIds.contains(trace.datasetId())) return false;
    if (!queryIds.isEmpty() && !queryIds.contains(trace.queryId())) return false;
    if (!statuses.isEmpty() && !statuses.contains(trace.status())) return false;
    return minTotalMillis == null || trace.totalMillis() >= minTotalMillis;
  }
}
