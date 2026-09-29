package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metric.MetricPublicationEventPO;
import org.apache.ibatis.annotations.Mapper;

/** Append-only Metric publication event mapper. */
@Mapper
public interface MetricPublicationEventMapper extends BaseMapper<MetricPublicationEventPO> {
}
