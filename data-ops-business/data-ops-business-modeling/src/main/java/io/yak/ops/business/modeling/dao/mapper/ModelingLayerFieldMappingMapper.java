package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.dao.model.ModelingLayerFieldMappingPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for layer-field mappings. */
@Mapper
public interface ModelingLayerFieldMappingMapper
    extends BaseMapper<ModelingLayerFieldMappingPO> {
}
