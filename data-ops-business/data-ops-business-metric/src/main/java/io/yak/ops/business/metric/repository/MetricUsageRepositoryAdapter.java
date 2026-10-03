package io.yak.ops.business.metric.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.dao.mapper.MetricUsageMapper;
import io.yak.ops.business.metric.domain.MetricUsage;
import io.yak.ops.business.metric.dao.model.MetricUsagePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** MyBatis adapter for Metric reference usage. */
@Repository
public class MetricUsageRepositoryAdapter implements MetricUsageRepository {

  private final MetricUsageMapper mapper;
  private final CurrentProject currentProject;

  public MetricUsageRepositoryAdapter(MetricUsageMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public void append(Long projectId, MetricUsage usage) {
    MetricUsagePO po = new MetricUsagePO();
    po.setProjectId(projectId == null ? currentProject.requireProjectId() : projectId);
    po.setMetricId(usage.metricId());
    po.setMetricVersion(usage.metricVersion());
    po.setUsageType(usage.usageType());
    po.setUsageId(usage.usageId());
    po.setUsageName(usage.usageName());
    po.setCreateTime(usage.createdAt() == null ? LocalDateTime.now() : usage.createdAt());
    mapper.insert(po);
  }

  @Override
  public void deleteForConsumer(String usageType, Long usageId) {
    mapper.delete(consumerScope(usageType, usageId));
  }

  @Override
  public List<MetricUsage> listForConsumer(String usageType, Long usageId) {
    return mapper.selectList(consumerScope(usageType, usageId)).stream()
        .map(MetricUsageRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<MetricUsage> listByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(new LambdaQueryWrapper<MetricUsagePO>()
        .eq(MetricUsagePO::getProjectId, projectId)
        .eq(MetricUsagePO::getMetricId, metricId)
        .orderByDesc(MetricUsagePO::getCreateTime))
        .stream().map(MetricUsageRepositoryAdapter::toDomain).toList();
  }

  @Override
  public long countByMetric(Long metricId) {
    return mapper.countByMetric(currentProject.requireProjectId(), metricId);
  }

  @Override
  public List<UsageTypeCount> countGroupByType(Long metricId) {
    return mapper.countGroupByType(currentProject.requireProjectId(), metricId).stream()
        .map(row -> new UsageTypeCount(
            String.valueOf(row.get("usageType")), ((Number) row.get("cnt")).longValue()))
        .toList();
  }

  @Override
  public void deleteByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    mapper.delete(new LambdaQueryWrapper<MetricUsagePO>()
        .eq(MetricUsagePO::getProjectId, projectId)
        .eq(MetricUsagePO::getMetricId, metricId));
  }

  private LambdaQueryWrapper<MetricUsagePO> consumerScope(String usageType, Long usageId) {
    return new LambdaQueryWrapper<MetricUsagePO>()
        .eq(MetricUsagePO::getProjectId, currentProject.requireProjectId())
        .eq(MetricUsagePO::getUsageType, usageType)
        .eq(MetricUsagePO::getUsageId, usageId);
  }

  private static MetricUsage toDomain(MetricUsagePO po) {
    return new MetricUsage(po.getId(), po.getMetricId(), po.getMetricVersion(), po.getUsageType(),
        po.getUsageId(), po.getUsageName(), po.getCreateTime());
  }
}
