package io.yak.ops.business.dataservice.dao.model;

import lombok.Data;

/** Grouped API invocation projection for Data Service overview metrics. */
@Data
public class DataServiceOverviewHotApiPO {
  private Long apiId;
  private String name;
  private String path;
  private Long calls;
  private Long successCalls;
  private Long totalDurationMs;
}
