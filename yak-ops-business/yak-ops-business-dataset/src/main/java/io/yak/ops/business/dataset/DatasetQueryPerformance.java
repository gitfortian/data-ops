package io.yak.ops.business.dataset;

import java.time.Instant;

/** Diagnostic trace for one Dataset Query Runtime attempt, including terminal failures. */
public record DatasetQueryPerformance(
    String queryId,
    long datasetId,
    String datasetName,
    Long datasetVersionId,
    Integer datasetVersionNo,
    String sourceType,
    String dataSourceId,
    String sql,
    String sqlHash,
    String actorType,
    String actorId,
    DatasetQueryStatus status,
    String failureStage,
    String errorType,
    String errorMessage,
    long waitMillis,
    long prepareMillis,
    long executeMillis,
    long transferMillis,
    long totalMillis,
    int returnedRows,
    boolean truncated,
    Instant startedAt,
    Instant finishedAt) {

  /** Compatibility constructor for callers created before stable actor attribution. */
  public DatasetQueryPerformance(
      String queryId,
      long datasetId,
      String datasetName,
      Long datasetVersionId,
      Integer datasetVersionNo,
      String sourceType,
      String dataSourceId,
      String sql,
      String sqlHash,
      DatasetQueryStatus status,
      String failureStage,
      String errorType,
      String errorMessage,
      long waitMillis,
      long prepareMillis,
      long executeMillis,
      long transferMillis,
      long totalMillis,
      int returnedRows,
      boolean truncated,
      Instant startedAt,
      Instant finishedAt) {
    this(
        queryId, datasetId, datasetName, datasetVersionId, datasetVersionNo, sourceType,
        dataSourceId, sql, sqlHash, null, null, status, failureStage, errorType, errorMessage,
        waitMillis, prepareMillis, executeMillis, transferMillis, totalMillis, returnedRows,
        truncated, startedAt, finishedAt);
  }

  /** Compatibility constructor for existing SUCCESS-only callers and tests. */
  public DatasetQueryPerformance(
      String queryId,
      long datasetId,
      String datasetName,
      long datasetVersionId,
      int datasetVersionNo,
      String sourceType,
      String dataSourceId,
      String sql,
      long waitMillis,
      long prepareMillis,
      long executeMillis,
      long transferMillis,
      long totalMillis,
      int returnedRows,
      boolean truncated,
      Instant startedAt) {
    this(
        queryId,
        datasetId,
        datasetName,
        datasetVersionId,
        datasetVersionNo,
        sourceType,
        dataSourceId,
        sql,
        null,
        null,
        null,
        DatasetQueryStatus.SUCCESS,
        null,
        null,
        null,
        waitMillis,
        prepareMillis,
        executeMillis,
        transferMillis,
        totalMillis,
        returnedRows,
        truncated,
        startedAt,
        startedAt == null ? null : startedAt.plusMillis(Math.max(0L, totalMillis)));
  }

  public DatasetQueryPerformance {
    if (queryId == null || queryId.isBlank()) {
      throw new IllegalArgumentException("queryId 不能为空");
    }
    actorType = normalize(actorType);
    actorId = normalize(actorId);
    if ((actorType == null) != (actorId == null)) {
      throw new IllegalArgumentException("actorType and actorId must be supplied together");
    }
    status = status == null ? DatasetQueryStatus.SUCCESS : status;
    startedAt = startedAt == null ? Instant.now() : startedAt;
    finishedAt = finishedAt == null
        ? startedAt.plusMillis(Math.max(0L, totalMillis))
        : finishedAt;
  }

  public DatasetQueryPerformance withActor(String type, String id) {
    return new DatasetQueryPerformance(
        queryId, datasetId, datasetName, datasetVersionId, datasetVersionNo, sourceType,
        dataSourceId, sql, sqlHash, type, id, status, failureStage, errorType, errorMessage,
        waitMillis, prepareMillis, executeMillis, transferMillis, totalMillis, returnedRows,
        truncated, startedAt, finishedAt);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
