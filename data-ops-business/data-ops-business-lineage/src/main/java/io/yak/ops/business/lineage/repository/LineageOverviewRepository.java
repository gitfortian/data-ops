package io.yak.ops.business.lineage.repository;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Read projection contract used by the bounded lineage overview. */
public interface LineageOverviewRepository {

  long countAssets(LineageAssetType assetType);

  long countAssetsUpdatedBetween(Instant start, Instant end);

  long countRelations();

  List<LineageRelation> findRecentRelations(int limit);

  List<LineageAsset> findAssetsByIds(Set<Long> assetIds);
}
