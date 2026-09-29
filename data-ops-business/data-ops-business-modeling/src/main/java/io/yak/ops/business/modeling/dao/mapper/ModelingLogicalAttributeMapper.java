package io.yak.ops.business.modeling.dao.mapper;

import io.yak.ops.business.modeling.domain.LogicalAttribute;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelingLogicalAttributeMapper {

  LogicalAttribute selectById(Long id);

  int insert(LogicalAttribute attribute);

  int update(LogicalAttribute attribute);
}
