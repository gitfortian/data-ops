package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.modeling.ModelingModelTagRelPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for model-tag relations. */
@Mapper
public interface ModelingModelTagRelMapper extends BaseMapper<ModelingModelTagRelPO> {
}
