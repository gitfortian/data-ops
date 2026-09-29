package io.yak.ops.business.dataset;

import java.time.Instant;

public record DatasetVersion(
    long id,
    long datasetId,
    int versionNo,
    DatasetSourceType sourceType,
    long sourceTaskAssetId,
    long sourceTaskRevisionId,
    int sourceTaskRevisionNo,
    String dataSourceId,
    String sql,
    String schemaSnapshot,
    Instant createTime) {

  /** Keeps existing QUERY_REVISION-focused callers source compatible. */
  public DatasetVersion(
      long id,
      long datasetId,
      int versionNo,
      DatasetSourceType sourceType,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      String schemaSnapshot,
      Instant createTime) {
    this(
        id,
        datasetId,
        versionNo,
        sourceType,
        sourceTaskAssetId,
        sourceTaskRevisionId,
        sourceTaskRevisionNo,
        null,
        null,
        schemaSnapshot,
        createTime);
  }
}
