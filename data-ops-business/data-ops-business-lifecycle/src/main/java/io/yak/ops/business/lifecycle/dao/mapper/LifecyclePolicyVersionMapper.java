package io.yak.ops.business.lifecycle.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.lifecycle.dao.model.LifecyclePolicyVersionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LifecyclePolicyVersionMapper extends BaseMapper<LifecyclePolicyVersionPO> {

  /** 版本号分配统一口径(C2):SQL MAX+1,不使用 selectCount。 */
  @Select("SELECT COALESCE(MAX(version_no), 0) + 1 FROM yak_lc_policy_version WHERE policy_id = #{policyId}")
  int nextVersionNo(@Param("policyId") Long policyId);
}
