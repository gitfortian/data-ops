package io.yak.ops.business.metadata.harvest.stats;

import io.yak.ops.business.metadata.harvest.stats.MetadataStatsProvider.TableStats;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * MySQL 系统计实现。
 *
 * <p><b>一期只此一家，且只按 {@code MYSQL} 路由</b>（plan §9 T12）：TiDB / OceanBase / Doris 的
 * {@code information_schema} 形状大同小异，但"看着像"不等于"跑过"——把它们写进 {@link #supports} 就是
 * 把未验证的方言标成已支持，正是本方案反复要避免的那类假承诺。真跑过一轮再逐个加名字。
 *
 * <p>只读三列：{@code TABLE_ROWS}（引擎估算，故属性叫 rowCountApprox）、{@code CREATE_TIME}
 * （MySQL 在 ALTER 重建表时刷新它，所以它是"最后 DDL 时间"的可用代理，而非精确值）、
 * {@code information_schema.PARTITIONS} 的分区计数。<b>不选任何字节量列</b>：
 * 表大小与索引大小归 lifecycle 的每日快照，两处各采一份迟早会给出两个答案
 * （哪两列被禁，由架构守卫按字面量记着）。
 *
 * <p>SQL 形态沿用 lifecycle 已跑通的"候选语句按序尝试"（{@code StorageSnapshotService.statementsFor()}）。
 * 一处差异是本实现用<b>绑定参数</b>传库名，所以不需要那边为字符串拼接补的库名白名单正则
 * （见 {@link #STATEMENTS} 上方注释）；候选的差别只是引擎有无 {@code PARTITIONS} 视图。
 */
@Slf4j
@Component
public class MysqlMetadataStatsProvider implements MetadataStatsProvider {

  /**
   * 候选语句，按序尝试、首个可用即采纳。
   *
   * <p>库名走 {@code ?} 绑定而不是拼接：注入面为零，也就不依赖任何字符集白名单。
   * 第二条去掉分区子查询，给没有 {@code information_schema.PARTITIONS} 的兼容引擎兜底——
   * 此时"是否分区"退回未知（null），而不是伪造一个 false。
   */
  static final List<String> STATEMENTS =
      List.of(
          "SELECT t.TABLE_NAME, t.TABLE_ROWS, t.CREATE_TIME,"
              + " (SELECT COUNT(*) FROM information_schema.PARTITIONS p"
              + "   WHERE p.TABLE_SCHEMA = t.TABLE_SCHEMA"
              + "     AND p.TABLE_NAME = t.TABLE_NAME"
              + "     AND p.PARTITION_NAME IS NOT NULL)"
              + " FROM information_schema.TABLES t"
              + " WHERE t.TABLE_SCHEMA = ? AND t.TABLE_TYPE <> 'SYSTEM VIEW'",
          "SELECT t.TABLE_NAME, t.TABLE_ROWS, t.CREATE_TIME"
              + " FROM information_schema.TABLES t"
              + " WHERE t.TABLE_SCHEMA = ? AND t.TABLE_TYPE <> 'SYSTEM VIEW'");

  /** 单作用域最多读回的表数；超出即视为统计不完整，只记日志不静默截断。 */
  static final int MAX_ROWS = 5_000;

  private static final int TIMEOUT_SECONDS = 15;
  private static final String MYSQL = "MYSQL";

  /** 驱动把时间列统一交回成字符串（{@code JdbcDataSourceSqlExecutor#normalizeValue}），故按文本解析。 */
  private static final DateTimeFormatter TIMESTAMP =
      new DateTimeFormatterBuilder()
          .appendPattern("yyyy-MM-dd HH:mm:ss")
          .optionalStart()
          .appendLiteral('.')
          .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, false)
          .optionalEnd()
          .toFormatter(Locale.ROOT);

  private final ObjectProvider<DataSourceExecutionProvider> executionProviders;

  public MysqlMetadataStatsProvider(ObjectProvider<DataSourceExecutionProvider> executionProviders) {
    this.executionProviders = executionProviders;
  }

  @Override
  public boolean supports(String databaseType) {
    return databaseType != null && MYSQL.equalsIgnoreCase(databaseType.trim());
  }

  @Override
  public Map<String, TableStats> load(long dataSourceId, String database, String schema) {
    DataSourceExecutionProvider provider = executionProviders.getIfAvailable();
    String scope = schema == null || schema.isBlank() ? database : schema;
    if (provider == null || scope == null || scope.isBlank()) {
      return Map.of();
    }
    List<Object> parameters = List.of(scope.trim());
    RuntimeException lastFailure = null;
    for (String sql : STATEMENTS) {
      try (DataSourceSqlExecutor executor = provider.open(String.valueOf(dataSourceId))) {
        DataSourceSqlResult result =
            executor.execute(new DataSourceSqlRequest(sql, MAX_ROWS, TIMEOUT_SECONDS, parameters));
        if (result.truncated()) {
          log.warn(
              "统计结果被截断（作用域内表数 > {}），超出部分按未知处理 dataSource={} scope={}",
              MAX_ROWS,
              dataSourceId,
              scope);
        }
        return toStats(result);
      } catch (RuntimeException e) {
        lastFailure = e;
      }
    }
    log.warn(
        "统计读取在全部候选语句上失败，本轮按未知处理 dataSource={} scope={}: {}",
        dataSourceId,
        scope,
        lastFailure == null ? "no candidate" : lastFailure.getMessage());
    return Map.of();
  }

  private Map<String, TableStats> toStats(DataSourceSqlResult result) {
    Map<String, TableStats> stats = new LinkedHashMap<>();
    for (List<Object> row : result.rows()) {
      String table = text(row, 0);
      if (table == null) {
        continue;
      }
      stats.putIfAbsent(
          table.toLowerCase(Locale.ROOT),
          new TableStats(number(row, 1), partitioned(row), timestamp(row, 2)));
    }
    return stats;
  }

  /** 列不存在（第二条候选语句）或值为 NULL 都退回 null=未知；这里唯一能区分两者的就是列数。 */
  private static Boolean partitioned(List<Object> row) {
    Long count = number(row, 3);
    return count == null ? null : count > 0L;
  }

  private static String text(List<Object> row, int index) {
    Object value = cell(row, index);
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static Long number(List<Object> row, int index) {
    Object value = cell(row, index);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value == null) {
      return null;
    }
    try {
      return Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static LocalDateTime timestamp(List<Object> row, int index) {
    String text = text(row, index);
    if (text == null) {
      return null;
    }
    try {
      return LocalDateTime.parse(text, TIMESTAMP);
    } catch (RuntimeException e) {
      // 引擎给回一个解析不出的时间格式时，宁可退回未知也不要中断整轮采集。
      return null;
    }
  }

  private static Object cell(List<Object> row, int index) {
    return index < row.size() ? row.get(index) : null;
  }
}
