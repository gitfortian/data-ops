package io.yak.ops.business.dataset.controller.v1.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/** HTTP output contracts for Dataset v1 APIs. */
public final class DatasetViews {

  private DatasetViews() {
  }

  public record DatasetVO(
      @JsonSerialize(using = ToStringSerializer.class) long id,
      String name,
      String description,
      String status,
      @JsonSerialize(using = ToStringSerializer.class) Long currentVersionId,
      Instant createTime,
      Instant updateTime) {
  }

  public record DatasetVersionVO(
      @JsonSerialize(using = ToStringSerializer.class) long id,
      @JsonSerialize(using = ToStringSerializer.class) long datasetId,
      int versionNo,
      String sourceType,
      @JsonSerialize(using = ToStringSerializer.class) long sourceTaskAssetId,
      @JsonSerialize(using = ToStringSerializer.class) long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String dataSourceId,
      String sql,
      String schemaSnapshot,
      Instant createTime) {
  }

  public record DatasetFieldVO(
      String fieldId,
      @JsonSerialize(using = ToStringSerializer.class) long versionId,
      String physicalName,
      String displayName,
      String dataType,
      boolean nullable,
      String description,
      String defaultRole,
      int sortOrder) {
  }

  public record DatasetCatalogVO(
      DatasetVO dataset, DatasetVersionVO currentVersion, List<DatasetFieldVO> fields) {
  }

  public record DatasetDetailVO(
      DatasetVO dataset,
      DatasetVersionVO currentVersion,
      List<DatasetVersionVO> versions,
      List<DatasetFieldVO> fields) {
  }

  public record DatasetQueryBindingVO(
      String key,
      String fieldId,
      String displayName,
      String dataType,
      String aggregation) {
  }

  public record DatasetQueryColumnVO(
      String name,
      String label,
      String typeName,
      int jdbcType,
      boolean nullable) {
  }

  public record DatasetQueryResultVO(
      String queryId,
      @JsonSerialize(using = ToStringSerializer.class) long datasetId,
      @JsonSerialize(using = ToStringSerializer.class) long datasetVersionId,
      int datasetVersionNo,
      List<DatasetQueryBindingVO> bindings,
      List<DatasetQueryColumnVO> columns,
      List<List<Object>> rows,
      int returnedRows,
      boolean truncated,
      long elapsedMillis) {
  }

  public record DatasetQueryPerformanceVO(
      String queryId,
      @JsonSerialize(using = ToStringSerializer.class) long datasetId,
      String datasetName,
      @JsonSerialize(using = ToStringSerializer.class) Long datasetVersionId,
      Integer datasetVersionNo,
      String sourceType,
      String dataSourceId,
      String sql,
      String sqlHash,
      String status,
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
  }
}
