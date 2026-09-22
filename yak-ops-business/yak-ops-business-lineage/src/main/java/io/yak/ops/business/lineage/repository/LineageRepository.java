package io.yak.ops.business.lineage.repository;

import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageAssetDraft;
import io.yak.ops.business.lineage.domain.LineageAssetType;
import io.yak.ops.business.lineage.domain.LineageRelation;
import io.yak.ops.business.lineage.domain.LineageRelationDraft;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Domain repository for metadata graph persistence. */
public interface LineageRepository {

  LineageAsset upsertAsset(LineageAssetDraft draft);

  LineageRelation upsertRelation(LineageRelationDraft draft);

  Map<String, LineageAsset> upsertAssets(List<LineageAssetDraft> drafts, int batchSize);

  void upsertRelations(List<LineageRelationDraft> drafts, int batchSize);

  int deleteRelationsByEvidence(String sourceType, String sourceId);

  Set<Long> findAssetIdsByEvidence(String sourceType, String sourceId);

  int deleteUnreferencedOwnedAssets(Set<Long> assetIds, String ownerType, String ownerId);

  Optional<LineageAsset> lockAssetByKey(String assetKey);

  Optional<LineageAsset> findAsset(long assetId);

  Optional<LineageAsset> findAssetByKey(String assetKey);

  List<LineageAsset> searchAssets(String keyword, LineageAssetType assetType, int limit);

  List<LineageAsset> findAssetsByIds(Set<Long> assetIds);

  List<LineageRelation> findOutgoingRelations(Set<Long> sourceAssetIds);

  List<LineageRelation> findIncomingRelations(Set<Long> targetAssetIds);
}
