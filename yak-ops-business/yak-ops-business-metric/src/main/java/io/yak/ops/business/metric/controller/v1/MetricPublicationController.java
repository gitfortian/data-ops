package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.publication.MetricPublicationReadinessService;
import io.yak.ops.business.metric.publication.MetricPublicationReadinessService.PublicationReadiness;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publication readiness API; actual publish command is intentionally not implemented here. */
@Tag(name = "指标发布准备度接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metrics")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricPublicationController {

  private final MetricPublicationReadinessService readinessService;

  @Operation(summary = "检查指定指标版本是否满足发布前置 Gate")
  @GetMapping("/{id}/versions/{version}/publication-readiness")
  public Result<PublicationReadiness> readiness(
      @PathVariable("id") Long id,
      @PathVariable("version") int version) {
    return Result.success(readinessService.check(id, version));
  }
}
