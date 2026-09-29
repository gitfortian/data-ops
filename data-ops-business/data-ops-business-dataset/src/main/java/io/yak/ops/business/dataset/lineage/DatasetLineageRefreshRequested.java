package io.yak.ops.business.dataset.lineage;

/** Requests a derived-lineage refresh after the surrounding Dataset transaction commits. */
public record DatasetLineageRefreshRequested(long projectId, long datasetId) {
  public DatasetLineageRefreshRequested {
    if (projectId <= 0L) {
      throw new IllegalArgumentException("projectId 必须大于 0");
    }
    if (datasetId <= 0L) {
      throw new IllegalArgumentException("datasetId 必须大于 0");
    }
  }
}
