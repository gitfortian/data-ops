package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.execution.DevelopmentTaskExecutionControlService;
import io.yak.ops.business.development.execution.DevelopmentTaskExecutionService;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionDetail;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionPage;
import io.yak.ops.business.development.execution.model.DevelopmentTaskExecutionSubmission;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据开发运行记录接口")
@RestController
@RequestMapping("/api/v1/data-development/executions")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentTaskExecutionController {

  private final DevelopmentTaskExecutionService service;
  private final DevelopmentTaskExecutionControlService controlService;
  private final CurrentUserProvider currentUserProvider;

  public DevelopmentTaskExecutionController(
      DevelopmentTaskExecutionService service,
      DevelopmentTaskExecutionControlService controlService,
      CurrentUserProvider currentUserProvider) {
    this.service = service;
    this.controlService = controlService;
    this.currentUserProvider = currentUserProvider;
  }

  @Operation(summary = "分页查询数据开发运行记录")
  @GetMapping
  public Result<DevelopmentTaskExecutionPage> page(
      @RequestParam(defaultValue = "1") int pageNo,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String taskType,
      @RequestParam(required = false) String triggerType,
      @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
      @RequestParam(required = false)
          @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
    return Result.success(
        service.page(pageNo, pageSize, keyword, status, taskType, triggerType, startTime, endTime));
  }

  @Operation(summary = "重新绑定节点当前运行实例")
  @GetMapping("/active")
  public Result<DevelopmentTaskExecutionDetail> active(
      @RequestParam("nodeId") Long nodeId) {
    if (nodeId == null || nodeId <= 0L) throw new IllegalArgumentException("节点 ID 非法");
    return Result.success(controlService.reattachActive(nodeId).orElse(null));
  }

  @Operation(summary = "查询并刷新数据开发运行记录详情")
  @GetMapping("/{id}")
  public Result<DevelopmentTaskExecutionDetail> get(@PathVariable("id") Long id) {
    if (id == null || id <= 0L) throw new IllegalArgumentException("运行记录 ID 非法");
    return Result.success(controlService.refresh(id));
  }

  @Operation(summary = "取消数据开发运行实例")
  @RequiresPermission(DataDevelopmentPermissionCode.EXECUTE)
  @PostMapping("/{id}/cancel")
  public Result<DevelopmentTaskExecutionDetail> cancel(@PathVariable("id") Long id) {
    if (id == null || id <= 0L) throw new IllegalArgumentException("运行记录 ID 非法");
    return Result.success(controlService.cancel(id));
  }

  @Operation(summary = "重试数据开发运行实例")
  @RequiresPermission(DataDevelopmentPermissionCode.EXECUTE)
  @PostMapping("/{id}/retry")
  public Result<DevelopmentTaskExecutionSubmission> retry(
      @PathVariable("id") Long id,
      HttpServletRequest servletRequest) {
    if (id == null || id <= 0L) throw new IllegalArgumentException("运行记录 ID 非法");
    return Result.success(controlService.retry(id, operatorName(servletRequest)));
  }

  private String operatorName(HttpServletRequest request) {
    String operatorName = currentUserProvider.getCurrentUser(request);
    return operatorName == null ? "unknown" : operatorName;
  }
}
