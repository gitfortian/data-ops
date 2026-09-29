package io.yak.ops.business.dataset;

import java.util.List;

/** Lightweight Dataset read model for operational overview pages. */
public record DatasetOverviewSnapshot(
    long datasetCount,
    long createdCount,
    List<Dataset> recentDatasets,
    List<Dataset> onlineDatasets) {

  public DatasetOverviewSnapshot {
    recentDatasets = recentDatasets == null ? List.of() : List.copyOf(recentDatasets);
    onlineDatasets = onlineDatasets == null ? List.of() : List.copyOf(onlineDatasets);
  }
}
