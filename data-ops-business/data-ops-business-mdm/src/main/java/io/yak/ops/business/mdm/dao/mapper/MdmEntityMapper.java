package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.mdm.dao.model.MdmEntityPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis mapper for the master data entity. */
@Mapper
public interface MdmEntityMapper extends BaseMapper<MdmEntityPO> {

  @Select("""
      SELECT CASE WHEN
        EXISTS (SELECT 1 FROM yak_mdm_attribute WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_source WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_collect_link WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_record WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_clean_rule WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_merge_log WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_distribution WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_subscription WHERE project_id = #{projectId} AND entity_id = #{entityId}) OR
        EXISTS (SELECT 1 FROM yak_mdm_change WHERE project_id = #{projectId} AND entity_id = #{entityId})
      THEN 1 ELSE 0 END
      """)
  boolean hasReferences(@Param("projectId") Long projectId, @Param("entityId") Long entityId);
}
