package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.application.MdmGovernanceService;
import io.yak.ops.business.mdm.application.MdmGovernanceService.GovernanceSummary;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据治理端点(ticket 61):质量/血缘/权限聚合视图。 */
@Tag(name = "主数据治理")
@RestController
@RequestMapping("/api/v1/mdm/governance")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class MdmGovernanceController {

  private final MdmGovernanceService service;

  public MdmGovernanceController(MdmGovernanceService service) {
    this.service = service;
  }

  @Operation(summary = "实体治理概览(来源/记录/分发/订阅/待审批)")
  @RequiresPermission("mdm:read")
  @GetMapping("/entity/{entityId}")
  public Result<GovernanceSummary> summary(@PathVariable("entityId") Long entityId) {
    return Result.success(service.getSummary(entityId));
  }

  @Operation(summary = "实体 owner 检查(轻量权限控制)")
  @RequiresPermission("mdm:read")
  @GetMapping("/entity/{entityId}/owner-check")
  public Result<Boolean> ownerCheck(
      @PathVariable("entityId") Long entityId,
      @RequestParam("operator") String operator) {
    return Result.success(service.isOwner(entityId, operator));
  }
}
