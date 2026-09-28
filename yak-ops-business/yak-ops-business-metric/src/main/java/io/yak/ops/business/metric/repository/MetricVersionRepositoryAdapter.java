package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.dao.mapper.MetricVersionMapper;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.common.bean.po.metric.MetricCompositionPO;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 指标版本历史仓储适配。 */
@Repository
public class MetricVersionRepositoryAdapter implements MetricVersionRepository {

  private final MetricVersionMapper mapper;
  private final CurrentProject currentProject;
  private final MetricCompositionRepository compositionRepository;
  private final MetricDependencyRepository dependencyRepository;

  public MetricVersionRepositoryAdapter(
      MetricVersionMapper mapper,
      CurrentProject currentProject,
      MetricCompositionRepository compositionRepository,
      MetricDependencyRepository dependencyRepository) {
    this.mapper = mapper;
    this.currentProject = currentProject;
    this.compositionRepository = compositionRepository;
    this.dependencyRepository = dependencyRepository;
  }

  @Override
  public void saveSnapshot(Metric metric, String changeDesc, String operator) {
    Long projectId = currentProject.requireProjectId();
    MetricVersionPO po = new MetricVersionPO();
    po.setProjectId(projectId);
    po.setMetricId(metric.id());
    po.setVersion(metric.version());
    List<MetricCompositionPO> compositions = compositionRepository.listByMetric(metric.id());
    java.util.Map<Long, Integer> dependencyVersions = dependencyRepository.listByMetric(metric.id())
        .stream()
        .filter(dependency -> "REF_METRIC".equals(dependency.getDependencyType())
            || "COMPOSITION".equals(dependency.getDependencyType()))
        .filter(dependency -> dependency.getDependencyId() != null
            && dependency.getDependencyVersion() != null)
        .collect(java.util.stream.Collectors.toMap(
            io.yak.ops.common.bean.po.metric.MetricDependencyPO::getDependencyId,
            io.yak.ops.common.bean.po.metric.MetricDependencyPO::getDependencyVersion,
            (left, right) -> left));
    po.setSnapshot(MetricCatalogService.toJsonSnapshot(metric, compositions, dependencyVersions));
    po.setChangeDesc(changeDesc);
    po.setChangedBy(operator);
    po.setCreateTime(LocalDateTime.now());
    mapper.insert(po);
  }

  @Override
  public List<MetricVersionPO> listByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(new LambdaQueryWrapper<MetricVersionPO>()
        .eq(MetricVersionPO::getProjectId, projectId)
        .eq(MetricVersionPO::getMetricId, metricId)
        .orderByDesc(MetricVersionPO::getVersion));
  }

  @Override
  public MetricVersionPO findByMetricAndVersion(Long metricId, int version) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectOne(new LambdaQueryWrapper<MetricVersionPO>()
        .eq(MetricVersionPO::getProjectId, projectId)
        .eq(MetricVersionPO::getMetricId, metricId)
        .eq(MetricVersionPO::getVersion, version));
  }

  @Override
  public void deleteByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    mapper.delete(new LambdaQueryWrapper<MetricVersionPO>()
        .eq(MetricVersionPO::getProjectId, projectId)
        .eq(MetricVersionPO::getMetricId, metricId));
  }
}
