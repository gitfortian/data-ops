package io.yak.ops.business.lineage.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.lineage.dao.model.LineageRelationPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis-Plus mapper for lineage relations. */
@Mapper
public interface LineageRelationMapper extends BaseMapper<LineageRelationPO> {
}
