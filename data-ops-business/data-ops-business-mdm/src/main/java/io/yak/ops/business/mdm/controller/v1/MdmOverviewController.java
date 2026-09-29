package io.yak.ops.business.mdm.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.mdm.application.MdmOverviewService;
import io.yak.ops.business.mdm.application.MdmOverviewService.EntityCard;
import io.yak.ops.business.mdm.application.MdmOverviewService.OverviewResult;
import io.yak.ops.common.constant.mdm.MdmPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 主数据总览端点(R7):六卡计数 + 管线节点 + 最近实体卡片,全部服务端有界聚合。 */
@Tag(name = "主数据总览")
@RestController
@RequestMapping("/api/v1/mdm/overview")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
public class MdmOverviewController {

  private final MdmOverviewService service;

  public MdmOverviewController(MdmOverviewService service) {
    this.service = service;
  }

  @Operation(summary = "主数据总览(六卡 + 管线节点 + 最近实体卡片)")
  @RequiresPermission(MdmPermissionCode.READ)
  @GetMapping
  public Result<OverviewResult> overview(
      @RequestParam(value = "limit", required = false) Integer limit) {
    return Result.success(service.getOverview(limit));
  }

  @Operation(summary = "单实体概览卡片")
  @RequiresPermission(MdmPermissionCode.READ)
  @GetMapping("/entity/{entityId}")
  public Result<EntityCard> entityCard(@PathVariable("entityId") Long entityId) {
    return Result.success(service.getEntityCard(entityId));
  }
}
