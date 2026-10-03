package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.metric.dao.model.MetricVersionPO;
import org.apache.ibatis.annotations.Mapper;

/** 指标版本历史 Mapper。 */
@Mapper
public interface MetricVersionMapper extends BaseMapper<MetricVersionPO> {
}
