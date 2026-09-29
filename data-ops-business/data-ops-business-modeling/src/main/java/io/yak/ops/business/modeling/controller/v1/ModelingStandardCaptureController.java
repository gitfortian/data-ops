package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.controller.v1.dto.ModelingStandardCaptureDTO;
import io.yak.ops.business.semantic.api.StandardCaptureApi;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Capture-to-standard from the modeling editor / import review (ticket 40). */
@Tag(name = "数据建模沉淀为标准接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/standards")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingStandardCaptureController {

  private final StandardCaptureApi captureApi;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "沉淀为标准（重名幂等返回既有标准；来源记入审计）")
  @RequiresPermission(ModelingPermissionCode.CREATE)
  @PostMapping("/capture")
  public Result<StandardCaptureApi.CaptureResult> capture(
      @PathVariable("modelId") Long modelId,
      @Valid @RequestBody ModelingStandardCaptureDTO request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        captureApi.capture(
            new StandardCaptureApi.CaptureRequest(
                request.getKind(),
                request.getCode(),
                request.getName(),
                request.getRuleExpr(),
                request.getTypeCode(),
                request.getStdType(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request.getBusinessDesc(),
                null,
                null,
                String.valueOf(modelId),
                request.getColumnName())));
  }
}
