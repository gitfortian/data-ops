package io.yak.ops.business.metadata.harvest;

import io.yak.ops.business.metadata.dao.mapper.MdCollectRunMapper;
import io.yak.ops.business.metadata.dao.model.CatalogPresenceRow;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneRow;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.PresenceScan;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.ScopeOutcome;
import io.yak.ops.business.metadata.harvest.MetadataPresenceService.PresenceOutcome;
import io.yak.ops.business.metadata.dao.model.MdCollectRunPO;
import io.yak.ops.common.enums.metadata.MetadataEnums.RunStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GONE 判定的四道闸（ticket 115，plan §3.4）。
 *
 * <p>这是采集侧唯一会造成不可逆损失的环节，所以每条断言问的都是<b>"有没有少删"</b>而不是
 * "有没有多删"：多删一次就是整库目录凭空消失，而少删一轮下一轮会自己补上。
 * 因此几乎所有用例都有一条公共断言 {@code repository.gone} 为空——它比结论本身更重要。
 *
 * <p>不连库、不起 Spring：在场集由假仓库交出，软删被记成命令。真实 SQL 的形状
 * （{@code source_type='METADATA'} 这类硬边界、SET 只有两列、全表没有一条物理 DELETE）
 * 由 {@code CatalogPresenceStatementTest} 与 pymysql 预演守着。
 *
 * <p>各用例的缺席比例都刻意留在熔断阈值之下（除非用例本身就是演熔断），否则断言会被第三道闸
 * 抢先拦下，测的就不是被闸本身。
 */
class MetadataPresenceServiceTest {

  private static final Long PROJECT = 42L;
  private static final String SOURCE_ID = "7";
  private static final Long RUN_ID = 501L;
  /** 上一有效轮的开始时刻：缺席判据的分界线。 */
  private static final LocalDateTime PREVIOUS_ROUND = LocalDateTime.of(2026, 9, 1, 3, 0);
  /** 本轮时刻。 */
  private static final LocalDateTime THIS_ROUND = LocalDateTime.of(2026, 9, 2, 3, 0);
  /** 早于上一轮开始 → 上一轮和本轮都没被刷到 → 缺席满两轮。 */
  private static final LocalDateTime STALE = LocalDateTime.of(2026, 8, 31, 3, 0);

  private final FakeCatalogRepository repository = new FakeCatalogRepository();
  private final MdCollectRunMapper runMapper = mock(MdCollectRunMapper.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<CatalogGraphRevocation> revocations = mock(ObjectProvider.class);
  private final RecordingRevocation revocation = new RecordingRevocation();

  private MetadataPresenceService service() {
    return new MetadataPresenceService(repository, runMapper, revocations, 0.3);
  }

  private PresenceOutcome evaluate(PresenceFacts facts) {
    return service().evaluateAndApply(facts);
  }

  @Test
  void effectivePolicyExposesTheConfiguredGlobalThresholdNotTheJobColumns() {
    assertThat(service().effectivePolicy().collapseThresholdPct()).isEqualTo(30);
    assertThat(service().effectivePolicy().missingRounds()).isEqualTo(2);

    MetadataPresenceService customized =
        new MetadataPresenceService(repository, runMapper, revocations, 0.45);
    assertThat(customized.effectivePolicy().collapseThresholdPct()).isEqualTo(45);
    assertThat(customized.effectivePolicy().missingRounds()).isEqualTo(2);
  }

  @Test
  void anEmptySeenSetIsRefusedBeforeTheScanEvenHappens() {
    repository.scan = List.of(tableRow("table:7:shop..orders", "orders", STALE));

    PresenceOutcome outcome = evaluate(facts().build());

    // 连接器崩了与"什么都没发现"无从区分，所以空集不许解释成全量消失（plan §3.4）。
    assertThat(outcome.goneCount()).isZero();
    assertThat(outcome.suspect()).isFalse();
    assertThat(outcome.reason()).contains("空集不得解释为全量消失");
    assertThat(outcome.statusOn(RunStatus.SUCCESS)).isEqualTo(RunStatus.SUCCESS);
    assertThat(repository.scanCalls).as("第一道闸应当在读在场集之前就拦住").isZero();
    assertThat(repository.gone).isEmpty();
  }

  @Test
  void aFirstRoundWithNothingToCompareAgainstJudgesNothingGone() {
    repository.scan = List.of(tableRow("table:7:shop..orders", "orders", STALE));

    PresenceOutcome outcome =
        evaluate(facts().seen("table:7:shop..orders").previousRound(null).build());

    // 任务首轮与前几轮全 FAILED 都落在这一条：两种都不构成删除证据。
    assertThat(outcome.reason()).contains("无有效上一轮");
    assertThat(repository.scanCalls).isZero();
    assertThat(repository.gone).isEmpty();
  }

  @Test
  void oneRoundMissingIsNotAbsentWhileTwoRoundsAre() {
    repository.scan =
        List.of(
            tableRow("table:7:shop..fresh", "fresh", THIS_ROUND),
            tableRow("table:7:shop..one_round_old", "one_round_old", PREVIOUS_ROUND.plusMinutes(1)),
            tableRow("table:7:shop..gone", "gone", STALE));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").scope("shop", "").build());

    // 边界取"上一轮开始时刻"：正好落在它之后说明上一轮还刷到过，只有更早才算缺席满两轮。
    assertThat(outcome.goneKeys()).containsExactly("table:7:shop..gone");
    assertThat(outcome.presentTables()).isEqualTo(3);
    assertThat(outcome.scanned()).isEqualTo(3);
    assertThat(outcome.goneTables()).isEqualTo(1);
    assertThat(repository.gone).singleElement().satisfies(command -> {
      assertThat(command.dryRun()).isFalse();
      assertThat(command.projectId()).isEqualTo(PROJECT);
      assertThat(command.collectRunId()).isEqualTo(RUN_ID);
      assertThat(command.operator()).isEqualTo("root");
      assertThat(command.goneAt()).isEqualTo(THIS_ROUND);
      assertThat(command.rows()).extracting(row -> row.row().getAssetKey())
          .containsExactly("table:7:shop..gone");
      // type_name 随候选行交出：共表里的 asset_type 是 lineage 的图类型，不是目录的元模型类型。
      assertThat(command.rows()).extracting(GoneRow::typeName).containsExactly("table");
    });
  }

  @Test
  void aRowThatWasNeverCollectedIsNotAllowedToVanish() {
    repository.scan =
        List.of(
            tableRow("table:7:shop..no_stamp", "no_stamp", null),
            tableRow("table:7:shop..gone", "gone", STALE));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").scope("shop", "").build());

    // 没有任何在场证据的行不该消失：写侧漏刷一次时间戳，不该被解释成"源侧删了它"。
    assertThat(outcome.goneKeys()).containsExactly("table:7:shop..gone");
  }

  @Test
  void aScopeWhoseTableListFailedToReadCannotHaveAnyOfItsTablesDeleted() {
    repository.scan =
        List.of(
            tableRow("table:7:shop..a", "a", STALE),
            tableRow("table:7:shop..b", "b", THIS_ROUND),
            tableRow("table:7:shop..c", "c", THIS_ROUND),
            warehouseRow("table:7:warehouse..d", "d", STALE),
            warehouseRow("table:7:warehouse..e", "e", STALE));

    PresenceOutcome outcome =
        evaluate(
            facts().seen("whatever").scope("shop", "").failedScope("warehouse", "").build());

    // 一轮超时读不到 warehouse 的清单 ≠ warehouse 的表都被删了。
    assertThat(outcome.goneKeys()).containsExactly("table:7:shop..a");
    assertThat(outcome.presentTables()).isEqualTo(3);
  }

  @Test
  void columnsOfAPartiallyReadScopeAreSkippedWhileItsTablesAreStillJudged() {
    repository.scan =
        List.of(
            tableRow("table:7:shop..t1", "t1", STALE),
            tableRow("table:7:shop..t2", "t2", THIS_ROUND),
            tableRow("table:7:shop..t3", "t3", THIS_ROUND),
            columnRow("column:7:shop..t1.id", "t1", STALE));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").partialScope("shop", "").build());

    // 列读不全时"少了一列"可能只是读取失败；表级证据不受影响。
    assertThat(outcome.goneTables()).isEqualTo(1);
    assertThat(outcome.goneColumns()).isZero();
    assertThat(outcome.goneKeys()).containsExactly("table:7:shop..t1");
  }

  @Test
  void columnsOfATableThatIsGoneAreNotListedOneByOne() {
    repository.scan =
        List.of(
            tableRow("table:7:shop..orders", "orders", STALE),
            columnRow("column:7:shop..orders.id", "orders", STALE),
            columnRow("column:7:shop..orders.amount", "orders", STALE),
            tableRow("table:7:shop..live", "live", THIS_ROUND),
            columnRow("column:7:shop..live.id", "live", STALE));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").scope("shop", "").build());

    // 祖先覆盖跳过：orders 的列必然跟着表一起不在，逐条记流水只会把一次真实变更淹在 12.6 倍噪音里；
    // live 还在场，它的缺席列才是各自独立的一件事。
    assertThat(outcome.goneKeys())
        .containsExactly("table:7:shop..orders", "column:7:shop..live.id");
    assertThat(outcome.goneTables()).isEqualTo(1);
    assertThat(outcome.goneColumns()).isEqualTo(1);
  }

  @Test
  void tablesExcludedByTheJobPatternAreNotCandidatesForGone() {
    repository.scan =
        List.of(
            tableRow("table:7:shop..orders", "orders", STALE),
            tableRow("table:7:shop..products", "products", STALE));

    PresenceOutcome outcome =
        evaluate(facts().seen("whatever").pattern("order%").scope("shop", "").build());

    // 任务改成只要 order% 之后，products 是"没被采"，不是"被删了"——否则配置一改就批量误删。
    assertThat(outcome.goneKeys()).containsExactly("table:7:shop..orders");
    assertThat(outcome.presentTables()).isEqualTo(1);
  }

  @Test
  void aSchemaNarrowedJobLeavesRowsOfOtherSchemasAlone() {
    repository.scan =
        List.of(
            row("table:7:dw.report.a", "TABLE", "dw", "report", "a", STALE),
            row("table:7:dw.report.b", "TABLE", "dw", "report", "b", THIS_ROUND),
            row("table:7:dw.report.c", "TABLE", "dw", "report", "c", THIS_ROUND),
            row("table:7:dw.fact.d", "TABLE", "dw", "fact", "d", STALE));

    PresenceOutcome outcome =
        evaluate(facts().seen("whatever").schemaName("report").scope("dw", "report").build());

    // 任务只采 report 模式时，fact 模式里的行本轮根本没进在场声明，缺席再久也不算"被删了"。
    assertThat(outcome.goneKeys()).containsExactly("table:7:dw.report.a");
    assertThat(outcome.presentTables()).isEqualTo(3);
  }

  @Test
  void databaseRowsAreOnlyJudgeableWhenTheWholeDatabaseListWasEnumerated() {
    repository.scan =
        List.of(
            databaseRow("database:7:shop", "shop", STALE),
            databaseRow("database:7:legacy", "legacy", STALE));

    PresenceOutcome enumerated = evaluate(facts().seen("datasource:7").build());
    assertThat(enumerated.goneKeys()).containsExactly("database:7:shop", "database:7:legacy");
    assertThat(enumerated.goneDatabases()).isEqualTo(2);

    repository.gone.clear();
    PresenceOutcome narrowed = evaluate(facts().seen("datasource:7").databaseName("shop").build());

    // 点名单个库时本轮根本没看过库清单，"别的库消失了"这个结论无从下。
    assertThat(narrowed.goneKeys()).isEmpty();
    assertThat(narrowed.goneDatabases()).isZero();
    assertThat(repository.gone).isEmpty();
  }

  @Test
  void engineOwnedDatabasesAreNeverMarkedGone() {
    repository.scan =
        List.of(
            databaseRow("database:7:information_schema", "information_schema", STALE),
            databaseRow("database:7:__internal_schema", "__internal_schema", STALE),
            databaseRow("database:7:shop", "shop", STALE));

    PresenceOutcome outcome = evaluate(facts().seen("datasource:7").build());

    // 排除表与采集侧共用同一份：上一轮本就没采系统库，这一轮就会把它们全判成"消失"。
    assertThat(outcome.goneKeys()).containsExactly("database:7:shop");
  }

  @Test
  void aCollapsedRoundIsFusedAndNothingIsDeleted() {
    List<CatalogPresenceRow> rows = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (int index = 0; index < 10; index++) {
      rows.add(tableRow("table:7:shop..t" + index, "t" + index, index < 5 ? STALE : THIS_ROUND));
    }
    repository.scan = rows;
    for (int index = 5; index < 10; index++) {
      seen.add("table:7:shop..t" + index);
    }

    PresenceOutcome outcome = evaluate(facts().seenKeys(seen).scope("shop", "").build());

    // 单轮 5/10 缺席超过 30% 阈值：一次连接超时就能让"整库消失"看起来成立，所以整轮熔断。
    assertThat(outcome.suspect()).isTrue();
    assertThat(outcome.statusOn(RunStatus.SUCCESS)).isEqualTo(RunStatus.SUSPECT);
    assertThat(outcome.reason()).contains("坍塌阈值").contains("本轮不落任何 GONE");
    assertThat(outcome.goneCount()).isZero();
    // 候选数照实报出来：运维要看得见"可疑在哪"，才判得出是真删了还是采崩了。
    assertThat(outcome.goneTables()).isEqualTo(5);
    assertThat(outcome.presentTables()).isEqualTo(10);
    assertThat(repository.gone).as("熔断时那条写路径根本不该被调用").isEmpty();
    assertThat(revocation.keys).as("可疑的缺席不构成从血缘图上摘节点的理由").isNull();
  }

  @Test
  void aSingleAbsentTableOnASmallSourceStillLands() {
    repository.scan =
        List.of(
            tableRow("table:7:shop..dropped", "dropped", STALE),
            tableRow("table:7:shop..keeper", "keeper", THIS_ROUND));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").scope("shop", "").build());

    // 两表的库删一张就是 50%：没有最小样本，小库的 GONE 永远落不了地，目录里留下永久僵尸。
    assertThat(outcome.suspect()).isFalse();
    assertThat(outcome.goneKeys()).containsExactly("table:7:shop..dropped");
  }

  @Test
  void aCollapseBelowTheThresholdIsApplied() {
    List<CatalogPresenceRow> rows = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (int index = 0; index < 10; index++) {
      rows.add(tableRow("table:7:shop..t" + index, "t" + index, index == 0 ? STALE : THIS_ROUND));
    }
    repository.scan = rows;
    for (int index = 1; index < 10; index++) {
      seen.add("table:7:shop..t" + index);
    }

    PresenceOutcome outcome = evaluate(facts().seenKeys(seen).scope("shop", "").build());

    assertThat(outcome.suspect()).isFalse();
    assertThat(outcome.goneCount()).isEqualTo(1);
    assertThat(outcome.goneKeys()).containsExactly("table:7:shop..t0");
    assertThat(outcome.reason()).isNull();
  }

  @Test
  void aScanThatHitTheLimitHasAnUntrustworthyDenominatorAndIsFused() {
    repository.scan = List.of(tableRow("table:7:shop..orders", "orders", STALE));
    repository.scanLimit = 1;

    PresenceOutcome outcome = evaluate(facts().seen("whatever").scope("shop", "").build());

    // 读不全时缺席/在场比例偏小，熔断会因"证据不足"放过一次真坍塌——所以触顶即 SUSPECT。
    assertThat(outcome.suspect()).isTrue();
    assertThat(outcome.reason()).contains("分母不可信");
    assertThat(outcome.scanned()).isEqualTo(1);
    assertThat(repository.gone).isEmpty();
  }

  @Test
  void aDryRunNamesEveryCandidateAndWritesNothing() {
    repository.scan =
        List.of(
            tableRow("table:7:shop..gone", "gone", STALE),
            tableRow("table:7:shop..t1", "t1", THIS_ROUND),
            tableRow("table:7:shop..t2", "t2", THIS_ROUND),
            tableRow("table:7:shop..t3", "t3", THIS_ROUND),
            columnRow("column:7:shop..t1.id", "t1", STALE));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").dryRun().scope("shop", "").build());

    // 预演的价值在于"删之前先看见会删谁"，代价不能是先把实体删了。
    assertThat(outcome.goneCount()).isZero();
    assertThat(outcome.goneKeys()).containsExactly("table:7:shop..gone", "column:7:shop..t1.id");
    assertThat(outcome.reason()).contains("dry-run");
    assertThat(repository.gone).isEmpty();
    assertThat(revocation.keys).as("预演更不该动血缘图").isNull();
  }

  @Test
  void aCleanRoundWithNothingAbsentWritesNoStatement() {
    repository.scan = List.of(tableRow("table:7:shop..orders", "orders", THIS_ROUND));

    PresenceOutcome outcome =
        evaluate(facts().seen("table:7:shop..orders").scope("shop", "").build());

    assertThat(outcome.reason()).isNull();
    assertThat(outcome.suspect()).isFalse();
    assertThat(outcome.presentTables()).isEqualTo(1);
    assertThat(repository.gone).isEmpty();
    assertThat(revocation.keys).isNull();
  }

  @Test
  void revocationRunsOnlyAfterAConfirmedSoftDelete() {
    when(revocations.getIfAvailable()).thenReturn(revocation);
    repository.scan =
        List.of(
            tableRow("table:7:shop..a", "a", STALE),
            tableRow("table:7:shop..b", "b", STALE),
            tableRow("table:7:shop..c", "c", THIS_ROUND),
            tableRow("table:7:shop..d", "d", THIS_ROUND),
            tableRow("table:7:shop..e", "e", THIS_ROUND),
            tableRow("table:7:shop..f", "f", THIS_ROUND),
            tableRow("table:7:shop..g", "g", THIS_ROUND));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").scope("shop", "").build());

    assertThat(outcome.goneCount()).isEqualTo(2);
    assertThat(revocation.keys).containsExactly("table:7:shop..a", "table:7:shop..b");
  }

  @Test
  void anAbsentRevocationIsLoggedRatherThanFaked() {
    when(revocations.getIfAvailable()).thenReturn(null);
    repository.scan = List.of(tableRow("table:7:shop..gone", "gone", STALE));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").scope("shop", "").build());

    // ticket 113 至今没有撤销入口：软删照落（实体确实不在源侧），图上残留记日志而不是谎报已撤销。
    assertThat(outcome.goneCount()).isEqualTo(1);
    assertThat(repository.gone).hasSize(1);
  }

  @Test
  void aFailedRevocationDoesNotRollBackTheSoftDelete() {
    when(revocations.getIfAvailable())
        .thenReturn(
            assetKeys -> {
              throw new IllegalStateException("模拟血缘侧不可达");
            });
    repository.scan = List.of(tableRow("table:7:shop..gone", "gone", STALE));

    PresenceOutcome outcome = evaluate(facts().seen("whatever").scope("shop", "").build());

    // 撤销失败是可见性问题，误删才是数据问题：不把一次图侧故障解释成"它其实还在"。
    assertThat(outcome.goneCount()).isEqualTo(1);
    assertThat(outcome.suspect()).isFalse();
    assertThat(repository.gone).hasSize(1);
  }

  @Test
  void collectingWithoutColumnsLeavesColumnRowsAlone() {
    repository.scan = List.of(columnRow("column:7:shop..orders.id", "orders", STALE));

    PresenceOutcome outcome =
        evaluate(facts().seen("table:7:shop..orders").noColumns().scope("shop", "").build());

    // 本轮没读列，就没有"列消失"这件事可说——与 ticket 114 的 PARTIAL 纪律同一面。
    assertThat(outcome.goneKeys()).isEmpty();
    assertThat(outcome.goneColumns()).isZero();
    assertThat(repository.gone).isEmpty();
  }

  @Test
  void thePreviousRoundBoundaryComesBackFromTheRunHistoryInOneQuery() {
    MdCollectRunPO previous = new MdCollectRunPO();
    previous.setStartedAt(PREVIOUS_ROUND);
    when(runMapper.selectOne(any())).thenReturn(previous);
    assertThat(service().previousRoundStartedAt(PROJECT, 7L, RUN_ID)).isEqualTo(PREVIOUS_ROUND);

    when(runMapper.selectOne(any())).thenReturn(null);
    assertThat(service().previousRoundStartedAt(PROJECT, 7L, RUN_ID)).isNull();

    // 一次判定一条查询：它的结果只用作"缺席满两轮"的边界时刻，没有别的读取路径可以绕过它。
    // 查询本身那四条边界（排除 dry-run、只认 SUCCESS/SUSPECT、排除本轮、倒序取一条）
    // 写在 wrapper 上，运行期无从断言（MP 渲染时才解析列名），故由 CatalogPresenceStatementTest 锁文本。
    verify(runMapper, times(2)).selectOne(any());
  }

  private FactsBuilder facts() {
    return new FactsBuilder();
  }

  /** 一轮判定交给服务的全部外部事实；测试只改被关心的那几个字段。 */
  private static final class FactsBuilder {
    private final Set<String> seenKeys = new LinkedHashSet<>();
    private final List<ScopeOutcome> scopes = new ArrayList<>();
    private LocalDateTime previousRound = PREVIOUS_ROUND;
    private boolean dryRun;
    private boolean collectColumns = true;
    private String databaseName;
    private String schemaName;
    private String tablePattern;

    FactsBuilder seen(String... keys) {
      seenKeys.addAll(List.of(keys));
      return this;
    }

    FactsBuilder seenKeys(Set<String> keys) {
      seenKeys.addAll(keys);
      return this;
    }

    FactsBuilder scope(String database, String schema) {
      scopes.add(new ScopeOutcome(database, schema, 4, 0, false));
      return this;
    }

    FactsBuilder partialScope(String database, String schema) {
      scopes.add(new ScopeOutcome(database, schema, 4, 1, false));
      return this;
    }

    FactsBuilder failedScope(String database, String schema) {
      scopes.add(new ScopeOutcome(database, schema, 0, 0, true));
      return this;
    }

    FactsBuilder previousRound(LocalDateTime value) {
      previousRound = value;
      return this;
    }

    FactsBuilder pattern(String value) {
      tablePattern = value;
      return this;
    }

    FactsBuilder databaseName(String value) {
      databaseName = value;
      return this;
    }

    FactsBuilder schemaName(String value) {
      schemaName = value;
      return this;
    }

    FactsBuilder dryRun() {
      dryRun = true;
      return this;
    }

    FactsBuilder noColumns() {
      collectColumns = false;
      return this;
    }

    PresenceFacts build() {
      return new PresenceFacts(
          PROJECT,
          SOURCE_ID,
          RUN_ID,
          "root",
          dryRun,
          databaseName,
          schemaName,
          tablePattern,
          collectColumns,
          Set.copyOf(seenKeys),
          List.copyOf(scopes),
          previousRound,
          THIS_ROUND);
    }
  }

  /** 只读扫描、只记命令的假仓库；<b>没有任何物理删除能力</b>。 */
  private static final class FakeCatalogRepository extends AssetUpsertRepository {
    final List<GoneCommand> gone = new ArrayList<>();
    List<CatalogPresenceRow> scan = List.of();
    /** 非空时覆盖真实上限，用来演"扫描触顶"。 */
    Integer scanLimit;
    int scanCalls;

    FakeCatalogRepository() {
      super(null, null);
    }

    @Override
    public PresenceScan presenceScan(Long projectId, String sourceId, int limit) {
      scanCalls++;
      return new PresenceScan(scan, scan.size() >= (scanLimit == null ? limit : scanLimit));
    }

    @Override
    public int markGone(GoneCommand command) {
      gone.add(command);
      return command.rows().size();
    }
  }

  private static final class RecordingRevocation implements CatalogGraphRevocation {
    List<String> keys;

    @Override
    public void revoke(Collection<String> assetKeys) {
      keys = List.copyOf(assetKeys);
    }
  }

  private static CatalogPresenceRow tableRow(String key, String tableName, LocalDateTime collected) {
    return row(key, "TABLE", "shop", null, tableName, collected);
  }

  private static CatalogPresenceRow warehouseRow(String key, String tableName, LocalDateTime collected) {
    return row(key, "TABLE", "warehouse", null, tableName, collected);
  }

  private static CatalogPresenceRow columnRow(String key, String tableName, LocalDateTime collected) {
    return row(key, "COLUMN", "shop", null, tableName, collected);
  }

  private static CatalogPresenceRow databaseRow(String key, String database, LocalDateTime collected) {
    return row(key, "DATABASE", database, null, null, collected);
  }

  private static CatalogPresenceRow row(
      String key,
      String assetType,
      String database,
      String schema,
      String tableName,
      LocalDateTime collected) {
    CatalogPresenceRow row = new CatalogPresenceRow();
    row.setId((long) Math.abs(key.hashCode()));
    row.setAssetKey(key);
    row.setAssetType(assetType);
    row.setDatabaseName(database);
    row.setSchemaName(schema);
    row.setTableName(tableName);
    row.setContentHash("hash-" + key);
    row.setLastCollectAt(collected);
    return row;
  }
}
