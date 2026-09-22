package io.yak.ops.business.metadata.harvest;

import java.util.Locale;
import java.util.Set;

/**
 * 采集时跳过的<b>引擎自带库</b>。
 *
 * <p>为什么在本模块过滤而不是在插件里：JDBC 插件的 {@code GenericJdbcCatalog#includeDatabase}
 * 只判空串，MySQL 系的 {@code listDatabases()} 会把 {@code mysql}/{@code performance_schema}/
 * {@code sys} 一并交出来；Doris 插件自己过滤了三个，别家不保证。<b>一轮采集把上千张引擎内部视图
 * 当成业务表灌进目录</b>，代价是搜索面与治理度量同时失真，且删掉它们要等 GONE 通道。
 *
 * <p>本类是唯一允许出现这些字面量的地方（架构守卫对 {@code INFORMATION_SCHEMA} 有逐文件豁免，
 * 豁免理由见 {@code MetadataLayeringConventionTest}）。
 */
public final class HarvestSystemDatabases {

  private static final Set<String> EXCLUDED =
      Set.of(
          "information_schema",
          "mysql",
          "performance_schema",
          "sys",
          "__internal_schema",
          "pg_catalog",
          "pg_toast",
          "template0",
          "template1");

  private HarvestSystemDatabases() {}

  public static boolean excluded(String database) {
    return database != null
        && EXCLUDED.contains(database.trim().toLowerCase(Locale.ROOT));
  }
}
