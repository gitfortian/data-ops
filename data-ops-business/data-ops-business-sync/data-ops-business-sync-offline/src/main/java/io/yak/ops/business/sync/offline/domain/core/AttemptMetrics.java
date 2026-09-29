package io.yak.ops.business.sync.offline.domain.core;

/** Runtime evidence retained by an ExecutionAttempt. */
public record AttemptMetrics(
    long sourceRecordCount,
    long sinkAttemptedRecordCount,
    long sinkSuccessRecordCount,
    long sinkCommittedRecordCount,
    long sourceReadBytes,
    long sinkWrittenBytes,
    double sourceAverageQps,
    double sinkAverageQps,
    long failedRecordCount,
    long skippedRecordCount,
    long databaseCommitMillis,
    long sqlExecutionMillis,
    double qps,
    long durationMillis) {

  public AttemptMetrics {
    requireNonNegative(sourceRecordCount, "sourceRecordCount");
    requireNonNegative(sinkAttemptedRecordCount, "sinkAttemptedRecordCount");
    requireNonNegative(sinkSuccessRecordCount, "sinkSuccessRecordCount");
    requireNonNegative(sinkCommittedRecordCount, "sinkCommittedRecordCount");
    requireNonNegative(sourceReadBytes, "sourceReadBytes");
    requireNonNegative(sinkWrittenBytes, "sinkWrittenBytes");
    requireNonNegative(sourceAverageQps, "sourceAverageQps");
    requireNonNegative(sinkAverageQps, "sinkAverageQps");
    requireNonNegative(failedRecordCount, "failedRecordCount");
    requireNonNegative(skippedRecordCount, "skippedRecordCount");
    requireNonNegative(databaseCommitMillis, "databaseCommitMillis");
    requireNonNegative(sqlExecutionMillis, "sqlExecutionMillis");
    requireNonNegative(qps, "qps");
    requireNonNegative(durationMillis, "durationMillis");
  }

  public static AttemptMetrics empty() {
    return new AttemptMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
  }

  private static void requireNonNegative(long value, String field) {
    if (value < 0) throw new IllegalArgumentException(field + " 不能小于 0");
  }

  private static void requireNonNegative(double value, String field) {
    if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
      throw new IllegalArgumentException(field + " 必须是非负有限数");
    }
  }
}
