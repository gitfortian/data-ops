package io.yak.ops.business.lineage.query;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.repository.LineageOverviewRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Builds bounded aggregate data for lightweight lineage overview consumers. */
@Component
public class LineageOverviewReader {

  static final int MAX_RELATION_LIMIT = 20;

  private final LineageOverviewRepository repository;

  public LineageOverviewReader(LineageOverviewRepository repository) {
    this.repository = repository;
  }

  public LineageQueryService.Overview overview(
      Instant updatedFrom, Instant updatedTo, int relationLimit) {
    if (updatedFrom == null || updatedTo == null || !updatedFrom.isBefore(updatedTo)) {
      throw new IllegalArgumentException("血缘更新时间范围无效");
    }
    if (relationLimit < 1 || relationLimit > MAX_RELATION_LIMIT) {
      throw new IllegalArgumentException(
          "relationLimit 必须在 1 到 " + MAX_RELATION_LIMIT + " 之间");
    }

    List<LineageRelation> relations = repository.findRecentRelations(relationLimit);
    Set<Long> endpointIds = new LinkedHashSet<>();
    for (LineageRelation relation : relations) {
      endpointIds.add(relation.sourceAssetId());
      endpointIds.add(relation.targetAssetId());
    }
    List<LineageAsset> nodes = repository.findAssetsByIds(endpointIds);

    return new LineageQueryService.Overview(
        repository.countAssets(null),
        repository.countRelations(),
        repository.countAssetsUpdatedBetween(updatedFrom, updatedTo),
        repository.countAssets(LineageAssetType.TABLE),
        repository.countAssets(LineageAssetType.COLUMN),
        repository.countAssets(LineageAssetType.DATASET),
        nodes,
        relations);
  }
}
