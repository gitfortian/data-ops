package io.yak.ops.business.modeling.dao.mapper;

import io.yak.ops.business.modeling.domain.LogicalEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelingLogicalEntityMapper {

  LogicalEntity selectById(Long id);

  int insert(LogicalEntity entity);

  int update(LogicalEntity entity);
}
