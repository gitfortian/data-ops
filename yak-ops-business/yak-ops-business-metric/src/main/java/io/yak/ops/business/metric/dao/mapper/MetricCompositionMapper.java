package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metric.MetricCompositionPO;
import org.apache.ibatis.annotations.Mapper;

/** 复合指标组成 Mapper。 */
@Mapper
public interface MetricCompositionMapper extends BaseMapper<MetricCompositionPO> {
}
