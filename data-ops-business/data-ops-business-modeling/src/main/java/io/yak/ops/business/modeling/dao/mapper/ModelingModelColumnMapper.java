package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.dao.model.ModelingModelColumnPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for modeling model columns. */
@Mapper
public interface ModelingModelColumnMapper extends BaseMapper<ModelingModelColumnPO> {
}
