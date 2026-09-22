package io.yak.ops.business.lineage.dao;

import io.yak.ops.business.lineage.dao.model.LineageAssetPO;
import io.yak.ops.business.lineage.dao.model.LineageRelationPO;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

/** Database access boundary for lineage persistence. */
public interface LineageDao {

  int upsertAsset(LineageAssetPO asset);

  int upsertRelation(LineageRelationPO relation);

  List<LineageAssetPO> upsertAssets(List<LineageAssetPO> assets, int batchSize);

  void upsertRelations(List<LineageRelationPO> relations, int batchSize);

  int deleteRelationsByEvidence(String sourceType, String sourceId);

  Set<Long> selectAssetIdsByEvidence(String sourceType, String sourceId);

  int deleteUnreferencedOwnedAssets(Set<Long> assetIds, String ownerType, String ownerId);

  LineageAssetPO selectAssetForUpdate(String assetKey);

  LineageAssetPO selectAsset(long assetId);

  LineageAssetPO selectAssetByKey(String assetKey);

  LineageAssetPO selectAssetByKey(String assetKey, Long projectId);

  List<LineageAssetPO> selectAssets(AssetSearch query);

  long countAssets(String assetType);

  long countAssetsUpdatedBetween(Timestamp start, Timestamp end);

  long countRelations();

  List<LineageRelationPO> selectRecentRelations(int limit);

  List<LineageAssetPO> selectAssetsByIds(Set<Long> assetIds);

  LineageRelationPO selectRelationByIdentity(LineageRelationPO identity);

  List<LineageRelationPO> selectOutgoingRelations(Set<Long> sourceAssetIds);

  List<LineageRelationPO> selectIncomingRelations(Set<Long> targetAssetIds);

  record AssetSearch(String keyword, String assetType, int limit) {
  }
}
