package io.yak.ops.business.modeling.dao.mapper;

import io.yak.ops.business.modeling.domain.LogicalModel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelingLogicalModelMapper {

  LogicalModel selectById(Long id);

  int insert(LogicalModel model);

  int update(LogicalModel model);
}
