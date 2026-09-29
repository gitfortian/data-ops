package io.yak.ops.business.lineage.dao.mapper;

import io.yak.ops.business.lineage.dao.model.LineageAssetPO;
import io.yak.ops.business.lineage.dao.model.LineageRelationPO;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Complex and atomic lineage writes that are intentionally implemented in Mapper XML. */
@Mapper
public interface LineageWriteMapper {

  int claimLegacyAssetProject(
      @Param("assetKey") String assetKey,
      @Param("projectId") Long projectId);

  int upsertAsset(@Param("row") LineageAssetPO row);

  int upsertRelation(@Param("row") LineageRelationPO row);

  int upsertAssets(@Param("rows") List<LineageAssetPO> rows);

  int upsertRelations(@Param("rows") List<LineageRelationPO> rows);

  LineageAssetPO selectAssetForUpdate(
      @Param("assetKey") String assetKey,
      @Param("projectId") Long projectId);

  int deleteUnreferencedOwnedAssets(
      @Param("assetIds") Set<Long> assetIds,
      @Param("ownerType") String ownerType,
      @Param("ownerId") String ownerId,
      @Param("projectId") Long projectId);
}
