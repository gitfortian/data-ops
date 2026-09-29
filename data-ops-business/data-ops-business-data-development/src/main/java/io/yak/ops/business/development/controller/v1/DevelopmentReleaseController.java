package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.release.DevelopmentReleaseService;
import io.yak.ops.business.development.release.model.DevelopmentReleaseDetail;
import io.yak.ops.business.development.release.model.DevelopmentReleasePage;
import io.yak.ops.business.development.release.model.DevelopmentReleaseSummary;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据开发发布中心接口")
@RestController
@RequestMapping("/api/v1/data-development/releases")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentReleaseController {

  private final DevelopmentReleaseService service;

  public DevelopmentReleaseController(DevelopmentReleaseService service) {
    this.service = service;
  }

  @Operation(summary = "分页查询发布中心任务")
  @GetMapping
  public Result<DevelopmentReleasePage> page(
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String taskType,
      @RequestParam(required = false) String keyword) {
    return Result.success(service.page(pageNo, pageSize, status, taskType, keyword));
  }

  @Operation(summary = "查询发布任务详情和版本历史")
  @GetMapping("/{assetId}")
  public Result<DevelopmentReleaseDetail> get(@PathVariable("assetId") long assetId) {
    return Result.success(service.get(assetId));
  }

  @Operation(summary = "下线已发布任务")
  @RequiresPermission(DataDevelopmentPermissionCode.RELEASE)
  @PostMapping("/{assetId}/offline")
  public Result<DevelopmentReleaseSummary> offline(@PathVariable("assetId") long assetId) {
    return Result.success(service.offline(assetId));
  }

  @Operation(summary = "重新上线已下线任务")
  @RequiresPermission(DataDevelopmentPermissionCode.RELEASE)
  @PostMapping("/{assetId}/online")
  public Result<DevelopmentReleaseSummary> online(@PathVariable("assetId") long assetId) {
    return Result.success(service.online(assetId));
  }

  @Operation(summary = "切换当前线上版本")
  @RequiresPermission(DataDevelopmentPermissionCode.RELEASE)
  @PostMapping("/{assetId}/activate/{revisionNo}")
  public Result<DevelopmentReleaseSummary> activate(
      @PathVariable("assetId") long assetId,
      @PathVariable("revisionNo") int revisionNo) {
    return Result.success(service.activate(assetId, revisionNo));
  }
}
