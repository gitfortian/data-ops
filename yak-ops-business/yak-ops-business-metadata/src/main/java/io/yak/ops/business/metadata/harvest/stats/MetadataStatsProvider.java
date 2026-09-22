package io.yak.ops.business.metadata.harvest.stats;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统计层：只补 SPI 目录接口<b>拿不到</b>的那三样——行数、是否分区、最后 DDL 时间。
 *
 * <p>SPI 的 {@code DataSourceTable} 只有 5 个字段（库/schema/表/类型/注释），{@code DataSourceColumn}
 * 只有 9 个，都不含体量信息。这三样是目录按"大表""分区表"筛选的最小集，所以有一层薄 provider，
 * 而不是把 OM 的整套 collector 搬进来。
 *
 * <p>{@code size_bytes} <b>不在这里</b>：存储量归 lifecycle 的每日快照（plan §1.3、§9 T16），
 * 本模块不建 mapper 去查 {@code yak_lc_*}。
 *
 * <p><b>读侧契约</b>：三样全可空，{@code null} 一律表示"未知"而不是 0/false。
 * 把未知写成 0 会让"空表"和"统计失败"在筛选面板上长成同一个东西，而这正是对账最想掩盖的差别。
 */
public interface MetadataStatsProvider {

  /** 本实现是否处理该方言（由 {@link MetadataStatsRegistry} 路由，实现之间不得重叠）。 */
  boolean supports(String databaseType);

  /**
   * 一个 (database, schema) 作用域内全部表的统计，键为<b>小写表名</b>。
   *
   * <p>整轮一次查询而不是每张表一次：几百张表挨个问一遍会把采集的耗时全部花在往返上。
   * 读取失败返回<b>空 Map</b>（= 本作用域统计未知），不抛异常——统计缺失不该让一轮采集报废。
   */
  Map<String, TableStats> load(long dataSourceId, String database, String schema);

  /**
   * @param rowCountApprox 存储引擎给出的<b>估算</b>行数（InnoDB 的 {@code TABLE_ROWS} 本就来自统计信息，
   *                       可能偏差数倍），故属性名与界面文案都带"近似"
   * @param partitioned null = 本方言/引擎读不到分区信息；非 null 才是结论
   * @param lastDdlTime 最后一次结构变更时间；同样 null = 未知
   */
  record TableStats(Long rowCountApprox, Boolean partitioned, LocalDateTime lastDdlTime) {

    public static final TableStats UNKNOWN = new TableStats(null, null, null);

    public boolean known() {
      return rowCountApprox != null || partitioned != null || lastDdlTime != null;
    }
  }
}
