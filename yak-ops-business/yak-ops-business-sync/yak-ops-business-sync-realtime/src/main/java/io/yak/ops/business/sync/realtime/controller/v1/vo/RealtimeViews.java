package io.yak.ops.business.sync.realtime.controller.v1.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class RealtimeViews {
  private RealtimeViews() {}

  public record PipelineSpec(
      Long sourceDataSourceRef, Long sinkDataSourceRef, List<TableRoute> tables, String startupMode,
      String schemaEvolution, int parallelism, long checkpointIntervalMs, RestartPolicy restart,
      SinkTuning sink) {}
  public record TableRoute(String sourceTable, String sinkTable, String matchMode, List<String> keyColumns) {}
  public record RestartPolicy(String strategy, int attempts, long delayMs) {}
  public record SinkTuning(int maxRetries, int batchSize, long flushIntervalMs, long maxBatchBytes, int statementCacheSize, boolean strictReplaySafety) {}

  public record RuntimeConfig(
      String restUrl, String flinkHome, String flinkCdcHome, String javaHome,
      String flinkVersion, String flinkCdcVersion, SshConfig ssh) {}
  public record SshConfig(
      String executable, String host, Integer port, String user, String identityFile,
      String knownHostsFile, Boolean strictHostKeyChecking, Integer connectTimeoutSeconds,
      String remoteRestAddress, Integer remoteRestPort) {}
  public record EnvironmentSnapshot(
      long id, String name, String engineType, String deploymentMode, String submitterType,
      RuntimeConfig config, int version) {}

  public record Deployment(
      long id, int definitionVersion, String specSummary, String configDigest,
      String idempotencyKey, String engineJobId, String runtimeRevision,
      EnvironmentSnapshot runtimeEnvironment, String status, boolean resultUncertain,
      String errorMessage, LocalDateTime createTime, LocalDateTime updateTime) {}

  public record Job(
      long id, String name, String description, PipelineSpec spec, long runtimeEnvironmentId,
      String releaseState, String desiredState, String observedState, int definitionVersion,
      Integer publishedVersion, String configDigest, String lastError, LocalDateTime createTime,
      LocalDateTime updateTime, boolean publishedUpdateAvailable, Deployment latestDeployment) {}

  public record Page(List<Job> records, long total, int pageNo, int pageSize) {}
  public record Event(long id, Long deploymentId, String eventType, String fromState, String toState, String message, LocalDateTime createTime) {}
  public record Validation(boolean valid, String deliverySemantics) {}

  public record Observability(
      String engineJobId, String flinkJobName, String flinkState, Long startTime, Long durationMs,
      String flinkWebUrl, long sampledAt, CheckpointSummary checkpoints, MetricSummary metrics) {}
  public record CheckpointSummary(
      long total, long completed, long failed, long inProgress, long restored,
      CheckpointDetail latestCompleted, CheckpointDetail latestFailed) {}
  public record CheckpointDetail(
      Long id, Long triggerTimestamp, Long latestAckTimestamp, Long durationMs,
      Long stateSizeBytes, Long checkpointedSizeBytes, Integer acknowledgedSubtasks,
      Integer totalSubtasks, String failureMessage) {}
  public record MetricSummary(
      Long recordsRead, Double recordsReadPerSecond, Long recordsWritten, Double recordsWrittenPerSecond,
      Long bytesRead, Double bytesReadPerSecond, Long bytesWritten, Double bytesWrittenPerSecond,
      Double maxBusyMsPerSecond, Double maxBackpressuredMsPerSecond, Double maxIdleMsPerSecond,
      int vertexCount) {}
  public record RuntimeLog(String rootException, Long timestamp, boolean truncated, List<RuntimeExceptionEntry> exceptions) {}
  public record RuntimeExceptionEntry(
      String exceptionName, String stacktrace, Long timestamp, String taskName,
      String taskManagerId, String endpoint) {}
}
