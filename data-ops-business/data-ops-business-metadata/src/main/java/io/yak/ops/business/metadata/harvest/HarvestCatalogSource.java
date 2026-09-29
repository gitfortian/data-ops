package io.yak.ops.business.metadata.harvest;

import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.util.List;

/**
 * 采集器眼中的<b>源侧目录</b>：本模块自己的端口，返回类型一律用 SPI 的
 * {@link DataSourceTable}/{@link DataSourceColumn}（模块 DEPENDENCIES.md："物理采集唯一入口"）。
 *
 * <p>为什么要有一层端口而不是直接调数据源模块：目录要的是"整轮采集"，而连接、插件、能力协商、
 * 项目作用域都在数据源模块那边。端口把这条缝收到一个类的大小，实现换成对方的 {@code api} 包
 * 还是平台 SPI 时，采集逻辑一行不用改。
 *
 * <p><b>分批口径</b>：{@code DataSourceCatalog} 的 {@code listTables} 只有 limit、没有 offset/游标，
 * 所以 plan §0.11 的"≤500 分批"在这里只能落到<b>按作用域分批</b>——一次调用一个
 * (database, schema)，绝不做"一次拿全库清单"。落库侧另有 500 行一条语句的硬分批
 * （{@link AssetUpsertRepository}）。若某个作用域的表数超出一轮可承受的规模，
 * 只能由源侧分页能力补齐，不是这里可以绕过的。
 */
public interface HarvestCatalogSource {

  /** 数据源模块是否装配；false 时采集直接判 FAILED，不影响启动。 */
  boolean available();

  /** 数据源显示名，用于 {@code databaseService} 实体。 */
  String databaseServiceName(long dataSourceId);

  /** 方言名（{@code DataSourceDbType}），{@link io.yak.ops.business.metadata.harvest.stats.MetadataStatsProvider} 按它路由。 */
  String databaseType(long dataSourceId);

  List<String> listDatabases(long dataSourceId);

  List<String> listSchemas(long dataSourceId, String database);

  /**
   * 一个 (database, schema) 作用域内的全部表与视图；{@code schema} 可为 null。
   *
   * <p><b>空集合是断言"这个作用域确实没有表"</b>，会被下一轮当成全库删除的依据；读不出来必须抛，
   * 让采集把该作用域记成"本轮不可判在场性"（ticket 115 靠 {@code failed=true} 跳过）。
   */
  List<DataSourceTable> listTables(long dataSourceId, String database, String schema);

  /**
   * 一张表的列。
   *
   * <p>返回空集合与抛异常<b>都算读取失败</b>（非 JDBC 插件的常态），调用方必须记该表
   * {@code PARTIAL} 而不是"这张表没有列"——后者会让下一轮把全部列判成删除（plan §3.2 边界说明）。
   */
  List<DataSourceColumn> listColumns(long dataSourceId, String database, String schema, String table);
}
