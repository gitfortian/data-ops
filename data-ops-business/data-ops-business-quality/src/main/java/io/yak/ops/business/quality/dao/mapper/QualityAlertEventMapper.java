package io.yak.ops.business.quality.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.quality.QualityAlertEventPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QualityAlertEventMapper extends BaseMapper<QualityAlertEventPO> {}
