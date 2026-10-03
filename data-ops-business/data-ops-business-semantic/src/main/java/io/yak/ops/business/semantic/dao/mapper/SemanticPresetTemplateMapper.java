package io.yak.ops.business.semantic.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.semantic.dao.model.SemanticPresetTemplatePO;
import org.apache.ibatis.annotations.Mapper;

/** MyBatis mapper for the platform preset template table. */
@Mapper
public interface SemanticPresetTemplateMapper extends BaseMapper<SemanticPresetTemplatePO> {
}
