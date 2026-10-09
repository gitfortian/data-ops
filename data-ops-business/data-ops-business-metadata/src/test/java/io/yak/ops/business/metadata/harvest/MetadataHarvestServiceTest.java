package io.yak.ops.business.metadata.harvest;

import static io.yak.ops.business.metadata.harvest.HarvestFixtures.DATA_SOURCE_ID;
import static io.yak.ops.business.metadata.harvest.HarvestFixtures.JOB_ID;
import static io.yak.ops.business.metadata.harvest.HarvestFixtures.PROJECT_ID;
import static io.yak.ops.business.metadata.harvest.HarvestFixtures.column;
import static io.yak.ops.business.metadata.harvest.HarvestFixtures.harvestJob;
import static io.yak.ops.business.metadata.harvest.HarvestFixtures.harvestTypeRegistry;
import static io.yak.ops.business.metadata.harvest.HarvestFixtures.table;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metadata.dao.mapper.MdCollectRunMapper;
import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.exception.MetadataException;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.BatchCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.HarvestBatchResult;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.HarvestRequest;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.HarvestSummary;
import io.yak.ops.business.metadata.harvest.MetadataHarvestService.ScopeOutcome;
import io.yak.ops.business.metadata.harvest.stats.MetadataStatsRegistry;
import io.yak.ops.business.metadata.harvest.stats.MetadataStatsProvider.TableStats;
import io.yak.ops.business.metadata.dao.model.MdCollectJobPO;
import io.yak.ops.business.metadata.dao.model.MdCollectRunPO;
import io.yak.ops.common.enums.metadata.MetadataEntityStatus;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import io.yak.ops.common.enums.metadata.MetadataEnums.RunStatus;
import io.yak.ops.common.enums.metadata.MetadataEnums.TriggerType;
import io.yak.ops.common.enums.metadata.MetadataErrorCode;
import io.yak.ops.common.util.metadata.PhysicalTableAssetKey;
import io.yak.ops.spi.datasource.metadata.DataSourceColumn;
import io.yak.ops.spi.datasource.metadata.DataSourceTable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 一轮物理采集的落库现场（ticket 114）。
 *
 * <p>不起 Spring、不连库：目录用一个<b>会照实反映失败</b>的假端口，落库用一个<b>记住在场行</b>的假仓库，
 * 于是可以断言的全是"这轮到底写出了什么"——父级挂对没有、读不到的列有没有被写成"零列"、
 * 系统库有没有混进来、失败的那一轮有没有偷偷删东西。这五件事每一件在真库里都要跑一轮才看得见，
 * 而看见时已经污染了目录。
 */
class MetadataHarvestServiceTest {

  private final FakeCatalog catalog = new FakeCatalog();
  private final FakeRepository repository = new FakeRepository();
  private final MetadataStatsRegistry statsRegistry = mock(MetadataStatsRegistry.class);
  private final FakePresence presence = new FakePresence();
  private final MdCollectRunMapper runMapper = mock(MdCollectRunMapper.class);
  private final ObjectMapper json = new ObjectMapper();
  private final List<MdCollectRunPO> openedRuns = new ArrayList<>();
  private final List<MdCollectRunPO> closedRuns = new ArrayList<>();

  private MetadataHarvestService service;
  private HarvestSummary lastSummary;

  @BeforeEach
  void wireService() {
    service =
        new MetadataHarvestService(
            catalog, harvestTypeRegistry(), HarvestFixtures.codec(), repository, statsRegistry,
            presence, runMapper, json);
    when(statsRegistry.load(any(), anyLong(), any(), any())).thenReturn(Map.of());
    when(runMapper.insert(any(MdCollectRunPO.class))).thenAnswer(call -> {
      MdCollectRunPO run = call.getArgument(0);
      run.setId(501L);
      // 存快照而不是引用：closeRun 改的是同一个对象，留着引用就看不出"开轮时是什么状态"。
      openedRuns.add(snapshot(run));
      return 1;
    });
    when(runMapper.updateById(any(MdCollectRunPO.class))).thenAnswer(call -> {
      closedRuns.add(snapshot(call.getArgument(0)));
      return 1;
    });
  }

  private static MdCollectRunPO snapshot(MdCollectRunPO run) {
    MdCollectRunPO copy = new MdCollectRunPO();
    org.springframework.beans.BeanUtils.copyProperties(run, copy);
    return copy;
  }

  @Test
  void oneRoundLandsFourLevelsAndWiresEveryParentId() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", "订单表")));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));

    HarvestSummary summary = harvest(harvestJob());

    assertThat(summary.succeeded()).isTrue();
    assertThat(summary.newCount()).isEqualTo(4);
    Long serviceId = repository.idOf("datasource:7");
    Long databaseId = repository.idOf("database:7:shop");
    Long tableId = repository.idOf("table:7:shop..orders");
    assertThat(repository.onlyRow("databaseService").getParentAssetId()).isNull();
    assertThat(repository.onlyRow("database").getParentAssetId()).isEqualTo(serviceId);
    assertThat(repository.onlyRow("table").getParentAssetId()).isEqualTo(databaseId);
    assertThat(repository.onlyRow("tableColumn").getParentAssetId()).isEqualTo(tableId);
    // 四层各自的键：与 TableIdentityResolver 逐字同源，差一个字符就在血缘图里裂成两个节点。
    assertThat(repository.onlyRow("databaseService").getAssetKey()).isEqualTo("datasource:7");
    assertThat(repository.onlyRow("tableColumn").getAssetKey()).isEqualTo("column:7:shop..orders.id");
    assertThat(catalog.listDatabasesCalls).isEqualTo(1);
  }

  @Test
  void engineOwnedSchemasNeverEnterTheCatalog() {
    catalog.databases =
        List.of("shop", "information_schema", "MySQL", " performance_schema ", "sys", "__internal_schema");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.tablesByScope.put("sys|", List.of(table("sys", "user", "TABLE", null)));

    harvest(harvestJob());

    assertThat(repository.keys("database")).containsExactly("database:7:shop");
    assertThat(repository.keys("table")).containsExactly("table:7:shop..orders");
  }

  @Test
  void aDatabaseNamedByTheJobIsCollectedEvenWhenItLooksLikeAnEngineSchema() {
    // 点名优先于排除表：把"明知是引擎库仍要采"的决定留给配置方，而不是被采集悄悄改掉。
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("mysql|", List.of(table("mysql", "user", "TABLE", null)));
    MdCollectJobPO job = harvestJob();
    job.setDatabaseName("mysql");

    harvest(job);

    assertThat(repository.keys("database")).containsExactly("database:7:mysql");
    assertThat(catalog.listDatabasesCalls).isZero();
  }

  @Test
  void schemasAreCollectedAsTheirOwnScopeAndAnEmptyLevelIsNotMistakenForNoTables() {
    catalog.databases = List.of("dw");
    catalog.schemasByDatabase.put("dw", List.of("report", "  "));
    catalog.tablesByScope.put("dw|report", List.of(table("dw", "dwd_order", "TABLE", null)));
    catalog.tablesByScope.put("dw|", List.of(table("dw", "no_schema", "TABLE", null)));
    catalog.columnsByTable.put("dwd_order", List.of(HarvestFixtures.idColumn()));

    harvest(harvestJob());

    // 空白模式名被丢掉，而"一个模式都没有"走空串哨兵继续往下——两者都不等于"这个库没有表"。
    assertThat(repository.keys("table")).containsExactly("table:7:dw.report.dwd_order");
    assertThat(repository.onlyRow("table").getSchemaName()).isEqualTo("report");
    assertThat(summaryScopes()).extracting(ScopeOutcome::schemaName).containsExactly("report");
  }

  @Test
  void columnsThatCannotBeReadArePartialAndNeverWrittenAsZeroColumnTables() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put(
        "shop|",
        List.of(
            table("shop", "orders", "TABLE", null),
            table("shop", "readers", "TABLE", null),
            table("shop", "empty", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn(), column("amount", "decimal", 93, 18, 2, true, 2, false, "金额")));
    catalog.throwingTables = Set.of("readers");
    catalog.emptyColumnTables = Set.of("empty");

    HarvestSummary summary = harvest(harvestJob());

    assertThat(summary.partialFailed()).isEqualTo(2);
    assertThat(summary.status()).isEqualTo(RunStatus.SUCCESS);
    // 读不到列 ≠ 这张表没有列：写"零列"的指纹，下一轮就会把全部列判成删除。
    assertThat(repository.rows("table"))
        .filteredOn(row -> !row.getTableName().equals("orders"))
        .allSatisfy(row -> {
          assertThat(row.getContentHash()).isNull();
          assertThat(bagOf(row)).doesNotContainKey("s_num_1");
        });
    CatalogAssetRow orders = tableRowNamed("orders");
    assertThat(orders.getTableName()).isEqualTo("orders");
    assertThat(orders.getContentHash()).hasSize(32);
    assertThat(bagOf(orders)).containsEntry("s_num_1", 2);
    // 只有读得到列的表产列行，且两张失败表各留一条作用域内标记给 ticket 115。
    assertThat(repository.keys("tableColumn"))
        .containsExactly("column:7:shop..orders.id", "column:7:shop..orders.amount");
    assertThat(summaryScopes()).singleElement().satisfies(scope -> {
      assertThat(scope.tables()).isEqualTo(3);
      assertThat(scope.partialTables()).isEqualTo(2);
      assertThat(scope.failed()).isFalse();
      assertThat(scope.tablesReadable()).isTrue();
      assertThat(scope.columnsComplete()).isFalse();
    });
  }

  @Test
  void aScopeWhoseTableListCannotBeReadIsNotJudgeableForGone() {
    catalog.databases = List.of("shop", "warehouse");
    catalog.throwingScopes = Set.of("warehouse|");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));

    HarvestSummary summary = harvest(harvestJob());

    // 清单读失败时"库里一张表都没有"与"连清单都没拿到"必须分得开——后者会被判成全库删除。
    assertThat(summary.succeeded()).isTrue();
    assertThat(summaryScopes()).extracting(ScopeOutcome::databaseName)
        .containsExactly("shop", "warehouse");
    assertThat(summaryScopes().get(1).failed()).isTrue();
    assertThat(summary.partialFailed()).as("failed table listing is an incomplete baseline").isEqualTo(1);
    assertThat(summaryScopes().get(1).tablesReadable()).isFalse();
    assertThat(summaryScopes().get(1).tables()).isZero();
    assertThat(repository.keys("database")).containsExactly("database:7:shop", "database:7:warehouse");
    assertThat(repository.keys("table")).containsExactly("table:7:shop..orders");
  }

  @Test
  void dryRunRecordsTheRoundAndAsksTheRepositoryForNoWrites() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));

    HarvestSummary summary = harvest(harvestJob(), true);

    assertThat(summary.succeeded()).isTrue();
    assertThat(repository.commands).isNotEmpty();
    assertThat(repository.commands).allSatisfy(command -> assertThat(command.dryRun()).isTrue());
    assertThat(repository.writesApplied).isZero();
    // dry-run 的运行历史必须留痕：它是"任务启用前置条件"的唯一证据（plan §0.13）。
    assertThat(openedRuns).singleElement().satisfies(run -> {
      assertThat(run.getDryRun()).isTrue();
      assertThat(run.getStatus()).isEqualTo(RunStatus.RUNNING.name());
      assertThat(run.getProviderType()).isEqualTo(ProviderType.HARVESTED.name());
      assertThat(run.getTriggerType()).isEqualTo(TriggerType.MANUAL.name());
      assertThat(run.getStartedAt()).isNotNull();
      assertThat(run.getCreatedBy()).isEqualTo("root");
    });
    assertThat(closedRuns).singleElement().satisfies(run -> {
      assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS.name());
      assertThat(run.getCntGone()).isZero();
    });
  }

  @Test
  void aFailedRoundReportsFailureAndRemovesNothing() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));
    repository.failOnType = "table";

    HarvestSummary summary = harvest(harvestJob());

    assertThat(summary.status()).isEqualTo(RunStatus.FAILED);
    assertThat(summary.errorMessage()).contains("模拟目录写入失败");
    assertThat(repository.keys("databaseService")).containsExactly("datasource:7");
    assertThat(repository.keys("database")).containsExactly("database:7:shop");
    // 已写入的不回滚、更不删：采集是幂等 upsert，回滚会把上一轮的好数据一起带走。
    assertThat(repository.deleted).isZero();
    assertThat(summary.newCount()).isEqualTo(2);

    List<MdCollectRunPO> closed = closedRuns;
    assertThat(closed).singleElement().satisfies(run -> {
      assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED.name());
      assertThat(run.getCntGone()).isZero();
      assertThat(run.getErrorMessage()).contains("模拟目录写入失败");
      assertThat(run.getCntNew()).isEqualTo(2);
    });
  }

  @Test
  void anUnavailableCatalogFailsTheRoundWithoutTouchingTheDirectory() {
    catalog.available = false;

    HarvestSummary summary = harvest(harvestJob());

    assertThat(summary.status()).isEqualTo(RunStatus.FAILED);
    assertThat(summary.errorMessage()).contains("数据源目录能力未装配");
    assertThat(repository.commands).isEmpty();
    assertThat(closedRuns).singleElement()
        .satisfies(run -> assertThat(run.getCntTotal()).isZero());
  }

  @Test
  void locatorsAreNormalizedWhileNamesKeepTheSourcesOwnCase() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "  Orders ", "TABLE", null)));
    catalog.columnsByTable.put("  Orders ", List.of(column(" Amount ", "decimal", 93, 18, 2, true, 1, false, null)));

    harvest(harvestJob());

    CatalogAssetRow tableRow = repository.onlyRow("table");
    CatalogAssetRow columnRow = repository.onlyRow("tableColumn");
    assertThat(tableRow.getTableName()).isEqualTo("orders");
    assertThat(tableRow.getName()).isEqualTo("Orders");
    assertThat(tableRow.getDisplayName()).isEqualTo("Orders");
    assertThat(columnRow.getColumnName()).isEqualTo("amount");
    assertThat(columnRow.getAssetKey()).isEqualTo("column:7:shop..orders.amount");
    assertThat(bagOf(tableRow)).containsEntry("tableName", "orders");
  }

  @Test
  void everyRowCarriesCatalogOwnedOwnershipAndFingerprintTags() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", "订单表")));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));

    harvest(harvestJob());

    for (CatalogAssetRow row : repository.written()) {
      // 归属标签必须独立于血缘侧的 DATASOURCE：lineage 的 deleteUnreferencedOwnedAssets 按
      // (source_type, source_id) 清理无关系资产，跟着它写就等于把自己的目录行交给别人的删除路径。
      assertThat(row.getSourceType()).isEqualTo("METADATA");
      assertThat(row.getSourceId()).isEqualTo(String.valueOf(DATA_SOURCE_ID));
      assertThat(row.getDataSourceId()).isEqualTo(String.valueOf(DATA_SOURCE_ID));
      assertThat(row.getProjectId()).isEqualTo(PROJECT_ID);
      assertThat(row.getProviderType()).isEqualTo(ProviderType.HARVESTED.name());
      assertThat(row.getEntityStatus()).isEqualTo(MetadataEntityStatus.UNPROCESSED.value());
      assertThat(row.getSourceHash()).isNull();
      assertThat(row.getCollectJobId()).isEqualTo(JOB_ID);
      assertThat(row.getUpdatedBy()).isEqualTo("root");
      assertThat(row.getFirstSeenAt()).isNotNull();
      assertThat(row.getLastCollectAt()).isEqualTo(row.getFirstSeenAt());
      assertThat(row.getFqnHash()).isEqualTo(digestHex(row.getAssetKey().toLowerCase()));
    }
    assertThat(repository.written()).extracting(CatalogAssetRow::getAssetType)
        .containsExactlyInAnyOrder("DATABASE_SERVICE", "DATABASE", "TABLE", "COLUMN");
    assertThat(repository.written()).extracting(CatalogAssetRow::getTypeId)
        .containsExactlyInAnyOrder(101L, 102L, 103L, 104L);
  }

  @Test
  void fqnsDropSegmentsTheSourceNeverHad() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));

    harvest(harvestJob());

    // MySQL/Doris 没有 schema 层：留成 "shop..orders" 会让展示出的 FQN 与实际键不一致。
    assertThat(repository.onlyRow("databaseService").getFullyQualifiedName()).isEqualTo("shop-mysql");
    assertThat(repository.onlyRow("database").getFullyQualifiedName()).isEqualTo("shop-mysql.shop");
    assertThat(repository.onlyRow("table").getFullyQualifiedName()).isEqualTo("shop.orders");
    assertThat(repository.onlyRow("tableColumn").getFullyQualifiedName()).isEqualTo("shop.orders.id");
  }

  @Test
  void aConfiguredTablePatternSelectsWhichTablesBecomeRows() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put(
        "shop|",
        List.of(
            table("shop", "orders", "TABLE", null),
            table("shop", "order_items", "TABLE", null),
            table("shop", "products", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));
    catalog.columnsByTable.put("order_items", List.of(HarvestFixtures.idColumn()));
    MdCollectJobPO job = harvestJob();
    job.setTablePattern("order%");

    harvest(job);

    // order% 是 SQL 后缀通配：orders 与 order_items 都命中，products 整轮不进目录。
    assertThat(repository.keys("table"))
        .containsExactly("table:7:shop..orders", "table:7:shop..order_items");
    // 作用域里的表数按"匹配后"计，ticket 115 拿它判 GONE 才不会把没采的表判成删除。
    assertThat(summaryScopes()).singleElement().satisfies(scope -> {
      assertThat(scope.tables()).isEqualTo(2);
      assertThat(scope.columnsComplete()).isTrue();
    });
  }

  @Test
  void statisticsLandAsAttributesAndUnknownStaysUnknownRatherThanZero() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put(
        "shop|",
        List.of(table("shop", "orders", "TABLE", null), table("shop", "legacy", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));
    catalog.columnsByTable.put("legacy", List.of(HarvestFixtures.idColumn()));
    LocalDateTime ddl = LocalDateTime.of(2026, 8, 30, 6, 30, 0);
    when(statsRegistry.load("MYSQL", DATA_SOURCE_ID, "shop", ""))
        .thenReturn(Map.of("orders", new TableStats(12_345L, true, ddl)));

    harvest(harvestJob());

    Map<String, Object> orders = bagOf(tableRowNamed("orders"));
    assertThat(orders)
        .containsEntry("s_num_2", 12_345)
        .containsEntry("s_bool_1", true)
        .containsEntry("s_date_1", "2026-08-30 06:30:00");
    // 统计缺失时三个键一律不出现：写 0 会被下游当成"这张表真的零行"。
    assertThat(bagOf(tableRowNamed("legacy")))
        .doesNotContainKeys("s_num_2", "s_bool_1", "s_date_1");
  }

  @Test
  void collectingWithoutColumnsIsANotAFailureAndProducesNoColumnRows() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    MdCollectJobPO job = harvestJob();
    job.setCollectColumns(false);

    HarvestSummary summary = harvest(job);

    assertThat(summary.partialFailed()).isZero();
    assertThat(repository.commands).extracting(BatchCommand::typeName)
        .containsExactly("databaseService", "database", "table");
    // 没要求列时不产列行，但表指纹也不能写——否则下一轮真采列时"多出列"会被判成结构变更。
    assertThat(repository.onlyRow("table").getContentHash()).isNull();
    assertThat(bagOf(repository.onlyRow("table"))).doesNotContainKey("s_num_1");
    assertThat(catalog.listColumnsCalls).isZero();
  }

  @Test
  void identityThatCannotBeResolvedFailsBeforeAnyRunRowExists() {
    MdCollectJobPO noProject = harvestJob();
    noProject.setProjectId(null);
    expectErrorCode(() -> harvest(noProject), MetadataErrorCode.PROJECT_CONTEXT_REQUIRED);

    MdCollectJobPO noDataSource = harvestJob();
    noDataSource.setDataSourceId(null);
    expectErrorCode(
        () -> harvest(noDataSource), MetadataErrorCode.COLLECT_JOB_SCOPE_INVALID);

    verify(runMapper, never()).insert(any(MdCollectRunPO.class));
    assertThat(repository.commands).isEmpty();
  }

  @Test
  void sqlWildcardsAreTheOnlyPatternMagic() {
    assertThat(MetadataHarvestService.matchesPattern("orders", null)).isTrue();
    assertThat(MetadataHarvestService.matchesPattern("orders", "   ")).isTrue();
    assertThat(MetadataHarvestService.matchesPattern("orders", "%")).isTrue();
    assertThat(MetadataHarvestService.matchesPattern("ORDER_2026", "order%")).isTrue();
    assertThat(MetadataHarvestService.matchesPattern("orders", "_rders")).isTrue();
    assertThat(MetadataHarvestService.matchesPattern("orders", "ord_er")).isFalse();
    assertThat(MetadataHarvestService.matchesPattern("order", "ord_er")).isFalse();
    // 正则元字符按字面量：表名里带点/带括号是常态，写成 .* 会让"点了全盘"式的匹配悄悄扩大作用域。
    assertThat(MetadataHarvestService.matchesPattern("orders", "order.s")).isFalse();
    assertThat(MetadataHarvestService.matchesPattern("orders(1)", "orders(1)")).isTrue();
  }

  @Test
  void judgementReceivesEveryKeyRegisteredEvenWhenTheSecondRoundChangesNothing() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));
    LocalDateTime previousRound = LocalDateTime.of(2026, 9, 1, 3, 0);
    presence.previousRound = previousRound;

    harvest(harvestJob());
    HarvestSummary second = harvest(harvestJob());

    assertThat(presence.handed).as("两轮各交一次在场性判定").hasSize(2);
    assertThat(second.unchangedCount()).as("第二轮应当全是 UNCHANGED").isEqualTo(4);
    // 判 GONE 要的是"本轮见过谁"，不是"本轮改过谁"：UNCHANGED 行若漏进 seen 集，下一轮就被判成消失。
    assertThat(presence.handed.get(1).seenKeys())
        .containsExactlyInAnyOrder(
            "datasource:7", "database:7:shop", "table:7:shop..orders", "column:7:shop..orders.id");
    PresenceFacts facts = presence.handed.get(1);
    assertThat(facts.previousRoundStartedAt()).isEqualTo(previousRound);
    assertThat(facts.projectId()).isEqualTo(PROJECT_ID);
    assertThat(facts.sourceId()).isEqualTo("7");
    assertThat(facts.collectRunId()).isEqualTo(501L);
    assertThat(facts.operator()).isEqualTo("root");
    assertThat(facts.dryRun()).isFalse();
    assertThat(facts.collectColumns()).isTrue();
    assertThat(facts.goneAt()).isNotNull();
    assertThat(facts.readableScopeKeys()).containsExactly("shop|");
    assertThat(facts.columnCompleteScopeKeys()).containsExactly("shop|");
    assertThat(facts.databasesEnumerated()).isTrue();
  }

  @Test
  void aNarrowedJobHandsOverItsNarrowedScopeRatherThanTheWholeSource() {
    catalog.databases = List.of("shop");
    MdCollectJobPO job = harvestJob();
    job.setDatabaseName("shop");
    job.setSchemaName("dw");
    job.setTablePattern("order%");
    job.setCollectColumns(false);

    harvest(job);

    PresenceFacts facts = presence.handed.get(0);
    assertThat(facts.tablePattern()).isEqualTo("order%");
    assertThat(facts.collectColumns()).isFalse();
    // 点名了单个库就没有"整个库消失"这个结论可下；判定侧靠这一个标记收窄候选。
    assertThat(facts.databasesEnumerated()).isFalse();
    assertThat(facts.databaseName()).isEqualTo("shop");
    assertThat(facts.schemaName()).isEqualTo("dw");
  }

  @Test
  void softDeletedCountFromTheJudgementLandsOnBothTheRunRowAndTheSummary() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));
    presence.outcome =
        new MetadataPresenceService.PresenceOutcome(
            false, null, 3, 1, 2, 0, 40, 1, List.of("table:7:shop..archive"));

    HarvestSummary summary = harvest(harvestJob());

    assertThat(summary.status()).isEqualTo(RunStatus.SUCCESS);
    assertThat(summary.goneCount()).isEqualTo(3);
    assertThat(summary.errorMessage()).isNull();
    assertThat(closedRuns).singleElement().satisfies(run -> {
      assertThat(run.getStatus()).isEqualTo(RunStatus.SUCCESS.name());
      assertThat(run.getCntGone()).isEqualTo(3);
      assertThat(run.getErrorMessage()).isNull();
    });
  }

  @Test
  void aFusedRoundIsSUSPECTWithTheReasonOnTheRunRow() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));
    presence.outcome =
        new MetadataPresenceService.PresenceOutcome(
            true, "单轮缺席表数 12/20 超过坍塌阈值 30%，本轮不落任何 GONE", 0, 12, 0, 0, 32, 20, List.of());

    HarvestSummary summary = harvest(harvestJob());

    // 熔断不是失败：数据一个都没写错。但它也不能记成 SUCCESS，否则"这轮为什么没删"无人可见。
    assertThat(summary.status()).isEqualTo(RunStatus.SUSPECT);
    assertThat(summary.succeeded()).isFalse();
    assertThat(summary.goneCount()).isZero();
    assertThat(summary.errorMessage()).contains("坍塌阈值");
    assertThat(closedRuns).singleElement().satisfies(run -> {
      assertThat(run.getStatus()).isEqualTo(RunStatus.SUSPECT.name());
      assertThat(run.getCntGone()).isZero();
      assertThat(run.getErrorMessage()).contains("本轮不落任何 GONE");
    });
  }

  @Test
  void absenceIsNeverJudgedAfterARoundThatFailedMidEnumeration() {
    catalog.databases = List.of("shop");
    catalog.tablesByScope.put("shop|", List.of(table("shop", "orders", "TABLE", null)));
    catalog.columnsByTable.put("orders", List.of(HarvestFixtures.idColumn()));
    repository.failOnType = "table";

    harvest(harvestJob());

    // 枚举本身没跑完，此刻的 seen 集是残缺的；拿它判缺席正是 plan §3.4 要拦的那一类。
    assertThat(presence.handed).isEmpty();
    assertThat(closedRuns).singleElement().satisfies(run -> {
      assertThat(run.getStatus()).isEqualTo(RunStatus.FAILED.name());
      assertThat(run.getCntGone()).isZero();
    });
  }

  private List<ScopeOutcome> summaryScopes() {
    return lastSummary.scopes();
  }

  private HarvestSummary harvest(MdCollectJobPO job) {
    return harvest(job, false);
  }

  private HarvestSummary harvest(MdCollectJobPO job, boolean dryRun) {
    lastSummary = service.harvest(new HarvestRequest(job, TriggerType.MANUAL, dryRun, "root"));
    return lastSummary;
  }

  private Map<String, Object> bagOf(CatalogAssetRow row) {
    try {
      assertThat(row.getMdAttributes()).isNotBlank();
      return json.readValue(row.getMdAttributes(), Map.class);
    } catch (Exception exception) {
      throw new AssertionError("属性袋不是合法 JSON: " + row.getMdAttributes(), exception);
    }
  }

  private static String digestHex(String raw) {
    return io.yak.ops.business.metadata.metamodel.MetadataKeyCodec.digestHex(raw);
  }

  private static void expectErrorCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, MetadataErrorCode expected) {
    assertThatThrownBy(call)
        .isInstanceOf(MetadataException.class)
        .extracting(e -> ((MetadataException) e).getErrorCode())
        .isEqualTo(expected);
  }

  /**
   * 在场性判定的假身。
   *
   * <p>本测试只关心"采集把什么交给判定、判定的结论怎么写回运行历史"这两条接缝；
   * 四道闸本身的规则在 {@code MetadataPresenceServiceTest} 里逐条测，此处重述一遍只会让两处漂移。
   */
  private static final class FakePresence extends MetadataPresenceService {
    final List<PresenceFacts> handed = new ArrayList<>();
    LocalDateTime previousRound;
    PresenceOutcome outcome =
        new PresenceOutcome(false, null, 0, 0, 0, 0, 0, 0, List.of());

    FakePresence() {
      super(null, null, null, 0.3);
    }

    @Override
    public LocalDateTime previousRoundStartedAt(Long projectId, Long jobId, Long currentRunId) {
      return previousRound;
    }

    @Override
    public PresenceOutcome evaluateAndApply(PresenceFacts facts) {
      handed.add(facts);
      return outcome;
    }
  }

  /** 按规范表名找回本轮写出的表行。 */
  private CatalogAssetRow tableRowNamed(String tableName) {
    return repository.rows("table").stream()
        .filter(row -> tableName.equals(row.getTableName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("本轮没有写出表 " + tableName));
  }

  /** 目录侧读写的假仓库：记住在场行、按指纹算四类计数，删除能力<b>物理不存在</b>。 */
  private static final class FakeRepository extends AssetUpsertRepository {
    final List<BatchCommand> commands = new ArrayList<>();
    final Map<String, CatalogAssetRow> stored = new LinkedHashMap<>();
    final Map<String, Long> ids = new LinkedHashMap<>();
    long writesApplied;
    long deleted;
    String failOnType;
    private long nextId = 2_000L;

    FakeRepository() {
      super(null, null);
    }

    @Override
    public HarvestBatchResult write(BatchCommand command) {
      commands.add(command);
      if (command.typeName().equals(failOnType)) {
        throw new IllegalStateException("模拟目录写入失败 type=" + command.typeName());
      }
      if (command.rows().isEmpty()) {
        return HarvestBatchResult.empty();
      }
      if (command.dryRun()) {
        return new HarvestBatchResult(
            (int) command.rows().stream().filter(row -> !stored.containsKey(row.getAssetKey())).count(),
            0,
            (int) command.rows().stream().filter(row -> stored.containsKey(row.getAssetKey())).count(),
            0,
            Map.of());
      }
      int fresh = 0;
      int changed = 0;
      int unchanged = 0;
      Map<String, Long> touchedIds = new LinkedHashMap<>();
      for (CatalogAssetRow row : command.rows()) {
        CatalogAssetRow before = stored.get(row.getAssetKey());
        if (before == null) {
          fresh++;
          stored.put(row.getAssetKey(), row);
        } else if (java.util.Objects.equals(before.getContentHash(), row.getContentHash())) {
          unchanged++;
          stored.put(row.getAssetKey(), row);
        } else {
          changed++;
          stored.put(row.getAssetKey(), row);
        }
        touchedIds.put(
            row.getAssetKey(),
            ids.computeIfAbsent(row.getAssetKey(), key -> nextId++));
      }
      writesApplied += command.rows().size();
      return new HarvestBatchResult(
          fresh, changed, unchanged, 0, Map.copyOf(touchedIds));
    }

    @Override
    public Map<String, Long> idsOf(Long projectId, java.util.Collection<String> assetKeys) {
      Map<String, Long> found = new LinkedHashMap<>();
      for (String key : assetKeys) {
        Long id = ids.get(key);
        if (id != null) {
          found.put(key, id);
        }
      }
      return found;
    }

    Long idOf(String assetKey) {
      return ids.get(assetKey);
    }

    List<CatalogAssetRow> rows(String typeName) {
      List<CatalogAssetRow> rows = new ArrayList<>();
      commands.stream()
          .filter(command -> command.typeName().equals(typeName))
          .forEach(command -> rows.addAll(command.rows()));
      return rows;
    }

    Set<String> keys(String typeName) {
      Set<String> keys = new LinkedHashSet<>();
      rows(typeName).forEach(row -> keys.add(row.getAssetKey()));
      return keys;
    }

    /** 一类实体一轮一批：批数不对本身就是问题，所以这里直接要求只有一行。 */
    CatalogAssetRow onlyRow(String typeName) {
      List<CatalogAssetRow> rows = rows(typeName);
      if (rows.size() != 1) {
        throw new AssertionError("期望 " + typeName + " 只写出一行，实际 " + rows.size() + " 行");
      }
      return rows.get(0);
    }

    List<CatalogAssetRow> written() {
      List<CatalogAssetRow> rows = new ArrayList<>();
      commands.forEach(command -> rows.addAll(command.rows()));
      return rows;
    }
  }

  /** 会照实反映失败的假目录端口。 */
  private static final class FakeCatalog implements HarvestCatalogSource {
    boolean available = true;
    String databaseType = "MYSQL";
    String serviceName = "shop-mysql";
    List<String> databases = List.of();
    Map<String, List<String>> schemasByDatabase = new LinkedHashMap<>();
    Map<String, List<DataSourceTable>> tablesByScope = new LinkedHashMap<>();
    Map<String, List<DataSourceColumn>> columnsByTable = new LinkedHashMap<>();
    Set<String> throwingScopes = new LinkedHashSet<>();
    Set<String> throwingTables = new LinkedHashSet<>();
    Set<String> emptyColumnTables = new LinkedHashSet<>();
    int listDatabasesCalls;
    int listColumnsCalls;

    @Override
    public boolean available() {
      return available;
    }

    @Override
    public String databaseServiceName(long dataSourceId) {
      return serviceName;
    }

    @Override
    public String databaseType(long dataSourceId) {
      return databaseType;
    }

    @Override
    public List<String> listDatabases(long dataSourceId) {
      listDatabasesCalls++;
      return databases;
    }

    @Override
    public List<String> listSchemas(long dataSourceId, String database) {
      return schemasByDatabase.getOrDefault(database, List.of());
    }

    @Override
    public List<DataSourceTable> listTables(long dataSourceId, String database, String schema) {
      String scope = database + "|" + (schema == null ? "" : schema);
      if (throwingScopes.contains(scope)) {
        throw new IllegalStateException("模拟目录不可达 scope=" + scope);
      }
      return tablesByScope.getOrDefault(scope, List.of());
    }

    @Override
    public List<DataSourceColumn> listColumns(
        long dataSourceId, String database, String schema, String tableName) {
      listColumnsCalls++;
      if (throwingTables.contains(tableName)) {
        throw new IllegalStateException("模拟列读取失败 table=" + tableName);
      }
      if (emptyColumnTables.contains(tableName)) {
        return List.of();
      }
      return columnsByTable.getOrDefault(tableName, List.of());
    }
  }
}
