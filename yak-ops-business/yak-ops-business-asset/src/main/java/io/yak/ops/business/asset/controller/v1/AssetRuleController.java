package io.yak.ops.business.asset.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.asset.catalog.AssignRuleService;
import io.yak.ops.business.asset.catalog.AssignRuleService.DryRunResult;
import io.yak.ops.business.asset.catalog.AssignRuleService.RuleView;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.RuleUpsertDTO;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
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

/** 编目规则 REST API(ticket 95):CRUD + 试跑 + 启停 + 重应用。 */
@Tag(name = "数据资产-编目规则")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assets/rules")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(AssetPermissionCode.READ)
public class AssetRuleController {

  private final AssignRuleService ruleService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "规则列表(按优先级)")
  @GetMapping
  public Result<List<RuleView>> list() {
    return Result.success(ruleService.list());
  }

  @Operation(summary = "新建规则(默认停用,需先试跑)")
  @RequiresPermission(AssetPermissionCode.CREATE)
  @PostMapping
  public Result<RuleView> create(@Valid @RequestBody RuleUpsertDTO dto,
      HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(ruleService.create(dto, operator));
  }

  @Operation(summary = "编辑规则(编辑后回到未试跑+停用)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PutMapping("/{id}")
  public Result<RuleView> update(@PathVariable("id") Long id,
      @Valid @RequestBody RuleUpsertDTO dto, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(ruleService.update(id, dto, operator));
  }

  @Operation(summary = "删除规则")
  @RequiresPermission(AssetPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    ruleService.delete(id, operator);
    return Result.success(Boolean.TRUE);
  }

  @Operation(summary = "试跑:命中数+前50样例(启用前置条件,D10)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/{id}/dry-run")
  public Result<DryRunResult> dryRun(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(ruleService.dryRun(id, operator));
  }

  @Operation(summary = "启用(未试跑 48010)")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/{id}/enable")
  public Result<RuleView> enable(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(ruleService.setEnabled(id, true, operator));
  }

  @Operation(summary = "停用")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/{id}/disable")
  public Result<RuleView> disable(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(ruleService.setEnabled(id, false, operator));
  }

  @Operation(summary = "重应用:对全部存量命中资产执行规则目标")
  @RequiresPermission(AssetPermissionCode.UPDATE)
  @PostMapping("/{id}/apply-again")
  public Result<Integer> applyAgain(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(ruleService.applyAgain(id, operator));
  }
}
