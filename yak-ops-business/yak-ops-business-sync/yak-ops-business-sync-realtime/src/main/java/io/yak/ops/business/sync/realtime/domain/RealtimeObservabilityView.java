package io.yak.ops.business.sync.realtime.domain;

import java.util.List;

/** Stable, UI-oriented observability model derived from Flink REST responses. */
public record RealtimeObservabilityView(
    String engineJobId,
    String flinkJobName,
    String flinkState,
    Long startTime,
    Long durationMs,
    String flinkWebUrl,
    long sampledAt,
    CheckpointSummary checkpoints,
    MetricSummary metrics) {

  public record CheckpointSummary(
      long total,
      long completed,
      long failed,
      long inProgress,
      long restored,
      CheckpointDetail latestCompleted,
      CheckpointDetail latestFailed) {}

  public record CheckpointDetail(
      Long id,
      Long triggerTimestamp,
      Long latestAckTimestamp,
      Long durationMs,
      Long stateSizeBytes,
      Long checkpointedSizeBytes,
      Integer acknowledgedSubtasks,
      Integer totalSubtasks,
      String failureMessage) {}

  public record MetricSummary(
      Long recordsRead,
      Double recordsReadPerSecond,
      Long recordsWritten,
      Double recordsWrittenPerSecond,
      Long bytesRead,
      Double bytesReadPerSecond,
      Long bytesWritten,
      Double bytesWrittenPerSecond,
      Double maxBusyMsPerSecond,
      Double maxBackpressuredMsPerSecond,
      Double maxIdleMsPerSecond,
      int vertexCount) {}

  public record RuntimeLog(
      String rootException,
      Long timestamp,
      boolean truncated,
      List<RuntimeExceptionEntry> exceptions) {
    public RuntimeLog {
      exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
    }
  }

  public record RuntimeExceptionEntry(
      String exceptionName,
      String stacktrace,
      Long timestamp,
      String taskName,
      String taskManagerId,
      String endpoint) {}
}
