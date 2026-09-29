package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metric.MetricDependencyPO;
import org.apache.ibatis.annotations.Mapper;

/** 指标血缘登记 Mapper。 */
@Mapper
public interface MetricDependencyMapper extends BaseMapper<MetricDependencyPO> {
}
