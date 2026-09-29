package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.domain.LogicalAttributeMapping;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelingLogicalAttributeMappingMapper extends BaseMapper<LogicalAttributeMapping> {
}
