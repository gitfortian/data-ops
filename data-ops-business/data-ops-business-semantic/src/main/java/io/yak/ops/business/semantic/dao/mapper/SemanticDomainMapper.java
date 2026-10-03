package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.semantic.dao.model.SemanticDomainPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the business domain tree. */
@Mapper
public interface SemanticDomainMapper extends BaseMapper<SemanticDomainPO> {
}
