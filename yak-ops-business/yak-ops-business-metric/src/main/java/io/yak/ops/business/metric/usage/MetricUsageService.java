package io.yak.ops.business.metric.usage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.dao.mapper.MetricUsageMapper;
import io.yak.ops.common.bean.po.metric.MetricUsagePO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default usage SPI implementation (T52): project-scoped event inserts
 * plus server-side count aggregation. Fail-open: record() catches all
 * exceptions and logs them instead of propagating.
 */
@Component
@Slf4j
public class MetricUsageService implements MetricUsageApi {

  private final MetricUsageMapper mapper;
  private final CurrentProject currentProject;

  public MetricUsageService(MetricUsageMapper mapper, CurrentProject currentProject) {
    this.mapper = mapper;
    this.currentProject = currentProject;
  }

  @Override
  public void record(MetricUsageEvent event) {
    try {
      Long projectId = event.projectId() != null
          ? event.projectId()
          : currentProject.requireProjectId();
      MetricUsagePO po = new MetricUsagePO();
      po.setProjectId(projectId);
      po.setMetricId(event.metricId());
      po.setUsageType(event.usageType());
      po.setUsageId(event.usageId());
      po.setUsageName(event.usageName());
      po.setCreateTime(LocalDateTime.now());
      mapper.insert(po);
    } catch (RuntimeException e) {
      log.warn("Metric usage record failed (fail-open): metricId={}, usageType={}, error={}",
          event.metricId(), event.usageType(), e.getMessage());
    }
  }

  @Override
  public void revoke(String usageType, Long usageId) {
    try {
      mapper.delete(consumerScope(usageType, usageId));
    } catch (RuntimeException e) {
      log.warn("Metric usage revoke failed (fail-open): usageType={}, usageId={}, error={}",
          usageType, usageId, e.getMessage());
    }
  }

  @Override
  public void syncBindings(String usageType, Long usageId, String usageName, List<Long> metricIds) {
    try {
      Long projectId = currentProject.requireProjectId();
      mapper.delete(consumerScope(usageType, usageId));
      LocalDateTime now = LocalDateTime.now();
      for (Long metricId : metricIds == null ? List.<Long>of() : metricIds) {
        if (metricId == null) {
          continue;
        }
        MetricUsagePO po = new MetricUsagePO();
        po.setProjectId(projectId);
        po.setMetricId(metricId);
        po.setUsageType(usageType);
        po.setUsageId(usageId);
        po.setUsageName(usageName);
        po.setCreateTime(now);
        mapper.insert(po);
      }
    } catch (RuntimeException e) {
      log.warn("Metric usage syncBindings failed (fail-open): usageType={}, usageId={}, error={}",
          usageType, usageId, e.getMessage());
    }
  }

  @Override
  public List<Long> boundMetricIds(String usageType, Long usageId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(consumerScope(usageType, usageId)).stream()
        .map(MetricUsagePO::getMetricId)
        .distinct()
        .toList();
  }

  @Override
  public UsageSummary summary(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    long total = mapper.countByMetric(projectId, metricId);
    List<Map<String, Object>> rows = mapper.countGroupByType(projectId, metricId);
    long report = 0, dataset = 0, dashboard = 0, api = 0, screen = 0;
    for (Map<String, Object> row : rows) {
      String type = String.valueOf(row.get("usageType"));
      long cnt = ((Number) row.get("cnt")).longValue();
      switch (type) {
        case "REPORT" -> report = cnt;
        case "DATASET" -> dataset = cnt;
        case "DASHBOARD" -> dashboard = cnt;
        case "API" -> api = cnt;
        case "SCREEN" -> screen = cnt;
        default -> { /* ignore unknown types */ }
      }
    }
    return new UsageSummary(metricId, total, report, dataset, dashboard, api, screen);
  }

  public List<MetricUsagePO> listByMetric(Long metricId) {
    Long projectId = currentProject.requireProjectId();
    return mapper.selectList(
        new LambdaQueryWrapper<MetricUsagePO>()
            .eq(MetricUsagePO::getProjectId, projectId)
            .eq(MetricUsagePO::getMetricId, metricId)
            .orderByDesc(MetricUsagePO::getCreateTime));
  }

  private LambdaQueryWrapper<MetricUsagePO> consumerScope(String usageType, Long usageId) {
    Long projectId = currentProject.requireProjectId();
    return new LambdaQueryWrapper<MetricUsagePO>()
        .eq(MetricUsagePO::getProjectId, projectId)
        .eq(MetricUsagePO::getUsageType, usageType)
        .eq(MetricUsagePO::getUsageId, usageId);
  }
}
