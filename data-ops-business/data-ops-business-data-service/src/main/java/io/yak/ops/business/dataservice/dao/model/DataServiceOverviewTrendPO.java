package io.yak.ops.business.dataservice.dao.model;

import lombok.Data;

/** One grouped time bucket for Data Service overview metrics. */
@Data
public class DataServiceOverviewTrendPO {
  private Integer bucketIndex;
  private Long calls;
  private Long successCalls;
  private Long failureCalls;
  private Long totalDurationMs;
}
