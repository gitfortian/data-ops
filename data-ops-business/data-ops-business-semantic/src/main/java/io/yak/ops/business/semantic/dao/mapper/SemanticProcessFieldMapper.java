package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.semantic.dao.model.SemanticProcessFieldPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for process-field references. */
@Mapper
public interface SemanticProcessFieldMapper extends BaseMapper<SemanticProcessFieldPO> {
}
