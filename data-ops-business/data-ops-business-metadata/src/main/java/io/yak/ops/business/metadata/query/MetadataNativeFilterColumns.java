package io.yak.ops.business.metadata.query;

import java.util.Map;
import java.util.Set;

/**
 * {@code queryFilter}/{@code postFilter} 允许直接落在目录<b>固有列</b>上的键（落点三态之②，
 * plan §4.5/§2.3；attr.* 走槽位，是落点之①）。
 *
 * <p>这张表同时是<b>白名单</b>：不在其中的裸键（无 {@code attr.} 前缀）一律 49024 拒，
 * 杜绝"看起来能筛其实全表扫"。{@code domainId}/{@code tagged}/{@code hasSummary} 没有等值列，
 * 由 builder 特殊处理，故不出现在列映射里。
 */
public final class MetadataNativeFilterColumns {

  /** 裸键 → 目录列（带表别名前缀）。值一律具名绑定。 */
  static final Map<String, String> COLUMNS = Map.of(
      "providerType", "a.provider_type",
      "datasourceId", "a.data_source_id",
      // 与列命中 rollup 的 GROUP BY 同一列：点"命中 N 列"下钻到该表的列，靠这个键而不是新端点。
      "parentAssetId", "a.parent_asset_id",
      "databaseName", "a.database_name",
      "layerCode", "a.layer_code",
      "owner", "a.owner_user",
      "entityStatus", "a.entity_status");

  /** 由 builder 特殊处理的键（FIND_IN_SET / EXISTS / 类型解析）。 */
  static final Set<String> SPECIAL_KEYS = Set.of("typeName", "domainId", "tagged", "hasSummary");

  static String columnFor(String key) {
    return COLUMNS.get(key);
  }

  private MetadataNativeFilterColumns() {}
}
