package io.yak.ops.business.metadata.query;

import java.util.List;
import java.util.Map;

/** 一次检索的后端返回：行 + 类型分桶 + 列命中回账 + 下一游标。 */
public record SearchResult(
    List<Map<String, Object>> rows,
    /** typeId → 命中数（queryFilter 计入、postFilter 不计入的同源聚合）。 */
    Map<Long, Long> typeFacets,
    /** 命中列按父表 id 的计数回账（plan §4.6）；null = 本次不需要。 */
    Map<Long, Long> columnHits,
    String nextSearchAfter,
    /** 本次实际执行的语句（具名占位符、无值），仅 explain=true 时随响应带回。 */
    List<String> renderedSql) {

  public static SearchResult empty() {
    return new SearchResult(List.of(), Map.of(), null, null, List.of());
  }
}
