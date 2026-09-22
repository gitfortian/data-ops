package io.yak.ops.business.security.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.security.application.SecurityOverviewService;
import io.yak.ops.business.security.domain.SecurityOverview;
import io.yak.ops.common.constant.security.SecurityPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据安全总览(票据 79),一次拉取五能力关键指标。 */
@Tag(name = "数据安全-总览接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-security/overview")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(SecurityPermissionCode.READ)
public class OverviewController {

  private final SecurityOverviewService service;

  @Operation(summary = "总览快照")
  @GetMapping
  public Result<SecurityOverview> overview() {
    return Result.success(service.overview());
  }
}
