package io.yak.ops.business.dashboard.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dashboard.DashboardService;
import io.yak.ops.business.dashboard.controller.v1.converter.DashboardRequestConverter;
import io.yak.ops.business.dashboard.controller.v1.converter.DashboardViewConverter;
import io.yak.ops.business.dashboard.controller.v1.dto.SaveDashboardRequest;
import io.yak.ops.business.dashboard.controller.v1.vo.DashboardViews;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BI Dashboard HTTP adapter. */
@Tag(name = "BI 仪表盘接口")
@RestController
@Validated
@ProjectScope
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

  private final DashboardService dashboardService;
  private final DashboardRequestConverter requestConverter;
  private final DashboardViewConverter viewConverter;

  @Operation(summary = "查询 Dashboard 列表")
  @GetMapping
  public Result<List<DashboardViews.Dashboard>> list() {
    return Result.success(viewConverter.dashboards(dashboardService.list()));
  }

  @Operation(summary = "查询 Dashboard 当前草稿和历史版本")
  @GetMapping("/{dashboardId}")
  public Result<DashboardViews.Detail> get(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(viewConverter.detail(dashboardService.get(dashboardId)));
  }

  @Operation(summary = "查询 Dashboard 版本历史")
  @GetMapping("/{dashboardId}/versions")
  public Result<List<DashboardViews.Version>> versions(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(viewConverter.versions(dashboardService.versions(dashboardId)));
  }

  @Operation(summary = "查看指定 DashboardVersion 快照")
  @GetMapping("/{dashboardId}/versions/{versionNo}")
  public Result<DashboardViews.VersionDetail> version(
      @PathVariable("dashboardId") long dashboardId, @PathVariable("versionNo") int versionNo) {
    return Result.success(viewConverter.versionDetail(dashboardService.version(dashboardId, versionNo)));
  }

  @Operation(summary = "查询 Dashboard 当前已发布快照")
  @GetMapping("/{dashboardId}/published")
  public Result<DashboardViews.VersionDetail> published(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(viewConverter.versionDetail(dashboardService.published(dashboardId)));
  }

  @Operation(summary = "创建 Dashboard，并保存草稿 V1")
  @PostMapping
  public Result<DashboardViews.Detail> create(@Valid @RequestBody SaveDashboardRequest request) {
    return Result.success(viewConverter.detail(dashboardService.create(requestConverter.toDraft(request))));
  }

  @Operation(summary = "保存 Dashboard 新草稿版本")
  @PostMapping("/{dashboardId}/versions")
  public Result<DashboardViews.Detail> saveVersion(
      @PathVariable("dashboardId") long dashboardId, @Valid @RequestBody SaveDashboardRequest request) {
    return Result.success(viewConverter.detail(
        dashboardService.saveVersion(dashboardId, requestConverter.toDraft(request))));
  }

  @Operation(summary = "发布当前 Dashboard 草稿")
  @PostMapping("/{dashboardId}/publish")
  public Result<DashboardViews.Detail> publish(@PathVariable("dashboardId") long dashboardId) {
    return Result.success(viewConverter.detail(dashboardService.publish(dashboardId)));
  }

  @Operation(summary = "将历史 DashboardVersion 恢复为新的草稿版本")
  @PostMapping("/{dashboardId}/restore/{versionNo}")
  public Result<DashboardViews.Detail> restoreVersion(
      @PathVariable("dashboardId") long dashboardId, @PathVariable("versionNo") int versionNo) {
    return Result.success(viewConverter.detail(dashboardService.restoreVersion(dashboardId, versionNo)));
  }

  @Deprecated
  @Operation(summary = "兼容旧版激活接口：恢复历史版本为新草稿")
  @PostMapping("/{dashboardId}/activate/{versionNo}")
  public Result<DashboardViews.Detail> activateVersion(
      @PathVariable("dashboardId") long dashboardId, @PathVariable("versionNo") int versionNo) {
    return Result.success(viewConverter.detail(dashboardService.activateVersion(dashboardId, versionNo)));
  }

  @Operation(summary = "删除 Dashboard 及其历史版本")
  @DeleteMapping("/{dashboardId}")
  public Result<Boolean> delete(@PathVariable("dashboardId") long dashboardId) {
    dashboardService.delete(dashboardId);
    return Result.success(Boolean.TRUE);
  }
}
