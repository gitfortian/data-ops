package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.api.DevelopmentNodeApi.CreateRequest;
import io.yak.ops.business.development.api.DevelopmentNodeApi.MoveRequest;
import io.yak.ops.business.development.api.DevelopmentNodeApi.RenameRequest;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.node.DevelopmentNodeService;
import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lightweight resource-node management for the data-development tree. */
@Tag(name = "数据开发节点接口")
@RestController
@RequestMapping("/api/v1/data-development/nodes")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentNodeController {

  private final DevelopmentNodeService service;
  private final CurrentUserProvider currentUserProvider;
  private final MetricUsageApi metricUsageApi;

  public DevelopmentNodeController(
      DevelopmentNodeService service,
      CurrentUserProvider currentUserProvider,
      MetricUsageApi metricUsageApi) {
    this.service = service;
    this.currentUserProvider = currentUserProvider;
    this.metricUsageApi = metricUsageApi;
  }

  @Operation(summary = "查询数据开发节点")
  @GetMapping
  public Result<List<DevelopmentNode>> list() {
    return Result.success(service.list());
  }

  @Operation(summary = "新建数据开发节点")
  @RequiresPermission(DataDevelopmentPermissionCode.EDIT)
  @PostMapping
  public Result<DevelopmentNode> create(
      @Valid @RequestBody CreateRequest request,
      HttpServletRequest servletRequest) {
    DevelopmentNode created = service.create(
        request.name(),
        request.type(),
        request.directoryId());
    return Result.success(service.recordUpdater(created.id(), operatorName(servletRequest)));
  }

  @Operation(summary = "重命名数据开发节点")
  @RequiresPermission(DataDevelopmentPermissionCode.EDIT)
  @PutMapping("/{id}/name")
  public Result<DevelopmentNode> rename(
      @PathVariable("id") Long id,
      @Valid @RequestBody RenameRequest request,
      HttpServletRequest servletRequest) {
    DevelopmentNode renamed = service.rename(id, request.name());
    return Result.success(service.recordUpdater(renamed.id(), operatorName(servletRequest)));
  }

  @Operation(summary = "移动数据开发节点")
  @RequiresPermission(DataDevelopmentPermissionCode.EDIT)
  @PutMapping("/{id}/directory")
  public Result<DevelopmentNode> move(
      @PathVariable("id") Long id,
      @Valid @RequestBody MoveRequest request,
      HttpServletRequest servletRequest) {
    DevelopmentNode moved = service.move(id, request.directoryId());
    return Result.success(service.recordUpdater(moved.id(), operatorName(servletRequest)));
  }

  @Operation(summary = "删除数据开发节点")
  @RequiresPermission(DataDevelopmentPermissionCode.DELETE)
  @DeleteMapping("/{id}")
  public Result<Boolean> delete(@PathVariable("id") Long id) {
    service.delete(id);
    // 01 消费接线:节点(含 Dataset)删除后撤销其指标引用,避免孤儿 usage 永久阻断指标删除
    metricUsageApi.revoke(MetricUsageApi.USAGE_TYPE_DATASET, id);
    return Result.success(Boolean.TRUE);
  }

  private String operatorName(HttpServletRequest request) {
    String operatorName = currentUserProvider.getCurrentUser(request);
    return operatorName == null ? "unknown" : operatorName;
  }
}
