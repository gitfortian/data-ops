package io.yak.ops.business.security.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.security.DsecAccessLogPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the data access audit trail. */
@Mapper
public interface AccessLogMapper extends BaseMapper<DsecAccessLogPO> {}
