package io.yak.ops.business.lineage.dao.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Complex lineage read projections.
 *
 * <p>原 XML 已移除；{@code UNION} 两侧都要带同一组可选 project 谓词，Wrapper 无法表达，
 * SQL 保留在注解里（{@code <script>} 承载原有的动态条件）。
 */
@Mapper
public interface LineageQueryMapper {

  @Select(
      """
      <script>
      SELECT source_asset_id AS asset_id
      FROM yak_metadata_relation
      WHERE source_type = #{sourceType} AND source_id = #{sourceId}
      <if test="projectId != null">
          AND project_id = #{projectId}
      </if>
      UNION
      SELECT target_asset_id AS asset_id
      FROM yak_metadata_relation
      WHERE source_type = #{sourceType} AND source_id = #{sourceId}
      <if test="projectId != null">
          AND project_id = #{projectId}
      </if>
      </script>
      """)
  List<Long> selectAssetIdsByEvidence(
      @Param("sourceType") String sourceType,
      @Param("sourceId") String sourceId,
      @Param("projectId") Long projectId);
}
