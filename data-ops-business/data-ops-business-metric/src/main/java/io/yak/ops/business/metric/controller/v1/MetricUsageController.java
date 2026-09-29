package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.domain.MetricUsage;
import io.yak.ops.business.metric.usage.MetricUsageService;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Metric Reference Usage REST API. */
@Tag(name = "指标使用统计接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricUsageController {

  private final MetricUsageApi usageApi;
  private final MetricUsageService usageService;

  public record UsageItemView(
      Long id, String usageType, Long usageId, Integer metricVersion,
      String usageName, LocalDateTime createTime) {
    public static UsageItemView from(MetricUsage usage) {
      return new UsageItemView(usage.id(), usage.usageType(), usage.usageId(),
          usage.metricVersion(), usage.usageName(), usage.createdAt());
    }
  }

  @Operation(summary = "查询指标使用统计")
  @GetMapping("/{id}/usage/summary")
  public Result<MetricUsageApi.UsageSummary> usageSummary(@PathVariable("id") Long id) {
    return Result.success(usageApi.summary(id));
  }

  @Operation(summary = "查询指标使用记录列表")
  @GetMapping("/{id}/usage")
  public Result<List<UsageItemView>> usageList(@PathVariable("id") Long id) {
    return Result.success(usageService.listByMetric(id).stream()
        .map(UsageItemView::from).toList());
  }
}
