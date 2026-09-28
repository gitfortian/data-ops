package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.impact.MetricImpactContextService;
import io.yak.ops.business.metric.impact.MetricImpactService;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phase 5 Metric Impact REST API. */
@Tag(name = "指标影响分析接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics/impact")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricImpactController {

  private final MetricImpactService impactService;
  private final MetricImpactContextService impactContextService;

  @Operation(summary = "检查指标上游变更(影响分析)")
  @GetMapping("/{metricId}/upstream-changes")
  public Result<MetricImpactService.ImpactReport> checkUpstreamChanges(
      @PathVariable("metricId") Long metricId) {
    return Result.success(impactService.checkUpstreamChanges(metricId));
  }

  @Operation(summary = "查询指标影响上下文(依赖/引用使用/运行使用覆盖)")
  @GetMapping("/{metricId}/context")
  public Result<MetricImpactContextService.ImpactContext> impactContext(
      @PathVariable("metricId") Long metricId) {
    return Result.success(impactContextService.get(metricId));
  }

  @Operation(summary = "反向影响分析:上游对象变更波及的指标清单")
  @GetMapping("/affected")
  public Result<List<MetricImpactService.AffectedMetric>> findAffectedMetrics(
      @RequestParam("dependencyType") String dependencyType,
      @RequestParam("dependencyId") Long dependencyId) {
    return Result.success(impactService.findAffectedMetrics(dependencyType, dependencyId));
  }
}
