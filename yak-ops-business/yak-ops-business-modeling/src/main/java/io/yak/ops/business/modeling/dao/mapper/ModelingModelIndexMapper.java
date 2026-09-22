package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.modeling.ModelingModelIndexPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for modeling model indexes. */
@Mapper
public interface ModelingModelIndexMapper extends BaseMapper<ModelingModelIndexPO> {
}
