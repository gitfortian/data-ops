package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.security.dao.model.DsecAccessPolicyPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for data-level access policies. */
@Mapper
public interface AccessPolicyMapper extends BaseMapper<DsecAccessPolicyPO> {}
