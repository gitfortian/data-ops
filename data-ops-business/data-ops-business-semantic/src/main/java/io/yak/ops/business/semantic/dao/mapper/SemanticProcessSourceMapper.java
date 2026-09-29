package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.semantic.SemanticProcessSourcePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for process-source bindings. */
@Mapper
public interface SemanticProcessSourceMapper extends BaseMapper<SemanticProcessSourcePO> {
}
