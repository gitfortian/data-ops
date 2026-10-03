package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.dao.model.ModelingDirectoryPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for modeling catalog directories. */
@Mapper
public interface ModelingDirectoryMapper extends BaseMapper<ModelingDirectoryPO> {
}
