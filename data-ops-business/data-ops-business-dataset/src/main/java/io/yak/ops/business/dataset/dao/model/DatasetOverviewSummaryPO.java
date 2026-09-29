package io.yak.ops.business.dataset.dao.model;

import lombok.Data;

/** Aggregate projection for the Dataset overview query. */
@Data
public class DatasetOverviewSummaryPO {
  private Long datasetCount;
  private Long createdCount;
}
