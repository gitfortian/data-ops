package io.yak.ops.business.development.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.handoff.DevelopmentModelTaskBinding;
import io.yak.ops.business.development.handoff.DevelopmentModelTaskBindingCoordinator;
import io.yak.ops.business.development.handoff.DevelopmentModelTaskBindingCoordinator.BindCommand;
import io.yak.ops.business.development.handoff.FrozenDwdExecutionCandidateService;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REVIEW BRANCH ONLY. Disabled by default. DR-07 Owner approval must be
 * documented before any production rollout or enabling this controller.
 * Binding does not execute an SQL statement or authorize future execution.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-development/model-handoffs")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
@ConditionalOnProperty(prefix = "data-ops.dwd", name = "binding-write-authorized", havingValue = "true",
    matchIfMissing = false)
public class DevelopmentModelTaskBindingController {
  private final DevelopmentModelTaskBindingCoordinator coordinator;
  private final FrozenDwdExecutionCandidateService frozenExecution;

  @PostMapping("/bind")
  @RequiresPermission(DataDevelopmentPermissionCode.PUBLISH)
  public Result<DevelopmentModelTaskBinding> bind(@NotNull @RequestBody BindCommand request) {
    return Result.success(coordinator.bind(request));
  }

  /** Read-only inspection; NOT a run endpoint or Runtime authorization. */
  @GetMapping("/{bindingId}/frozen-execution-preview")
  public Result<FrozenDwdExecutionCandidateService.Preview> inspectFrozenExecution(
      @PathVariable long bindingId) {
    return Result.success(frozenExecution.inspect(bindingId));
  }

  @GetMapping("/{bindingId}")
  public Result<DevelopmentModelTaskBinding> get(@PathVariable long bindingId) {
    return Result.success(coordinator.get(bindingId));
  }
}
