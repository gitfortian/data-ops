package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.semantic.dao.model.SemanticFieldPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the standard field library. */
@Mapper
public interface SemanticFieldMapper extends BaseMapper<SemanticFieldPO> {
}
