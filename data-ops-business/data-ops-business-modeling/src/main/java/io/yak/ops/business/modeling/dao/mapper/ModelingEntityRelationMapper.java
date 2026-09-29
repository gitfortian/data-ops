package io.yak.ops.business.modeling.dao.mapper;

import io.yak.ops.business.modeling.domain.EntityRelation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ModelingEntityRelationMapper {

  EntityRelation selectById(Long id);

  int insert(EntityRelation relation);

  int update(EntityRelation relation);
}
