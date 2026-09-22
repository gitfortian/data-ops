package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metric.MetricPO;
import org.apache.ibatis.annotations.Mapper;

/** 指标主表 Mapper。 */
@Mapper
public interface MetricMapper extends BaseMapper<MetricPO> {
}
