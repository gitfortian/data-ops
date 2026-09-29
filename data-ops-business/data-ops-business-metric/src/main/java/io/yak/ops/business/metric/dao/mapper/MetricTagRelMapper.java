package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metric.MetricTagRelPO;
import org.apache.ibatis.annotations.Mapper;

/** 指标标签关联 Mapper。 */
@Mapper
public interface MetricTagRelMapper extends BaseMapper<MetricTagRelPO> {
}
