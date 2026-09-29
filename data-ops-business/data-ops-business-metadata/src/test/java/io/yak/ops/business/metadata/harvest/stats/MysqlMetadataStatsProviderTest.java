package io.yak.ops.business.metadata.harvest.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.harvest.stats.MetadataStatsProvider.TableStats;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * MySQL 统计实现（ticket 114，plan §3.2 属性补全）。
 *
 * <p>统计是<b>可缺项</b>：它唯一的失败方式是"把不知道写成了知道"。所以这里的断言全部围绕三件事——
 * 未知必须是 null 而不是 0、读不到必须咽下去而不是炸掉整轮、方言没跑过就不路由。
 * 第二条候选语句（没有 {@code PARTITIONS} 视图的兼容引擎）尤其要盯着：它少一列，
 * 少读的那一列只能退回未知，不能默认成"不是分区表"。
 */
class MysqlMetadataStatsProviderTest {

  @SuppressWarnings("unchecked")
  private final ObjectProvider<DataSourceExecutionProvider> executionProviders =
      mock(ObjectProvider.class);

  private final FakeExecutor executor = new FakeExecutor();
  private MysqlMetadataStatsProvider provider;

  @BeforeEach
  void wireProvider() {
    DataSourceExecutionProvider executionProvider = ref -> executor;
    when(executionProviders.getIfAvailable()).thenReturn(executionProvider);
    provider = new MysqlMetadataStatsProvider(executionProviders);
  }

  @Test
  void onlyTheMysqlDialectRoutesHereUntilAnotherOneIsActuallyRun() {
    assertThat(provider.supports("MYSQL")).isTrue();
    assertThat(provider.supports(" mysql ")).isTrue();
    // Doris / TiDB / OceanBase 的 information_schema "看着像"，但没跑过一轮就不算支持（plan §9 T12）。
    assertThat(provider.supports("DORIS")).isFalse();
    assertThat(provider.supports("TIDB")).isFalse();
    assertThat(provider.supports("OCEANBASE")).isFalse();
    assertThat(provider.supports("POSTGRESQL")).isFalse();
    assertThat(provider.supports(null)).isFalse();
  }

  @Test
  void theScopeIsSchemaWhenThereIsOneAndDatabaseOtherwise() {
    provider.load(7L, "shop", "report");
    assertThat(executor.requests.get(0).parameters()).containsExactly("report");

    executor.requests.clear();
    provider.load(7L, "shop", "   ");
    assertThat(executor.requests.get(0).parameters()).containsExactly("shop");
  }

  @Test
  void theScopeNameIsAlwaysBoundAndNeverSplicedIntoTheSql() {
    provider.load(7L, "shop; DROP TABLE yak_metadata_asset", "");

    DataSourceSqlRequest request = executor.requests.get(0);
    // 绑定参数让这里不需要 lifecycle 那份库名白名单正则：注入面为零，也就无需维护一张字符集表。
    assertThat(request.sql()).contains("?").doesNotContain("DROP");
    assertThat(request.parameters()).containsExactly("shop; DROP TABLE yak_metadata_asset");
    assertThat(request.maxRows()).isEqualTo(MysqlMetadataStatsProvider.MAX_ROWS);
  }

  @Test
  void aScopeWithoutAnyNameIssuesNoStatementAtAll() {
    assertThat(provider.load(7L, "  ", null)).isEmpty();
    assertThat(provider.load(7L, null, null)).isEmpty();
    assertThat(executor.requests).isEmpty();
  }

  @Test
  void thePartitionSubqueryDecidesYesAndNoAndItsAbsenceDecidesNothing() {
    executor.results = List.of(
        rows(
            row("orders", 12_345L, "2026-08-30 06:30:00", 4L),
            row("audit_log", 0L, null, 0L)));

    Map<String, TableStats> stats = provider.load(7L, "shop", "");

    assertThat(stats.get("orders").partitioned()).isTrue();
    assertThat(stats.get("orders").rowCountApprox()).isEqualTo(12_345L);
    // 零分区就是 false——这一行引擎明确回答了，与"没回答"是两回事。
    assertThat(stats.get("audit_log").partitioned()).isFalse();
    assertThat(stats.get("audit_log").rowCountApprox()).isZero();
    assertThat(stats.get("audit_log").lastDdlTime()).isNull();
  }

  @Test
  void anEngineWithoutThePartitionsViewFallsBackAndLeavesPartitioningUnknown() {
    executor.failFirst = true;
    executor.results = List.of(rows(row("orders", 10L, "2026-08-30 06:30:00")));

    Map<String, TableStats> stats = provider.load(7L, "shop", "");

    assertThat(executor.requests).hasSize(2);
    assertThat(executor.requests.get(0).sql()).contains("PARTITIONS");
    assertThat(executor.requests.get(1).sql()).doesNotContain("PARTITIONS");
    assertThat(stats.get("orders").partitioned()).isNull();
    assertThat(stats.get("orders").rowCountApprox()).isEqualTo(10L);
    assertThat(executor.closed).isEqualTo(2);
  }

  @Test
  void everyCandidateFailingDegradesToNoStatsInsteadOfFailingTheRound() {
    executor.throwAll = new IllegalStateException("目录不可达");

    assertThat(provider.load(7L, "shop", "")).isEmpty();
    assertThat(executor.requests).hasSize(MysqlMetadataStatsProvider.STATEMENTS.size());
  }

  @Test
  void aMissingExecutionCapabilityIsSilenceNotAnError() {
    when(executionProviders.getIfAvailable()).thenReturn(null);

    assertThat(new MysqlMetadataStatsProvider(executionProviders).load(7L, "shop", "")).isEmpty();
    assertThat(executor.requests).isEmpty();
  }

  @Test
  void timestampsTolerateTheDriversTextualFormsAndRejectEverythingElse() {
    executor.results = List.of(
        rows(
            row("plain", 1L, "2026-08-30 06:30:00"),
            row("fraction", 1L, "2026-08-30 06:30:00.123456"),
            row("trailing_zero", 1L, "2026-08-30 06:30:00.0"),
            row("garbage", 1L, "30.08.2026")));

    Map<String, TableStats> stats = provider.load(7L, "shop", "");
    // 驱动把时间列统一交回字符串（JdbcDataSourceSqlExecutor#normalizeValue），所以只能按文本解析。
    assertThat(stats.get("plain").lastDdlTime()).isEqualTo(LocalDateTime.of(2026, 8, 30, 6, 30, 0));
    assertThat(stats.get("fraction").lastDdlTime())
        .isEqualTo(LocalDateTime.of(2026, 8, 30, 6, 30, 0, 123_456_000));
    assertThat(stats.get("trailing_zero").lastDdlTime())
        .isEqualTo(LocalDateTime.of(2026, 8, 30, 6, 30, 0));
    // 解析不出的时间退回未知，而不是把整轮采集带下去。
    assertThat(stats.get("garbage").lastDdlTime()).isNull();
  }

  @Test
  void rowCountsSurviveBothNumericAndTextualEngineReplies() {
    executor.results = List.of(
        rows(
            row("as_number", 9_000L, null, 0L),
            row("as_text", "9000", null, 0L),
            row("as_garbage", "约九千", null, 0L)));

    Map<String, TableStats> stats = provider.load(7L, "shop", "");

    assertThat(stats.get("as_number").rowCountApprox()).isEqualTo(9_000L);
    assertThat(stats.get("as_text").rowCountApprox()).isEqualTo(9_000L);
    // 读不懂的行数退回未知：猜一个 0 会被治理面板画成"这张表可以删了"。
    assertThat(stats.get("as_garbage").rowCountApprox()).isNull();
  }

  @Test
  void keysAreNormalizedSoEngineCaseFoldingCannotSplitOneTableInTwo() {
    executor.results = List.of(rows(row("Orders", 1L, null, 0L), row(" ORDERS ", 2L, null, 0L)));

    Map<String, TableStats> stats = provider.load(7L, "shop", "");

    assertThat(stats).containsOnlyKeys("orders");
    // 同一张表被引擎写成两种大小写时，先到的那份算数，而不是后到的覆盖它。
    assertThat(stats.get("orders").rowCountApprox()).isEqualTo(1L);
  }

  @Test
  void aRowWithoutATableNameIsNotCountedAsATable() {
    executor.results = List.of(rows(row(null, 5L, null, 1L), row("   ", 5L, null, 1L)));

    assertThat(provider.load(7L, "shop", "")).isEmpty();
  }

  @Test
  void aTruncatedReplyStillServesTheTablesThatDidComeBack() {
    executor.truncated = true;
    executor.results = List.of(rows(row("orders", 1L, null, 0L)));

    // 截断只记日志：超出上限的表本轮按未知，已读回的部分照常可用——统计不该拖累实体登记。
    assertThat(provider.load(7L, "shop", "")).containsOnlyKeys("orders");
  }

  @Test
  void theStatementsAreScopedToTheSchemaAndSkipSystemViews() {
    List<String> statements = MysqlMetadataStatsProvider.STATEMENTS;
    assertThat(statements).hasSize(2);
    for (String sql : statements) {
      assertThat(sql).startsWith("SELECT ");
      assertThat(sql).contains("TABLE_SCHEMA = ?");
      assertThat(sql).contains("TABLE_TYPE <> 'SYSTEM VIEW'");
    }
  }

  @Test
  void unknownStatsAreKnownToBeUnknown() {
    assertThat(TableStats.UNKNOWN.known()).isFalse();
    assertThat(new TableStats(null, null, null).known()).isFalse();
    // 0 行、false、null 时间都是"引擎答了"，与 UNKNOWN 不是一类。
    assertThat(new TableStats(0L, null, null).known()).isTrue();
    assertThat(new TableStats(null, false, null).known()).isTrue();
  }

  private static List<Object> row(Object... cells) {
    return Arrays.asList(cells);
  }

  @SafeVarargs
  private static List<List<Object>> rows(List<Object>... cells) {
    return List.copyOf(Arrays.asList(cells));
  }

  /** 记请求、按脚本回应答复的假执行器；关闭也计数，确保 try-with-resources 真的用上了。 */
  private static final class FakeExecutor implements DataSourceSqlExecutor {
    final List<DataSourceSqlRequest> requests = new ArrayList<>();
    List<List<List<Object>>> results = List.of();
    boolean failFirst;
    boolean truncated;
    RuntimeException throwAll;
    int closed;

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) {
      requests.add(request);
      if (throwAll != null) {
        throw throwAll;
      }
      if (failFirst && requests.size() == 1) {
        throw new IllegalStateException("该引擎没有 PARTITIONS 视图");
      }
      int index = results.isEmpty() ? -1 : Math.min(requests.size() - 1, results.size() - 1);
      return DataSourceSqlResult.resultSet(
          List.of(), index < 0 ? List.of() : results.get(index), truncated);
    }

    @Override
    public void close() {
      closed++;
    }
  }
}
