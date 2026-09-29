package io.yak.ops.business.lineage.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationType;
import io.yak.ops.business.lineage.repository.LineageOverviewRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LineageOverviewReaderTest {

  @Test
  void summarizesCountsAndKeepsRecentRelationsBounded() {
    Instant now = Instant.parse("2026-08-26T02:00:00Z");
    LineageAsset table = asset(1L, "ods_order", LineageAssetType.TABLE, now);
    LineageAsset column = asset(2L, "order_id", LineageAssetType.COLUMN, now);
    LineageAsset dataset = asset(3L, "订单数据集", LineageAssetType.DATASET, now);
    LineageRelation first = relation(11L, table.id(), dataset.id(), now.minusSeconds(60));
    LineageRelation second = relation(12L, column.id(), dataset.id(), now);
    FakeOverviewRepository repository =
        new FakeOverviewRepository(List.of(table, column, dataset), List.of(first, second));

    LineageOverviewReader reader = new LineageOverviewReader(repository);
    LineageQueryService.Overview overview =
        reader.overview(
            Instant.parse("2026-08-26T00:00:00Z"),
            Instant.parse("2026-08-27T00:00:00Z"),
            1);

    assertEquals(3L, overview.assetCount());
    assertEquals(2L, overview.relationCount());
    assertEquals(3L, overview.updatedAssetCount());
    assertEquals(1L, overview.tableAssetCount());
    assertEquals(1L, overview.columnAssetCount());
    assertEquals(1L, overview.datasetAssetCount());
    assertEquals(1, overview.relations().size());
    assertEquals(12L, overview.relations().get(0).id());
    assertEquals(Set.of(2L, 3L), overview.nodes().stream().map(LineageAsset::id).collect(java.util.stream.Collectors.toSet()));
  }

  @Test
  void rejectsInvalidOverviewWindowAndLimit() {
    LineageOverviewReader reader =
        new LineageOverviewReader(new FakeOverviewRepository(List.of(), List.of()));
    Instant point = Instant.parse("2026-08-26T00:00:00Z");

    assertThrows(IllegalArgumentException.class, () -> reader.overview(point, point, 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> reader.overview(point, point.plusSeconds(60), 0));
  }

  private static LineageAsset asset(
      long id, String name, LineageAssetType type, Instant updatedAt) {
    return new LineageAsset(
        id,
        type.name().toLowerCase() + ":" + id,
        type,
        name,
        "TEST",
        String.valueOf(id),
        null,
        null,
        null,
        null,
        type == LineageAssetType.TABLE ? name : null,
        type == LineageAssetType.COLUMN ? name : null,
        null,
        updatedAt.minusSeconds(60),
        updatedAt);
  }

  private static LineageRelation relation(
      long id, long sourceAssetId, long targetAssetId, Instant updatedAt) {
    return new LineageRelation(
        id,
        sourceAssetId,
        targetAssetId,
        LineageRelationType.DERIVES_FROM,
        "TEST",
        "case",
        null,
        BigDecimal.ONE,
        "v1",
        updatedAt,
        null,
        updatedAt.minusSeconds(60),
        updatedAt);
  }

  private static final class FakeOverviewRepository implements LineageOverviewRepository {
    private final List<LineageAsset> assets;
    private final List<LineageRelation> relations;

    private FakeOverviewRepository(
        List<LineageAsset> assets, List<LineageRelation> relations) {
      this.assets = assets;
      this.relations = relations;
    }

    @Override
    public long countAssets(LineageAssetType assetType) {
      return assets.stream()
          .filter(asset -> assetType == null || asset.assetType() == assetType)
          .count();
    }

    @Override
    public long countAssetsUpdatedBetween(Instant start, Instant end) {
      return assets.stream()
          .filter(asset -> asset.updateTime() != null)
          .filter(asset -> !asset.updateTime().isBefore(start) && asset.updateTime().isBefore(end))
          .count();
    }

    @Override
    public long countRelations() {
      return relations.size();
    }

    @Override
    public List<LineageRelation> findRecentRelations(int limit) {
      return relations.stream()
          .sorted(
              Comparator.comparing(LineageRelation::updateTime)
                  .reversed()
                  .thenComparing(LineageRelation::id, Comparator.reverseOrder()))
          .limit(limit)
          .toList();
    }

    @Override
    public List<LineageAsset> findAssetsByIds(Set<Long> assetIds) {
      return assets.stream().filter(asset -> assetIds.contains(asset.id())).toList();
    }
  }
}
