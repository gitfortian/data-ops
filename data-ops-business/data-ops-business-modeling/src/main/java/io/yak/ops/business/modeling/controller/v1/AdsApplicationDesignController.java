package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.derive.AdsApplicationDesignService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADS application output design; this API never executes SQL or grants consumption access. */
@Tag(name = "ADS 应用输出合同")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/ads-application")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class AdsApplicationDesignController {
  private final AdsApplicationDesignService service;
  private final CurrentUserProvider currentUsers;

  @GetMapping
  public Result<AdsApplicationDesignService.Saved> inspect(@PathVariable long modelId) {
    return Result.success(service.inspect(modelId).orElse(null));
  }

  @GetMapping("/history")
  public Result<java.util.List<AdsApplicationDesignService.HistoryEntry>> history(
      @PathVariable long modelId) {
    return Result.success(service.history(modelId));
  }

  @PostMapping("/validate")
  public Result<AdsApplicationDesignService.ValidationResult> validate(
      @PathVariable long modelId,
      @Valid @RequestBody AdsApplicationDesignService.Request request) {
    return Result.success(service.validate(modelId, request));
  }

  @PutMapping
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<AdsApplicationDesignService.Saved> save(
      @PathVariable long modelId,
      @Valid @RequestBody AdsApplicationDesignService.Request request,
      HttpServletRequest httpRequest) {
    return Result.success(service.save(modelId, request, currentUsers.getCurrentUser(httpRequest)));
  }
}
