package io.yak.ops.business.lineage.dao.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Complex lineage read projections implemented in Mapper XML. */
@Mapper
public interface LineageQueryMapper {

  List<Long> selectAssetIdsByEvidence(
      @Param("sourceType") String sourceType,
      @Param("sourceId") String sourceId,
      @Param("projectId") Long projectId);
}
