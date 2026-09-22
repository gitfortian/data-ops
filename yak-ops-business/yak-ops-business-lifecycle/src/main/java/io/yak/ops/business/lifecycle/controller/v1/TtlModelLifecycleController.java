package io.yak.ops.business.lifecycle.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.lifecycle.binding.ModelTtlBindingService;
import io.yak.ops.business.lifecycle.binding.ModelTtlResolution;
import io.yak.ops.business.lifecycle.controller.v1.dto.LifecycleRequests.BindingDTO;
import io.yak.ops.business.lifecycle.controller.v1.dto.LifecycleRequests.DispatchDTO;
import io.yak.ops.business.lifecycle.controller.v1.dto.LifecycleRequests.ModelIdsDTO;
import io.yak.ops.business.lifecycle.dispatch.TtlDispatchService;
import io.yak.ops.business.lifecycle.dispatch.TtlDispatchService.DispatchOutcome;
import io.yak.ops.business.lifecycle.preview.TtlPreviewService;
import io.yak.ops.business.lifecycle.preview.TtlPreviewService.PreviewBatch;
import io.yak.ops.common.constant.lifecycle.LifecyclePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模型生命周期 Tab + 预览 + 下发 REST API(ticket 83~85)。 */
@Tag(name = "数据生命周期-模型策略")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(LifecyclePermissionCode.READ)
public class TtlModelLifecycleController {

  private final ModelTtlBindingService bindingService;
  private final TtlPreviewService previewService;
  private final TtlDispatchService dispatchService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "模型生命周期详情(Tab 数据:策略来源/语句/状态)")
  @GetMapping("/models/{modelId}/lifecycle")
  public Result<ModelTtlResolution> get(@PathVariable("modelId") Long modelId) {
    return Result.success(bindingService.resolve(modelId));
  }

  @Operation(summary = "绑定/覆盖 TTL 策略")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PutMapping("/models/{modelId}/lifecycle/binding")
  public Result<ModelTtlResolution> bind(
      @PathVariable("modelId") Long modelId,
      @Valid @RequestBody BindingDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(bindingService.bind(modelId, dto.getPolicyId(), operator));
  }

  @Operation(summary = "解绑覆盖策略(回退继承分层默认)")
  @RequiresPermission(LifecyclePermissionCode.DELETE)
  @DeleteMapping("/models/{modelId}/lifecycle/binding")
  public Result<ModelTtlResolution> unbind(
      @PathVariable("modelId") Long modelId, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(bindingService.unbind(modelId, operator));
  }

  @Operation(summary = "TTL 预览(分区分布+将删列表,返回确认令牌)")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PostMapping("/models/lifecycle/preview")
  public Result<PreviewBatch> preview(@Valid @RequestBody ModelIdsDTO dto) {
    return Result.success(previewService.preview(dto.getModelIds()));
  }

  @Operation(summary = "TTL 下发(需预览确认令牌)")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PostMapping("/models/lifecycle/dispatch")
  public Result<List<DispatchOutcome>> dispatch(
      @Valid @RequestBody DispatchDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(
        dispatchService.dispatch(dto.getModelIds(), dto.getConfirmToken(), operator));
  }
}
