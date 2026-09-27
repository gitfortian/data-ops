package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metric.MetricValidationEvidencePO;
import org.apache.ibatis.annotations.Mapper;

/** 指标定义校验证据 Mapper。 */
@Mapper
public interface MetricValidationEvidenceMapper extends BaseMapper<MetricValidationEvidencePO> {
}
