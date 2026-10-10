package io.yak.ops.business.modeling.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.modeling.dao.model.LogicalRelationPO;
import org.apache.ibatis.annotations.Mapper;

/** Mapper of existing logical-modeling tables. Consumers enforce scoped parents. */
@Mapper
public interface ModelingEntityRelationMapper extends BaseMapper<LogicalRelationPO> {
}
