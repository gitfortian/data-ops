package io.yak.ops.business.sync.realtime.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.sync.realtime.controller.RealtimePermissionCode;
import io.yak.ops.business.sync.realtime.controller.v1.dto.ComputeEnvironmentRequests;
import io.yak.ops.business.sync.realtime.controller.v1.mapper.RealtimeRequestMapper;
import io.yak.ops.business.sync.realtime.controller.v1.mapper.RealtimeViewMapper;
import io.yak.ops.business.sync.realtime.controller.v1.vo.ComputeEnvironmentViews;
import io.yak.ops.business.sync.realtime.environment.ComputeEnvironmentService;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "计算引擎运行环境")
@RestController
@RequestMapping("/api/v1/compute-environments")
@RequiresPermission(RealtimePermissionCode.READ)
@ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)
public class ComputeEnvironmentController {

  private final ComputeEnvironmentService service;
  private final RealtimeRequestMapper requestMapper;
  private final RealtimeViewMapper viewMapper;

  public ComputeEnvironmentController(
      ComputeEnvironmentService service,
      RealtimeRequestMapper requestMapper,
      RealtimeViewMapper viewMapper) {
    this.service = service;
    this.requestMapper = requestMapper;
    this.viewMapper = viewMapper;
  }

  @Operation(summary = "查询运行环境列表")
  @GetMapping
  public Result<List<ComputeEnvironmentViews.Environment>> list() {
    return Result.success(service.list().stream().map(viewMapper::toView).toList());
  }

  @Operation(summary = "查询运行环境详情")
  @GetMapping("/{id}")
  public Result<ComputeEnvironmentViews.Environment> detail(@PathVariable long id) {
    return Result.success(viewMapper.toView(service.get(id)));
  }

  @Operation(summary = "新建运行环境")
  @PostMapping
  @RequiresPermission(RealtimePermissionCode.CREATE)
  public Result<Long> create(@RequestBody ComputeEnvironmentRequests.SaveRequest request) {
    return Result.success(
        service.create(
            request.name(),
            request.submitterType(),
            requestMapper.toRuntimeConfig(request.config()),
            request.enabled(),
            request.makeDefault()));
  }

  @Operation(summary = "预检测未保存的运行环境")
  @PostMapping("/diagnose")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<ComputeEnvironmentViews.Diagnosis> diagnosePreview(
      @RequestBody ComputeEnvironmentRequests.SaveRequest request) {
    return Result.success(
        viewMapper.toView(
            service.diagnosePreview(
                request.name(),
                request.submitterType(),
                requestMapper.toRuntimeConfig(request.config()))));
  }

  @Operation(summary = "检测已保存的运行环境")
  @PostMapping("/{id}/diagnose")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<ComputeEnvironmentViews.Diagnosis> diagnose(@PathVariable long id) {
    return Result.success(viewMapper.toView(service.diagnose(id)));
  }

  @Operation(summary = "更新运行环境")
  @PutMapping("/{id}")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Boolean> update(
      @PathVariable long id, @RequestBody ComputeEnvironmentRequests.SaveRequest request) {
    service.update(
        id,
        request.name(),
        request.submitterType(),
        requestMapper.toRuntimeConfig(request.config()),
        request.enabled(),
        request.makeDefault());
    return Result.success(true);
  }

  @Operation(summary = "启用或停用运行环境")
  @PutMapping("/{id}/enabled")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Boolean> setEnabled(
      @PathVariable long id, @RequestBody ComputeEnvironmentRequests.EnabledRequest request) {
    service.setEnabled(id, request.enabled());
    return Result.success(true);
  }

  @Operation(summary = "设置默认运行环境")
  @PostMapping("/{id}/default")
  @RequiresPermission(RealtimePermissionCode.UPDATE)
  public Result<Boolean> setDefault(@PathVariable long id) {
    service.setDefault(id);
    return Result.success(true);
  }

  @Operation(summary = "删除运行环境")
  @DeleteMapping("/{id}")
  @RequiresPermission(RealtimePermissionCode.DELETE)
  public Result<Boolean> delete(@PathVariable long id) {
    service.delete(id);
    return Result.success(true);
  }
}
