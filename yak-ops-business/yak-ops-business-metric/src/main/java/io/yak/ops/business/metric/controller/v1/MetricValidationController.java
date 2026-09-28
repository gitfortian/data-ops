package io.yak.ops.business.metric.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metric.validation.MetricDefinitionValidationService;
import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Version-scoped Definition Validation evidence API. */
@Tag(name = "指标定义校验接口")
@RestController
@RequestMapping("/api/v1/metrics")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetricPermissionCode.READ)
public class MetricValidationController {

  private final MetricDefinitionValidationService validationService;
  private final CurrentUserProvider currentUserProvider;

  public MetricValidationController(
      MetricDefinitionValidationService validationService,
      CurrentUserProvider currentUserProvider) {
    this.validationService = validationService;
    this.currentUserProvider = currentUserProvider;
  }

  @Operation(summary = "校验指定指标版本并生成不可变证据")
  @RequiresPermission(MetricPermissionCode.UPDATE)
  @PostMapping("/{id}/versions/{version}/validation")
  public Result<MetricValidationEvidence> validate(
      @PathVariable("id") Long id,
      @PathVariable("version") int version,
      HttpServletRequest request) {
    String operator = currentUserProvider.getCurrentUser(request);
    return Result.success(validationService.validate(id, version, operator));
  }

  @Operation(summary = "查询指定指标版本的校验证据历史")
  @GetMapping("/{id}/versions/{version}/validations")
  public Result<List<MetricValidationEvidence>> history(
      @PathVariable("id") Long id,
      @PathVariable("version") int version) {
    return Result.success(validationService.history(id, version));
  }

  @Operation(summary = "查询指定指标版本最近一次 PASSED 且 provider READY 的校验证据")
  @GetMapping("/{id}/versions/{version}/validation/latest-ready")
  public Result<MetricValidationEvidence> latestReady(
      @PathVariable("id") Long id,
      @PathVariable("version") int version) {
    return Result.success(validationService.latestReady(id, version));
  }
}
