package io.yak.ops.business.dataset;

import java.util.List;

/** Domain write contract used to append one immutable Dataset version aggregate. */
public record DatasetVersionDraft(
    long datasetId,
    int versionNo,
    DatasetSourceType sourceType,
    long sourceTaskAssetId,
    long sourceTaskRevisionId,
    int sourceTaskRevisionNo,
    String dataSourceId,
    String sql,
    List<DatasetFieldDefinition> fields) {

  public DatasetVersionDraft {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }

  public static DatasetVersionDraft queryRevision(
      long datasetId,
      int versionNo,
      long sourceTaskAssetId,
      long sourceTaskRevisionId,
      int sourceTaskRevisionNo,
      List<DatasetFieldDefinition> fields) {
    return new DatasetVersionDraft(
        datasetId,
        versionNo,
        DatasetSourceType.QUERY_REVISION,
        sourceTaskAssetId,
        sourceTaskRevisionId,
        sourceTaskRevisionNo,
        null,
        null,
        fields);
  }

  public static DatasetVersionDraft sqlQuery(
      long datasetId,
      int versionNo,
      String dataSourceId,
      String sql,
      List<DatasetFieldDefinition> fields) {
    return new DatasetVersionDraft(
        datasetId,
        versionNo,
        DatasetSourceType.SQL_QUERY,
        0L,
        0L,
        0,
        dataSourceId,
        sql,
        fields);
  }
}
