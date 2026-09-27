package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.publication.MetricPublicationReadinessService;
import io.yak.ops.business.metric.publication.MetricPublicationReadinessService.PublicationReadiness;
import io.yak.ops.business.metric.publication.MetricPublicationService;
import io.yak.ops.business.metric.publication.MetricPublicationService.PublicationEventView;
import io.yak.ops.business.metric.publication.MetricPublicationService.PublishedMetricContract;
import io.yak.ops.business.metric.publication.MetricPublicationService.WithdrawalResult;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Metric publication readiness and explicit publication lifecycle API. */
@Tag(name = "指标发布接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricPublicationController {

  private final MetricPublicationReadinessService readinessService;
  private final MetricPublicationService publicationService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "检查指定指标版本是否满足发布前置 Gate")
  @GetMapping("/{id}/versions/{version}/publication-readiness")
  public Result<PublicationReadiness> readiness(
      @PathVariable("id") Long id,
      @PathVariable("version") int version) {
    return Result.success(readinessService.check(id, version));
  }

  @Operation(summary = "显式发布一个满足 Gate 的 immutable MetricVersion")
  @RequiresPermission(MetricPermissionCode.UPDATE)
  @PostMapping("/{id}/versions/{version}/publish")
  public Result<PublishedMetricContract> publish(
      @PathVariable("id") Long id,
      @PathVariable("version") int version,
      HttpServletRequest request) {
    return Result.success(publicationService.publish(
        id, version, currentUserProvider.getCurrentUser(request)));
  }

  @Operation(summary = "读取当前生效的 Published Metric Contract")
  @GetMapping("/{id}/publication")
  public Result<PublishedMetricContract> active(@PathVariable("id") Long id) {
    return Result.success(publicationService.active(id));
  }

  @Operation(summary = "读取指标发布生命周期账本")
  @GetMapping("/{id}/publication-history")
  public Result<List<PublicationEventView>> history(@PathVariable("id") Long id) {
    return Result.success(publicationService.history(id));
  }

  @Operation(summary = "显式撤回当前 Published Metric Contract")
  @RequiresPermission(MetricPermissionCode.UPDATE)
  @PostMapping("/{id}/publication/withdraw")
  public Result<WithdrawalResult> withdraw(
      @PathVariable("id") Long id,
      HttpServletRequest request) {
    return Result.success(publicationService.withdraw(
        id, currentUserProvider.getCurrentUser(request)));
  }
}
