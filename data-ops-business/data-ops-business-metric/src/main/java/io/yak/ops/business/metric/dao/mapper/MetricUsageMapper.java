package io.yak.ops.business.metric.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.metric.dao.model.MetricUsagePO;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 指标使用记录 Mapper。 */
@Mapper
public interface MetricUsageMapper extends BaseMapper<MetricUsagePO> {

  @Select("SELECT usage_type AS usageType, COUNT(*) AS cnt FROM yak_metric_usage "
      + "WHERE project_id = #{projectId} AND metric_id = #{metricId} "
      + "GROUP BY usage_type")
  List<Map<String, Object>> countGroupByType(
      @Param("projectId") Long projectId,
      @Param("metricId") Long metricId);

  @Select("SELECT COUNT(*) FROM yak_metric_usage "
      + "WHERE project_id = #{projectId} AND metric_id = #{metricId}")
  long countByMetric(
      @Param("projectId") Long projectId,
      @Param("metricId") Long metricId);
}
