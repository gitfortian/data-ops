package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metric.MetricTagPO;
import org.apache.ibatis.annotations.Mapper;

/** 指标标签 Mapper。 */
@Mapper
public interface MetricTagMapper extends BaseMapper<MetricTagPO> {
}
