package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.semantic.SemanticLayerPO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for warehouse layer config. */
@Mapper
public interface SemanticLayerMapper extends BaseMapper<SemanticLayerPO> {
}
