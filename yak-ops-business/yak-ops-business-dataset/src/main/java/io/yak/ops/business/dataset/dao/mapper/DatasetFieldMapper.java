package io.yak.ops.business.dataset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DatasetFieldMapper extends BaseMapper<DatasetFieldPO> {

  @Insert({
      "<script>",
      "INSERT INTO yak_dataset_field ",
      "(field_id, version_id, physical_name, display_name, data_type, `nullable`, description, default_role, sort_order) VALUES ",
      "<foreach collection='fields' item='field' separator=','>",
      "(#{field.fieldId}, #{field.versionId}, #{field.physicalName}, #{field.displayName}, ",
      "#{field.dataType}, #{field.nullable}, #{field.description}, #{field.defaultRole}, #{field.sortOrder})",
      "</foreach>",
      "</script>"
  })
  int insertBatch(@Param("fields") List<DatasetFieldPO> fields);

  @Select(
      "SELECT f.* FROM yak_dataset_field f "
          + "JOIN yak_dataset_version v ON v.id = f.version_id "
          + "JOIN yak_dataset d ON d.id = v.dataset_id "
          + "WHERE d.project_id = #{projectId} AND f.version_id = #{versionId} "
          + "ORDER BY f.sort_order ASC, f.physical_name ASC")
  List<DatasetFieldPO> selectProjectFields(
      @Param("projectId") Long projectId, @Param("versionId") long versionId);

  @Select({
      "<script>",
      "SELECT f.* FROM yak_dataset_field f ",
      "JOIN yak_dataset_version v ON v.id = f.version_id ",
      "JOIN yak_dataset d ON d.id = v.dataset_id ",
      "WHERE d.project_id = #{projectId} AND f.version_id IN ",
      "<foreach collection='versionIds' item='versionId' open='(' separator=',' close=')'>",
      "#{versionId}",
      "</foreach>",
      "ORDER BY f.version_id ASC, f.sort_order ASC, f.physical_name ASC",
      "</script>"
  })
  List<DatasetFieldPO> selectProjectFieldsByVersionIds(
      @Param("projectId") Long projectId,
      @Param("versionIds") Collection<Long> versionIds);
}
