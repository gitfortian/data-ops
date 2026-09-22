package io.yak.ops.business.metadata.api;

import java.util.List;
import java.util.Map;

/**
 * 目录查询的入参（ticket 118，plan §5.2）。
 *
 * <p>{@code filters} 的键与 HTTP 面 {@code queryFilter} 同族（{@code providerType}/{@code datasourceId}/
 * {@code databaseName}/{@code layerCode}/{@code ownerUser}/{@code entityStatus}/{@code attr.<field>}），
 * 值多值即"任一命中"。<b>不另发明一套筛选 DSL</b>——两套入参形状迟早漂成两种语义。
 *
 * <p>分页用页码而非偏移：出参是框架的 {@code PagingData}，它只有页码这一种形状。
 * 若入参收 offset、出参回 pageNo，凡 offset 不是页大小整数倍的那一页都是假数据。
 *
 * @param q 全文关键词；空 = 浏览
 * @param typeNames 实体类型名（= {@code type_def.type_name}）；空 = 默认检索面
 * @param filters 结构化筛选，键同上
 * @param pageNo 页码，从 1 起
 * @param pageSize 每页条数，钳到检索后端的界
 */
public record EntityQuery(
    String q,
    List<String> typeNames,
    Map<String, Object> filters,
    int pageNo,
    int pageSize) {

  public EntityQuery {
    filters = filters == null ? Map.of() : Map.copyOf(filters);
    typeNames = typeNames == null ? List.of() : List.copyOf(typeNames);
    pageNo = Math.max(1, pageNo);
    pageSize = Math.max(1, pageSize);
  }
}
