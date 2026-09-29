package io.yak.ops.common.util.metadata;

import java.util.Locale;

/**
 * 物理表 {@code asset_key} 的<b>唯一</b>生成处（ticket 134，plan §2.3 后果 7、§3.2b 硬约束 4）。
 *
 * <p>这段字符串原本长在 data-development 的 {@code TableIdentityResolver.PhysicalTableIdentity} 里。
 * 元数据中心要把物理表登记进同一张目录表，就必须交出<b>逐字相同</b>的键：目录与血缘共用 lineage 那把
 * {@code uk (project_scope_id, asset_key)}，按"更整齐"的格式另起一套不会报错，只会让同一张表在血缘图里
 * 裂成两个节点（plan §2.4.3）。所以是<b>下沉</b>，不是复制一份。
 *
 * <p>入参按源域口径进来（已 trim + 小写，空值转空串），本类不做任何再加工——
 * "顺手规范化"正是逐字节漂移的开始。
 */
public final class PhysicalTableAssetKey {

  private PhysicalTableAssetKey() {}

  /**
   * {@code table:[unresolved:]{dataSourceId}:{db}.{schema}.{tbl}}。
   *
   * @param dataSourceId 已规范化的数据源标识
   * @param databaseName 已规范化的库名，缺省为空串而不是 null
   * @param schemaName 已规范化的模式名，缺省为空串而不是 null
   * @param tableName 已规范化的表名
   */
  public static String of(
      String dataSourceId, String databaseName, String schemaName, String tableName) {
    // Legacy configs without database/schema must never alias a confirmed physical asset.
    String resolution = databaseName.isEmpty() && schemaName.isEmpty() ? "unresolved:" : "";
    return "table:%s%s:%s.%s.%s".formatted(
        resolution, dataSourceId, databaseName, schemaName, tableName);
  }

  /**
   * 列键＝把表键的 {@code table:} 前缀换成 {@code column:} 再接小写列名。
   *
   * <p>这段公式原本长在 data-development 的 {@code DevelopmentSqlLineageService#columnAssetKey}。
   * 采集器要把同一张表的列也登记进目录，就必须交出与血缘<b>逐字相同</b>的列键，否则同一个列在
   * 血缘图与目录里成两个节点，且没有任何 DDL 会报错。故与 {@link #of} 同处，不要再抄第三份。
   */
  public static String columnOf(String tableAssetKey, String columnName) {
    return tableAssetKey.replaceFirst("^table:", "column:")
        + "." + columnName.toLowerCase(Locale.ROOT);
  }
}
