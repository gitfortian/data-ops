package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.api.MetricApi;
import io.yak.ops.business.metric.dao.mapper.MetricCompositionMapper;
import io.yak.ops.common.bean.po.metric.MetricCompositionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 复合指标组成仓储适配。 */
@Repository
public class MetricCompositionRepositoryAdapter implements MetricCompositionRepository {

  private final MetricCompositionMapper mapper;
  private final CurrentProject currentProject;

  public MetricCompositionRepositoryAdapter(
      MetricCompositionMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public void replaceCompositions(Long metricId, List<MetricApi.CompositionItem> compositions) {
    Long projectId = currentProject.requireProjectId();
    mapper.delete(new LambdaQueryWrapper<MetricCompositionPO>()
        .eq(MetricCompositionPO::getProjectId, projectId)
        .eq(MetricCompositionPO::getMetricId, metricId));
    if (compositions == null || compositions.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (MetricApi.CompositionItem item : compositions) {
      MetricCompositionPO po = new MetricCompositionPO();
      po.setProjectId(projectId);
      po.setMetricId(metricId);
      // 符号项(运算符/括号)无子指标,以 0 占位(token 流契约,REQUIREMENTS 决策 1)
      po.setSubMetricId(item.subMetricId() != null ? item.subMetricId() : 0L);
      po.setOperator(item.operator());
      po.setExpression(item.expression());
      po.setSortOrder(item.sortOrder());
      po.setCreateTime(now);
      mapper.insert(po);
    }
  }

  @Override
  public void deleteByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    mapper.delete(new LambdaQueryWrapper<MetricCompositionPO>()
        .eq(MetricCompositionPO::getProjectId, projectId)
        .eq(MetricCompositionPO::getMetricId, metricId));
  }

  @Override
  public List<MetricCompositionPO> listByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(new LambdaQueryWrapper<MetricCompositionPO>()
        .eq(MetricCompositionPO::getProjectId, projectId)
        .eq(MetricCompositionPO::getMetricId, metricId)
        .orderByAsc(MetricCompositionPO::getSortOrder));
  }

  @Override
  public long countBySubMetric(Long subMetricId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectCount(new LambdaQueryWrapper<MetricCompositionPO>()
        .eq(MetricCompositionPO::getProjectId, projectId)
        .eq(MetricCompositionPO::getSubMetricId, subMetricId));
  }
}
