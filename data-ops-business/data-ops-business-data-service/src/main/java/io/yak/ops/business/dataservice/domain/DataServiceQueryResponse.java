package io.yak.ops.business.dataservice.domain;

import java.util.List;
import java.util.Map;

/** Runtime-neutral query result returned by Data Service invocation. */
public record DataServiceQueryResponse(
    List<String> columns,
    List<Map<String, Object>> rows,
    boolean truncated,
    int rowCount,
    long durationMs,
    Integer totalNum,
    Integer pageNum,
    Integer pageSize) {

  public DataServiceQueryResponse(
      List<String> columns,
      List<Map<String, Object>> rows,
      boolean truncated,
      int rowCount,
      long durationMs) {
    this(columns, rows, truncated, rowCount, durationMs, null, null, null);
  }
}
