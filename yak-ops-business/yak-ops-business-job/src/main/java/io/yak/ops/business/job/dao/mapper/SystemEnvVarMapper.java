package io.yak.ops.business.job.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.job.SystemEnvVarPO;
import org.apache.ibatis.annotations.Mapper;

/** 系统环境变量 MyBatis 映射接口。 */
@Mapper
public interface SystemEnvVarMapper extends BaseMapper<SystemEnvVarPO> {
}
