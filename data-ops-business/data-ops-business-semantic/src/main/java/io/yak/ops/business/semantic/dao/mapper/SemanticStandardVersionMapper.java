package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.semantic.SemanticStandardVersionPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the standard version snapshot table. */
@Mapper
public interface SemanticStandardVersionMapper extends BaseMapper<SemanticStandardVersionPO> {
}
