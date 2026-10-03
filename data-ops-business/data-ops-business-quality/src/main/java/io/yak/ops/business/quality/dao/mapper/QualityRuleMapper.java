package io.yak.ops.business.quality.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.quality.dao.model.QualityRulePO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QualityRuleMapper extends BaseMapper<QualityRulePO> {}
