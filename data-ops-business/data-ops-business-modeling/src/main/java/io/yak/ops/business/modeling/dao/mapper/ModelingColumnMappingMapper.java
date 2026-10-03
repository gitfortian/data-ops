package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.dao.model.ModelingColumnMappingPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for column source mappings. */
@Mapper
public interface ModelingColumnMappingMapper extends BaseMapper<ModelingColumnMappingPO> {
}
