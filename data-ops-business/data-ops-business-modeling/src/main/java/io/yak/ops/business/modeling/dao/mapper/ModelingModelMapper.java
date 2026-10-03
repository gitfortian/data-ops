package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.dao.model.ModelingModelPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the modeling model table. */
@Mapper
public interface ModelingModelMapper extends BaseMapper<ModelingModelPO> {
}
