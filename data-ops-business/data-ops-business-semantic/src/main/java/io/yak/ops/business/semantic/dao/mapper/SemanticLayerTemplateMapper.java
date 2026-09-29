package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.semantic.SemanticLayerTemplatePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the platform layer template. */
@Mapper
public interface SemanticLayerTemplateMapper extends BaseMapper<SemanticLayerTemplatePO> {
}
