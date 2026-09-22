package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.catalog.MetricCatalogService;
import io.yak.ops.business.metric.dao.mapper.MetricVersionMapper;
import io.yak.ops.business.metric.domain.Metric;
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

  public MetricVersionRepositoryAdapter(
      MetricVersionMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public void saveSnapshot(Metric metric, String changeDesc, String operator) {
    Long projectId = currentProject.requireProjectId();
    MetricVersionPO po = new MetricVersionPO();
    po.setProjectId(projectId);
    po.setMetricId(metric.id());
    po.setVersion(metric.version());
    po.setSnapshot(MetricCatalogService.toJsonSnapshot(metric));
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
}
