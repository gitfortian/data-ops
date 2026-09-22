package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.security.DsecSecurityLevelPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the security level dictionary. */
@Mapper
public interface SecurityLevelMapper extends BaseMapper<DsecSecurityLevelPO> {}
