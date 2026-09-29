package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.domain.LogicalModelVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelingLogicalModelVersionMapper extends BaseMapper<LogicalModelVersion> {
}
