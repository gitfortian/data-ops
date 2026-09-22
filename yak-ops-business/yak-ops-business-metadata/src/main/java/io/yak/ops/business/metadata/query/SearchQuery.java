package io.yak.ops.business.metadata.query;

import java.util.List;
import java.util.Map;

/** 归一化后的搜索请求（控制器参数在 service 层补齐默认值后进来，接缝以下不再认识 HTTP）。 */
public record SearchQuery(
    String q,
    /** 已解析为类型名的 index 参数；空 = 默认检索面（由 service 决定，含 tableColumn 显式点名）。 */
    List<String> indexTypeNames,
    Map<String, Object> queryFilter,
    Map<String, Object> postFilter,
    String sortField,
    String sortOrder,
    String searchAfter,
    int from,
    int size,
    boolean getHierarchy) {

  public static SearchQuery browse() {
    return new SearchQuery(null, List.of(), Map.of(), Map.of(), null, null, null, 0, 20, false);
  }
}
