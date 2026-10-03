package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.dao.mapper.MetricDependencyMapper;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.dao.model.MetricDependencyPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 指标血缘登记仓储适配(服务层自动写入)。 */
@Repository
public class MetricDependencyRepositoryAdapter implements MetricDependencyRepository {

  private final MetricDependencyMapper mapper;
  private final CurrentProject currentProject;

  public MetricDependencyRepositoryAdapter(
      MetricDependencyMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public void syncDependencies(Metric metric, List<DependencySpec> dependencies) {
    Long projectId = currentProject.requireProjectId();
    Long metricId = metric.id();

    mapper.delete(new LambdaQueryWrapper<MetricDependencyPO>()
        .eq(MetricDependencyPO::getProjectId, projectId)
        .eq(MetricDependencyPO::getMetricId, metricId));

    if (dependencies == null || dependencies.isEmpty()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    for (DependencySpec spec : dependencies) {
      MetricDependencyPO po = new MetricDependencyPO();
      po.setProjectId(projectId);
      po.setMetricId(metricId);
      po.setDependencyType(spec.dependencyType());
      po.setDependencyId(spec.dependencyId());
      po.setDependencyCode(spec.dependencyCode());
      po.setDependencyVersion(spec.dependencyVersion());
      po.setCreateTime(now);
      mapper.insert(po);
    }
  }

  @Override
  public void deleteByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    mapper.delete(new LambdaQueryWrapper<MetricDependencyPO>()
        .eq(MetricDependencyPO::getProjectId, projectId)
        .eq(MetricDependencyPO::getMetricId, metricId));
  }

  @Override
  public List<MetricDependencyPO> listByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(new LambdaQueryWrapper<MetricDependencyPO>()
        .eq(MetricDependencyPO::getProjectId, projectId)
        .eq(MetricDependencyPO::getMetricId, metricId));
  }

  @Override
  public List<MetricDependencyPO> listByDependency(String dependencyType, Long dependencyId) {
    Long projectId = currentProject.requireProjectId();
    // 命中 idx_yak_metric_dep_target(dependency_type, dependency_id)
    return mapper.selectList(new LambdaQueryWrapper<MetricDependencyPO>()
        .eq(MetricDependencyPO::getProjectId, projectId)
        .eq(MetricDependencyPO::getDependencyType, dependencyType)
        .eq(MetricDependencyPO::getDependencyId, dependencyId));
  }

  @Override
  public long countByDependency(String dependencyType, Long dependencyId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectCount(new LambdaQueryWrapper<MetricDependencyPO>()
        .eq(MetricDependencyPO::getProjectId, projectId)
        .eq(MetricDependencyPO::getDependencyType, dependencyType)
        .eq(MetricDependencyPO::getDependencyId, dependencyId));
  }
}
