package io.yak.ops.business.lifecycle.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.PageData;
import io.yak.framework.common.PagingData;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.lifecycle.controller.v1.dto.LifecycleRequests.MonitorModelQueryDTO;
import io.yak.ops.business.lifecycle.dispatch.TtlDispatchService.DispatchOutcome;
import io.yak.ops.business.lifecycle.monitor.TtlMonitorService;
import io.yak.ops.business.lifecycle.monitor.TtlMonitorService.MonitorModelView;
import io.yak.ops.business.lifecycle.monitor.TtlMonitorService.MonitorSummary;
import io.yak.ops.common.constant.lifecycle.LifecyclePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TTL 监控 REST API(ticket 87)。 */
@Tag(name = "数据生命周期-TTL监控")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lifecycle")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(LifecyclePermissionCode.READ)
public class TtlMonitorController {

  private final TtlMonitorService monitorService;
  private final CurrentUserProvider currentUserProvider;

  @Operation(summary = "监控概览(状态计数+异常告警)")
  @GetMapping("/monitor/summary")
  public Result<MonitorSummary> summary() {
    return Result.success(monitorService.summary());
  }

  @Operation(summary = "分页查询模型 TTL 状态")
  @PostMapping("/monitor/models/page")
  public Result<PagingData<MonitorModelView>> pageModels(
      @Valid @RequestBody MonitorModelQueryDTO query) {
    PageData<MonitorModelView> page = monitorService.pageModels(query.getPageNo(),
        query.getPageSize(), query.getState(), query.getLayerCode(), query.getKeyword());
    return Result.success(PagingData.from(page));
  }

  @Operation(summary = "单模型立即重发")
  @RequiresPermission(LifecyclePermissionCode.UPDATE)
  @PostMapping("/monitor/models/{modelId}/redispatch")
  public Result<DispatchOutcome> redispatch(
      @PathVariable("modelId") Long modelId, HttpServletRequest httpRequest) {
    String operator = currentUserProvider.getCurrentUser(httpRequest);
    return Result.success(monitorService.redispatch(modelId, operator));
  }
}
