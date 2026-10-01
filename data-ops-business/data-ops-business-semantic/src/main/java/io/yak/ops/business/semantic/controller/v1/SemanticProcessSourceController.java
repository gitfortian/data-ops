package io.yak.ops.business.semantic.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.semantic.binding.ProcessSourceBinding;
import io.yak.ops.business.semantic.binding.SemanticProcessBindingService;
import io.yak.ops.common.constant.semantic.SemanticPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Process-source bindings (ticket 36): connectivity-checked at bind time. */
@Tag(name = "业务语义过程源表关联接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/semantic/processes/{processId}/sources")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SemanticPermissionCode.READ)
public class SemanticProcessSourceController {

  private final SemanticProcessBindingService bindingService;
  private final CurrentUserProvider currentUserProvider;

  public record BindRequest(
      @NotNull(message = "数据源不能为空") Long datasourceId,
      @NotBlank(message = "源表名不能为空") @Size(max = 128, message = "源表名不能超过 128 个字符")
          String sourceTable,
      @Size(max = 16, message = "表角色不能超过 16 个字符") String tableRole,
      @Size(max = 512, message = "关联条件不能超过 512 个字符") String joinCondition) {}

  public record BindingView(
      Long id,
      Long processId,
      Long datasourceId,
      String sourceTable,
      String tableRole,
      String joinCondition) {}

  @Operation(summary = "列出业务过程的源表关联")
  @GetMapping
  public Result<List<BindingView>> list(@PathVariable("processId") Long processId) {
    return Result.success(
        bindingService.listByProcess(processId).stream()
            .map(SemanticProcessSourceController::toView)
            .toList());
  }

  @Operation(summary = "绑定源表（连通性校验失败拒绝）")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @PostMapping
  public Result<BindingView> bind(
      @PathVariable("processId") Long processId,
      @Valid @RequestBody BindRequest request,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    ProcessSourceBinding binding =
        bindingService.bind(
            processId,
            request.datasourceId(),
            request.sourceTable(),
            request.tableRole(),
            request.joinCondition(),
            operator);
    return Result.success(toView(binding));
  }

  @Operation(summary = "解绑源表")
  @RequiresPermission(SemanticPermissionCode.UPDATE)
  @DeleteMapping("/{bindingId}")
  public Result<Boolean> unbind(
      @PathVariable("processId") Long processId, @PathVariable("bindingId") Long bindingId) {
    bindingService.unbind(processId, bindingId);
    return Result.success(Boolean.TRUE);
  }

  private static BindingView toView(ProcessSourceBinding binding) {
    return new BindingView(
        binding.id(),
        binding.processId(),
        binding.datasourceId(),
        binding.sourceTable(),
        binding.tableRole(),
        binding.joinCondition());
  }
}
