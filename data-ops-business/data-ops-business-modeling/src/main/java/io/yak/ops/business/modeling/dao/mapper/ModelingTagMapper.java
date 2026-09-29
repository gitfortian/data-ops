package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.modeling.ModelingTagPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for modeling catalog tags. */
@Mapper
public interface ModelingTagMapper extends BaseMapper<ModelingTagPO> {
}
