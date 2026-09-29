package io.yak.ops.business.dataservice.dao.model;

import lombok.Data;

/** Aggregate projection for Data Service overview metrics. */
@Data
public class DataServiceOverviewSummaryPO {
  private Long apiTotal;
  private Long runningApis;
  private Long totalCalls;
  private Long successCalls;
  private Long totalDurationMs;
  private Long totalRows;
}
