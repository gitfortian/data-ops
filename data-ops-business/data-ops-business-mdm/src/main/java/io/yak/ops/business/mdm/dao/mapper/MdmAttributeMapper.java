package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.mdm.MdmAttributePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis mapper for the master data attribute. */
@Mapper
public interface MdmAttributeMapper extends BaseMapper<MdmAttributePO> {

  @Select("""
      SELECT CASE WHEN
        EXISTS (SELECT 1 FROM yak_mdm_record WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_source
          WHERE project_id = #{projectId} AND entity_id = #{entityId}
            AND JSON_CONTAINS_PATH(COALESCE(field_mapping, JSON_OBJECT()), 'one', CONCAT('$.\"', #{attributeCode}, '\"'))) OR
        EXISTS (SELECT 1 FROM yak_mdm_clean_rule
          WHERE project_id = #{projectId} AND entity_id = #{entityId}
            AND (JSON_SEARCH(rule_expr, 'one', #{attributeCode}, NULL, '$.fields[*].attrCode') IS NOT NULL
              OR JSON_CONTAINS_PATH(rule_expr, 'one', CONCAT('$.fields.\"', #{attributeCode}, '\"'))
              OR JSON_CONTAINS_PATH(rule_expr, 'one', CONCAT('$.defaults.\"', #{attributeCode}, '\"'))))
      THEN 1 ELSE 0 END
      """)
  boolean hasReferences(
      @Param("projectId") Long projectId,
      @Param("entityId") Long entityId,
      @Param("attributeCode") String attributeCode);
}
