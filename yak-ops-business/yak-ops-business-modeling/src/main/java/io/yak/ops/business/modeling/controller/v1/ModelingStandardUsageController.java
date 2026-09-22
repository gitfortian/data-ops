package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.semantic.api.StandardUsageApi;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Usage reporting from modeling action points (ticket 42, push-based). */
@Tag(name = "数据建模标准使用上报接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/standards")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingStandardUsageController {

  private final StandardUsageApi usageApi;
  private final CurrentUserProvider currentUserProvider;

  /** 上报请求。 */
  @Data
  public static class UsageReportRequest {
    @NotNull(message = "标准不能为空")
    Long standardId;

    @NotBlank(message = "事件类型不能为空")
        @Pattern(regexp = "^(APPLY|BYPASS)$", message = "事件类型必须为 APPLY 或 BYPASS")
        String usageType;

    @Size(max = 32, message = "场景不能超过 32 个字符")
    String scene;
  }

  @Operation(summary = "上报标准采纳/绕过事件（fail-open，不阻断编辑）")
  @PostMapping("/usage-report")
  public Result<Boolean> report(
      @PathVariable("modelId") Long modelId,
      @Valid @RequestBody UsageReportRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    usageApi.record(
        new StandardUsageApi.UsageEvent(
            request.getStandardId(),
            request.getUsageType(),
            request.getScene(),
            String.valueOf(modelId),
            operator));
    return Result.success(Boolean.TRUE);
  }
}
