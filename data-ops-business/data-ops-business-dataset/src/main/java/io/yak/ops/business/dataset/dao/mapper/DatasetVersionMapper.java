package io.yak.ops.business.dataset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DatasetVersionMapper extends BaseMapper<DatasetVersionPO> {

  @Select(
      "SELECT v.* FROM yak_dataset_version v "
          + "JOIN yak_dataset d ON d.id = v.dataset_id "
          + "WHERE d.project_id = #{projectId} AND v.id = #{versionId} LIMIT 1")
  DatasetVersionPO selectProjectVersion(
      @Param("projectId") Long projectId, @Param("versionId") long versionId);

  @Select(
      "SELECT v.* FROM yak_dataset_version v "
          + "JOIN yak_dataset d ON d.id = v.dataset_id "
          + "WHERE d.project_id = #{projectId} AND v.dataset_id = #{datasetId} "
          + "AND v.version_no = #{versionNo} LIMIT 1")
  DatasetVersionPO selectProjectVersionNo(
      @Param("projectId") Long projectId,
      @Param("datasetId") long datasetId,
      @Param("versionNo") int versionNo);

  @Select(
      "SELECT v.* FROM yak_dataset_version v "
          + "JOIN yak_dataset d ON d.id = v.dataset_id "
          + "WHERE d.project_id = #{projectId} AND v.dataset_id = #{datasetId} "
          + "ORDER BY v.version_no DESC")
  List<DatasetVersionPO> selectProjectVersions(
      @Param("projectId") Long projectId, @Param("datasetId") long datasetId);

  @Select({
      "<script>",
      "SELECT v.* FROM yak_dataset_version v ",
      "JOIN yak_dataset d ON d.id = v.dataset_id ",
      "WHERE d.project_id = #{projectId} AND v.id IN ",
      "<foreach collection='versionIds' item='versionId' open='(' separator=',' close=')'>",
      "#{versionId}",
      "</foreach>",
      "</script>"
  })
  List<DatasetVersionPO> selectProjectVersionsByIds(
      @Param("projectId") Long projectId,
      @Param("versionIds") Collection<Long> versionIds);

  @Select(
      "SELECT COALESCE(MAX(v.version_no), 0) + 1 FROM yak_dataset_version v "
          + "JOIN yak_dataset d ON d.id = v.dataset_id "
          + "WHERE d.project_id = #{projectId} AND v.dataset_id = #{datasetId}")
  Integer selectProjectNextVersionNo(
      @Param("projectId") Long projectId, @Param("datasetId") long datasetId);
}
