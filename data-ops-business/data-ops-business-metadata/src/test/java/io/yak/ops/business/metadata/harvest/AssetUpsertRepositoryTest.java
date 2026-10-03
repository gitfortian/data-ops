package io.yak.ops.business.metadata.harvest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metadata.dao.mapper.LineageCatalogRowMapper;
import io.yak.ops.business.metadata.dao.mapper.MdChangeMapper;
import io.yak.ops.business.metadata.dao.model.CatalogAssetRow;
import io.yak.ops.business.metadata.dao.model.CatalogAssetState;
import io.yak.ops.business.metadata.dao.model.CatalogPresenceRow;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.BatchCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneCommand;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.GoneRow;
import io.yak.ops.business.metadata.harvest.AssetUpsertRepository.HarvestBatchResult;
import io.yak.ops.business.metadata.dao.model.MdChangePO;
import io.yak.ops.common.enums.metadata.MetadataEnums.ChangeType;
import io.yak.ops.common.enums.metadata.MetadataEnums.ProviderType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 唯一 upsert 的判增量语义（ticket 114，plan §3.3）＋ 在场集扫描与软删（ticket 115，plan §3.4）。
 *
 * <p>SQL 的形状（UPDATE 子句含哪些列、{@code <=>} 守卫、{@code LAST_INSERT_ID(id)}、{@code markGone}
 * 的 SET 只有两列）由 {@code SharedTableWritePathContractTest}、{@code CatalogPresenceStatementTest}
 * 与 XML 预演守着；本类只管 Java 侧那半件事：<b>四类计数怎么算出来、软删怎么分批</b>。
 * 这是唯一一处可能被写歪又无人值守的地方——受影响行数在 Connector/J 默认配置下不可信，
 * 所以计数只能来自写前预读，一旦有人"顺手"改成读 {@code upsertAssets} 的返回值，
 * 采集会把每一轮都报成全量变更。
 *
 * <p>本类断言的 GONE 一律是<b>软删</b>：假 mapper 里根本没有 delete 方法可调，
 * 物理删除在编译期就是不可能事件（唯一的DELETE禁令见语句级守卫）。
 */
class AssetUpsertRepositoryTest {

  private static final Long PROJECT = 42L;
  private static final LocalDateTime ROUND_ONE = LocalDateTime.of(2026, 9, 1, 3, 0);

  private final LineageCatalogRowMapper catalogMapper = mock(LineageCatalogRowMapper.class);
  private final MdChangeMapper changeMapper = mock(MdChangeMapper.class);
  private final AssetUpsertRepository repository =
      new AssetUpsertRepository(catalogMapper, changeMapper);

  /** 库里在场的行：asset_key → 状态。{@code upsertAssets} 的模拟实现会更新它。 */
  private final Map<String, CatalogAssetState> store = new LinkedHashMap<>();

  private final List<List<CatalogAssetRow>> upsertChunks = new ArrayList<>();
  private final List<List<String>> touchedChunks = new ArrayList<>();
  private final List<List<Long>> goneChunks = new ArrayList<>();
  private final List<MdChangePO> changes = new ArrayList<>();
  /** 软删语句的受影响行数；{@code -1} = 与候选数相符，测试用它模拟并发轮次先行处理。 */
  private int markGoneRowsPerCall = -1;
  private long nextId = 1000L;

  @BeforeEach
  void wireFakeStatements() {
    when(catalogMapper.selectStates(eq(PROJECT), any())).thenAnswer(call -> {
      Collection<String> keys = call.getArgument(1);
      List<CatalogAssetState> found = new ArrayList<>();
      for (String key : keys) {
        CatalogAssetState state = store.get(key);
        if (state != null) {
          found.add(copy(state));
        }
      }
      return found;
    });
    when(catalogMapper.upsertAssets(any())).thenAnswer(call -> {
      Collection<CatalogAssetRow> rows = call.getArgument(0);
      upsertChunks.add(new ArrayList<>(rows));
      for (CatalogAssetRow row : rows) {
        CatalogAssetState state =
            store.computeIfAbsent(row.getAssetKey(), key -> {
              CatalogAssetState fresh = new CatalogAssetState();
              fresh.setId(nextId++);
              fresh.setAssetKey(key);
              return fresh;
            });
        state.setContentHash(row.getContentHash());
        state.setSourceHash(row.getSourceHash());
        state.setProviderType(row.getProviderType());
      }
      return rows.size();
    });
    when(catalogMapper.touchLastCollected(eq(PROJECT), any(), any())).thenAnswer(call -> {
      touchedChunks.add(new ArrayList<>(call.getArgument(1)));
      return ((Collection<?>) call.getArgument(1)).size();
    });
    when(catalogMapper.markGone(any(), any())).thenAnswer(call -> {
      Collection<Long> ids = call.getArgument(0);
      goneChunks.add(new ArrayList<>(ids));
      return markGoneRowsPerCall < 0 ? ids.size() : markGoneRowsPerCall;
    });
    when(changeMapper.insert(any(MdChangePO.class))).thenAnswer(call -> {
      changes.add(call.getArgument(0));
      return 1;
    });
  }

  @Test
  void countsComeFromThePreReadNotFromAffectedRows() {
    CatalogAssetRow existingUnchanged = row("table:7:shop..a", "hash-a", null);
    CatalogAssetRow existingChanged = row("table:7:shop..b", "hash-b-new", null);
    CatalogAssetRow brandNew = row("table:7:shop..c", "hash-c", null);
    givenStored("table:7:shop..a", "hash-a", null, null);
    givenStored("table:7:shop..b", "hash-b-old", null, null);

    HarvestBatchResult result =
        repository.write(command(ProviderType.HARVESTED, existingUnchanged, existingChanged, brandNew));

    assertThat(result.newCount()).isEqualTo(1);
    assertThat(result.changedCount()).isEqualTo(1);
    assertThat(result.unchangedCount()).isEqualTo(1);
    assertThat(result.revivedCount()).isZero();
  }

  @Test
  void harvestedChannelOnlyLooksAtContentHashAndRegisteredOnlyAtSourceHash() {
    // 拿反了的后果：投影每次重排 source_hash 都变 → 目录天天全量 CHANGED，变更流水被刷爆。
    CatalogAssetState harvested = state("k", "content-old", "source-old", null);
    assertThat(
            AssetUpsertRepository.isChanged(
                ProviderType.HARVESTED, harvested, rowWithHashes("k", "content-new", "source-old")))
        .isTrue();
    assertThat(
            AssetUpsertRepository.isChanged(
                ProviderType.HARVESTED, harvested, rowWithHashes("k", "content-old", "source-new")))
        .isFalse();

    CatalogAssetState registered = state("k", "content-old", "source-old", null);
    assertThat(
            AssetUpsertRepository.isChanged(
                ProviderType.REGISTERED, registered, rowWithHashes("k", "content-old", "source-new")))
        .isTrue();
    assertThat(
            AssetUpsertRepository.isChanged(
                ProviderType.REGISTERED, registered, rowWithHashes("k", "content-new", "source-old")))
        .isFalse();
  }

  @Test
  void nullHashesCompareAsValuesRatherThanAsUnknown() {
    // 遗留行 content_hash 为 NULL。用 = 比较恒得 NULL → 判成"变了"，一轮把全部遗留行写进变更流水。
    CatalogAssetState legacy = state("k", null, null, null);
    assertThat(
            AssetUpsertRepository.isChanged(
                ProviderType.HARVESTED, legacy, rowWithHashes("k", null, null)))
        .isFalse();
    assertThat(
            AssetUpsertRepository.isChanged(
                ProviderType.HARVESTED, legacy, rowWithHashes("k", "hash", null)))
        .isTrue();
  }

  @Test
  void dryRunReportsTheSameCountsButIssuesNoWriteAtAll() {
    givenStored("table:7:shop..a", "hash-a-old", null, null);

    HarvestBatchResult result =
        repository.write(
            command(ProviderType.HARVESTED, true, row("table:7:shop..a", "hash-a", null),
                row("table:7:shop..b", "hash-b", null)));

    assertThat(result.newCount()).isEqualTo(1);
    assertThat(result.changedCount()).isEqualTo(1);
    assertThat(result.assetIds()).isEmpty();
    verify(catalogMapper, never()).upsertAssets(any());
    verify(catalogMapper, never()).touchLastCollected(anyLong(), any(), any());
    verify(changeMapper, never()).insert(any(MdChangePO.class));
    assertThat(store.get("table:7:shop..a").getContentHash()).isEqualTo("hash-a-old");
  }

  @Test
  void anIdempotentReRunRefreshsPresenceWithoutProducingChangeRows() {
    CatalogAssetRow first = row("table:7:shop..a", "hash-a", null);
    repository.write(command(ProviderType.HARVESTED, first));
    assertThat(changes).hasSize(1);

    upsertChunks.clear();
    touchedChunks.clear();
    changes.clear();

    HarvestBatchResult second =
        repository.write(
            command(ProviderType.HARVESTED, row("table:7:shop..a", "hash-a", null)));

    assertThat(second.unchangedCount()).isEqualTo(1);
    assertThat(second.newCount()).isZero();
    assertThat(second.changedCount()).isZero();
    assertThat(second.assetIds()).isEmpty();
    // 在场痕迹照刷（ticket 115 判 GONE 要看它），但变更流水一条不加。
    // "不产生实际行写入"是 XML 里那三个 <=> 守卫的活，不在这一层。
    assertThat(changes).isEmpty();
    assertThat(touchedChunks).containsExactly(List.of("table:7:shop..a"));
  }

  @Test
  void aGoneEntityThatComesBackIsLoggedAsRevivedNotQuietlyOverwritten() {
    givenStored("table:7:shop..a", "hash-a-old", null, ROUND_ONE.minusDays(3));

    HarvestBatchResult result =
        repository.write(command(ProviderType.HARVESTED, row("table:7:shop..a", "hash-a", null)));

    assertThat(result.revivedCount()).isEqualTo(1);
    assertThat(result.changedCount()).isEqualTo(1);
    assertThat(changes).singleElement().satisfies(
            change -> {
              assertThat(change.getChangeType()).isEqualTo(ChangeType.REVIVED.name());
              assertThat(change.getBeforeValue()).isEqualTo("hash-a-old");
              assertThat(change.getAfterValue()).isEqualTo("hash-a");
            });
  }

  @Test
  void changeRowsCarryTheFingerprintThatActuallyDivergedAndTheCommandsType() {
    givenStored("table:7:shop..a", "hash-a-old", null, null);

    repository.write(
        command(ProviderType.HARVESTED, row("table:7:shop..a", "hash-a", null),
            row("table:7:shop..b", "hash-b", null)));

    assertThat(changes).hasSize(2);
    Map<String, MdChangePO> byKey = new LinkedHashMap<>();
    changes.forEach(change -> byKey.put(change.getAssetKey(), change));

    MdChangePO changed = byKey.get("table:7:shop..a");
    assertThat(changed.getChangeType()).isEqualTo(ChangeType.CHANGED.name());
    assertThat(changed.getBeforeValue()).isEqualTo("hash-a-old");
    assertThat(changed.getAfterValue()).isEqualTo("hash-a");
    // type_name 来自命令：一批 = 一类实体，所以目录行模型上不必挂一个共表里不存在的列。
    assertThat(changed.getTypeName()).isEqualTo("table");
    assertThat(changed.getProviderType()).isEqualTo(ProviderType.HARVESTED.name());
    assertThat(changed.getProjectId()).isEqualTo(PROJECT);
    assertThat(changed.getChangedBy()).isEqualTo("root");

    MdChangePO created = byKey.get("table:7:shop..b");
    assertThat(created.getChangeType()).isEqualTo(ChangeType.NEW.name());
    assertThat(created.getBeforeValue()).isNull();
    assertThat(created.getAfterValue()).isNull();
    assertThat(created.getAssetId()).isNotNull();
  }

  @Test
  void writesAreChunkedAtFiveHundredRowsPerStatement() {
    List<CatalogAssetRow> rows = new ArrayList<>();
    for (int index = 0; index < 1_200; index++) {
      rows.add(row("table:7:shop..t" + index, "hash-" + index, null));
    }

    HarvestBatchResult result = repository.write(command(ProviderType.HARVESTED, rows));

    assertThat(result.newCount()).isEqualTo(1_200);
    assertThat(upsertChunks).extracting(List::size).containsExactly(500, 500, 200);
    assertThat(touchedChunks).extracting(List::size).containsExactly(500, 500, 200);
  }

  @Test
  void assetIdsComeBackOnlyForChangedRowsSoParentsCanStillBeResolvedByReading() {
    givenStored("table:7:shop..a", "hash-a", null, null);

    HarvestBatchResult result =
        repository.write(
            command(ProviderType.HARVESTED, row("table:7:shop..a", "hash-a", null),
                row("table:7:shop..new", "hash-new", null)));

    assertThat(result.assetIds()).containsOnlyKeys("table:7:shop..new");

    // 挂在空父级上是静默的：所以父 id 一律由 idsOf 回读，UNCHANGED 的行也在内。
    Map<String, Long> ids = repository.idsOf(PROJECT, List.of("table:7:shop..a", "table:7:shop..new", "gone"));
    assertThat(ids).containsOnlyKeys("table:7:shop..a", "table:7:shop..new");
    assertThat(ids.get("table:7:shop..a")).isNotNull();
  }

  @Test
  void idsOfIsReadOnlyAndDeduplicatesAcrossChunks() {
    givenStored("database:7:shop", null, null, null);
    givenStored("datasource:7", null, null, null);

    Map<String, Long> ids =
        repository.idsOf(
            PROJECT,
            List.of("datasource:7", "database:7:shop", "database:7:shop", "not-there"));

    assertThat(ids).containsOnlyKeys("datasource:7", "database:7:shop");
    verify(catalogMapper, never()).upsertAssets(any());
    verify(catalogMapper, never()).touchLastCollected(anyLong(), any(), any());
    verify(changeMapper, never()).insert(any(MdChangePO.class));
  }

  @Test
  void anEmptyBatchCostsNothingAndIsNotCountedAsFailure() {
    HarvestBatchResult result = repository.write(command(ProviderType.HARVESTED, List.of()));

    assertThat(result).isEqualTo(HarvestBatchResult.empty());
    verify(catalogMapper, never()).selectStates(any(), any());
    verify(catalogMapper, never()).upsertAssets(any());
  }

  @Test
  void markGoneSoftDeletesInChunksAndLeavesOneChangeRowPerCandidate() {
    List<GoneRow> candidates = new ArrayList<>();
    for (int index = 0; index < 1_200; index++) {
      boolean column = index % 3 == 0;
      candidates.add(
          presenceGoneRow(
              (column ? "column:7:shop..t" : "table:7:shop..t") + index, column ? "column" : "table"));
    }

    int applied = repository.markGone(goneCommand(candidates));

    assertThat(applied).isEqualTo(1_200);
    assertThat(goneChunks).extracting(List::size).containsExactly(500, 500, 200);
    assertThat(changes).hasSize(1_200);
    assertThat(changes).allSatisfy(change -> {
      assertThat(change.getChangeType()).isEqualTo(ChangeType.GONE.name());
      assertThat(change.getProviderType()).isEqualTo(ProviderType.HARVESTED.name());
      // before 是消失前的结构指纹：源侧此后再也读不到，这是最后一份可比对现场。
      assertThat(change.getBeforeValue()).startsWith("hash-");
      assertThat(change.getAfterValue()).isNull();
      assertThat(change.getAssetId()).isNotNull();
    });
    // type_name 跟着候选行来：一次软删可以跨表/列/库三类实体，而命令只有一个类型可用。
    assertThat(changes).extracting(MdChangePO::getTypeName).contains("table", "column");
  }

  @Test
  void aDryRunOfTheSoftDeleteIssuesNoStatementAtAll() {
    List<GoneRow> candidates =
        List.of(presenceGoneRow("table:7:shop..a", "table"), presenceGoneRow("column:7:shop..a.id", "column"));

    int applied = repository.markGone(new GoneCommand(PROJECT, 501L, "root", ROUND_ONE, true, ProviderType.HARVESTED, candidates));

    // 预演要看得见"会删谁"，代价不能是先把实体删了（判定方把候选集放在结论里交回）。
    assertThat(applied).isZero();
    verify(catalogMapper, never()).markGone(any(), any());
    verify(changeMapper, never()).insert(any(MdChangePO.class));
  }

  @Test
  void aRowCountShortfallIsLeftAsAnObservationNotRetried() {
    markGoneRowsPerCall = 1;
    List<GoneRow> candidates =
        List.of(presenceGoneRow("table:7:shop..a", "table"), presenceGoneRow("table:7:shop..b", "table"));

    int applied = repository.markGone(goneCommand(candidates));

    // 差额只可能是并发轮次已先行确认了同一件事（实体确实不在场）；重试既无意义也可能覆盖别人的现场。
    // 证据按候选集留，不因行数短少而缺失。
    assertThat(applied).isEqualTo(1);
    assertThat(changes).hasSize(2);
    assertThat(goneChunks).containsExactly(
        List.of(candidates.get(0).row().getId(), candidates.get(1).row().getId()));
  }

  @Test
  void presenceScanReportsTruncationBecauseTheDenominatorWouldBeASilentLie() {
    when(catalogMapper.selectPresenceRows(eq(PROJECT), eq("7"), anyInt()))
        .thenReturn(List.of(presenceRow("table:7:shop..a"), presenceRow("table:7:shop..b")));

    assertThat(repository.presenceScan(PROJECT, "7", 2).truncated()).isTrue();
    assertThat(repository.presenceScan(PROJECT, "7", 3).truncated()).isFalse();
    assertThat(repository.presenceScan(PROJECT, "7", 3).rows())
        .extracting(CatalogPresenceRow::getAssetKey)
        .containsExactly("table:7:shop..a", "table:7:shop..b");

    when(catalogMapper.selectPresenceRows(eq(PROJECT), eq("8"), anyInt())).thenReturn(null);
    assertThat(repository.presenceScan(PROJECT, "8", 2_000).rows()).isEmpty();
    // 0 行不是"触顶"：空在场集由判定方的空 seen 闸拦下，而不是这里再算一次比例。
    assertThat(repository.presenceScan(PROJECT, "8", 2_000).truncated()).isFalse();
  }

  private GoneCommand goneCommand(List<GoneRow> rows) {
    return new GoneCommand(PROJECT, 501L, "root", ROUND_ONE, false, ProviderType.HARVESTED, rows);
  }

  private static GoneRow presenceGoneRow(String assetKey, String typeName) {
    CatalogPresenceRow row = presenceRow(assetKey);
    row.setContentHash("hash-" + assetKey);
    return new GoneRow(row, typeName);
  }

  private static CatalogPresenceRow presenceRow(String assetKey) {
    CatalogPresenceRow row = new CatalogPresenceRow();
    row.setId(900L + Math.abs(assetKey.hashCode() % 300));
    row.setAssetKey(assetKey);
    row.setAssetType("TABLE");
    row.setDatabaseName("shop");
    row.setSchemaName(null);
    row.setTableName(assetKey.substring(assetKey.lastIndexOf('.') + 1));
    row.setLastCollectAt(ROUND_ONE);
    return row;
  }

  private void givenStored(String key, String contentHash, String sourceHash, LocalDateTime goneAt) {
    store.put(key, state(key, contentHash, sourceHash, goneAt));
  }

  private static CatalogAssetState state(
      String key, String contentHash, String sourceHash, LocalDateTime goneAt) {
    CatalogAssetState state = new CatalogAssetState();
    state.setId(700L + Math.abs(key.hashCode() % 200));
    state.setAssetKey(key);
    state.setProviderType(ProviderType.HARVESTED.name());
    state.setContentHash(contentHash);
    state.setSourceHash(sourceHash);
    state.setGoneAt(goneAt);
    return state;
  }

  private static CatalogAssetState copy(CatalogAssetState source) {
    CatalogAssetState copy = new CatalogAssetState();
    copy.setId(source.getId());
    copy.setAssetKey(source.getAssetKey());
    copy.setProviderType(source.getProviderType());
    copy.setContentHash(source.getContentHash());
    copy.setSourceHash(source.getSourceHash());
    copy.setGoneAt(source.getGoneAt());
    return copy;
  }

  private static CatalogAssetRow row(String assetKey, String contentHash, String sourceHash) {
    CatalogAssetRow row = new CatalogAssetRow();
    row.setProjectId(PROJECT);
    row.setAssetKey(assetKey);
    row.setAssetType("TABLE");
    row.setName(assetKey);
    row.setProviderType(ProviderType.HARVESTED.name());
    row.setContentHash(contentHash);
    row.setSourceHash(sourceHash);
    return row;
  }

  private static CatalogAssetRow rowWithHashes(String assetKey, String contentHash, String sourceHash) {
    CatalogAssetRow row = row(assetKey, contentHash, sourceHash);
    row.setProviderType(ProviderType.REGISTERED.name());
    return row;
  }

  private static BatchCommand command(ProviderType providerType, CatalogAssetRow... rows) {
    return command(providerType, false, List.of(rows));
  }

  private static BatchCommand command(
      ProviderType providerType, boolean dryRun, CatalogAssetRow... rows) {
    return command(providerType, dryRun, List.of(rows));
  }

  private static BatchCommand command(
      ProviderType providerType, boolean dryRun, List<CatalogAssetRow> rows) {
    return new BatchCommand(
        PROJECT, "table", providerType, 501L, "root", ROUND_ONE, dryRun, rows);
  }

  private static BatchCommand command(ProviderType providerType, List<CatalogAssetRow> rows) {
    return command(providerType, false, rows);
  }
}
