package io.yak.ops.business.dataset.observability;

import io.yak.ops.business.dataset.DatasetQueryPerformance;
import io.yak.ops.business.dataset.repository.DatasetQueryPerformanceStore;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Best-effort recorder: observability failures must never fail a Dataset query. */
@Component
public class DatasetQueryPerformanceRecorder {

  private static final Logger LOG = LoggerFactory.getLogger(DatasetQueryPerformanceRecorder.class);

  private final DatasetQueryPerformanceBuffer buffer;
  private final ObjectProvider<DatasetQueryPerformanceStore> storeProvider;
  private final CurrentProject currentProject;
  private final DatasetQuerySqlEvidence sqlEvidence;
  private final DatasetQueryObservabilityProperties properties;
  private final AtomicLong nextCleanupAtMillis = new AtomicLong();

  @Autowired
  public DatasetQueryPerformanceRecorder(
      DatasetQueryPerformanceBuffer buffer,
      ObjectProvider<DatasetQueryPerformanceStore> storeProvider,
      CurrentProject currentProject,
      DatasetQuerySqlEvidence sqlEvidence,
      DatasetQueryObservabilityProperties properties) {
    this.buffer = buffer;
    this.storeProvider = storeProvider;
    this.currentProject = currentProject;
    this.sqlEvidence = sqlEvidence;
    this.properties = properties;
  }

  /** Compatibility constructor for focused unit tests without Spring infrastructure. */
  public DatasetQueryPerformanceRecorder(DatasetQueryPerformanceBuffer buffer) {
    this(
        buffer,
        null,
        Optional::<ProjectContext>empty,
        new DatasetQuerySqlEvidence(),
        new DatasetQueryObservabilityProperties());
  }

  public void record(DatasetQueryPerformance trace) {
    if (trace == null) return;
    try {
      recordSafely(trace);
    } catch (RuntimeException exception) {
      LOG.warn("Dataset query diagnostics failed completely; business query remains unaffected", exception);
      try {
        buffer.add(null, withoutSensitiveText(trace));
      } catch (RuntimeException fallbackException) {
        LOG.warn("Dataset query diagnostics fallback also failed", fallbackException);
      }
    }
  }

  private void recordSafely(DatasetQueryPerformance trace) {
    Long projectId = currentProject.current().map(ProjectContext::projectId).orElse(null);
    DatasetQueryPerformance safeTrace;
    try {
      safeTrace = sqlEvidence.sanitize(trace);
    } catch (RuntimeException exception) {
      LOG.warn("Sanitizing Dataset query diagnostics failed; retaining no SQL evidence", exception);
      safeTrace = withoutSensitiveText(trace);
    }

    DatasetQueryPerformanceStore store = storeProvider == null ? null : storeProvider.getIfAvailable();
    if (store == null) {
      buffer.add(projectId, safeTrace);
      return;
    }

    try {
      store.append(projectId, safeTrace);
    } catch (RuntimeException exception) {
      LOG.warn("Persisting Dataset query diagnostics failed; using local fallback", exception);
      buffer.add(projectId, safeTrace);
      return;
    }

    cleanupIfDue(store);
  }

  private void cleanupIfDue(DatasetQueryPerformanceStore store) {
    long now = System.currentTimeMillis();
    long next = nextCleanupAtMillis.get();
    if (now < next) return;
    long intervalMillis = Math.max(1L, properties.getCleanupIntervalMinutes()) * 60_000L;
    if (!nextCleanupAtMillis.compareAndSet(next, now + intervalMillis)) return;
    try {
      int retentionDays = Math.max(1, properties.getRetentionDays());
      store.deleteBefore(
          Instant.now().minus(retentionDays, ChronoUnit.DAYS),
          Math.max(1, properties.getCleanupBatchSize()));
    } catch (RuntimeException exception) {
      LOG.warn("Cleaning Dataset query diagnostics failed", exception);
    }
  }

  private DatasetQueryPerformance withoutSensitiveText(DatasetQueryPerformance trace) {
    return new DatasetQueryPerformance(
        trace.queryId(), trace.datasetId(), trace.datasetName(), trace.datasetVersionId(),
        trace.datasetVersionNo(), trace.sourceType(), trace.dataSourceId(), null, null,
        trace.status(), trace.failureStage(), trace.errorType(), null, trace.waitMillis(),
        trace.prepareMillis(), trace.executeMillis(), trace.transferMillis(), trace.totalMillis(),
        trace.returnedRows(), trace.truncated(), trace.startedAt(), trace.finishedAt());
  }
}
