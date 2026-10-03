package io.yak.ops.business.mdm.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.mdm.dao.model.MdmMergeLogPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** MyBatis mapper for the master data merge log. */
@Mapper
public interface MdmMergeLogMapper extends BaseMapper<MdmMergeLogPO> {

  @Select("SELECT EXISTS (SELECT 1 FROM yak_mdm_merge_log "
      + "WHERE project_id = #{projectId} AND rule_id = #{ruleId})")
  boolean existsByRuleId(@Param("projectId") Long projectId, @Param("ruleId") Long ruleId);
}
