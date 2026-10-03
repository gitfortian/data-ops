package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.semantic.dao.model.SemanticProcessPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for business processes. */
@Mapper
public interface SemanticProcessMapper extends BaseMapper<SemanticProcessPO> {
}
