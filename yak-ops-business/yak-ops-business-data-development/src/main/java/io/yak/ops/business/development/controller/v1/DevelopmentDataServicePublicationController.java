package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.dataservice.publication.PublicationState;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.development.dataservice.DevelopmentDataServicePublicationService;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Project-governed Runtime publication APIs owned by Data Service Nodes. */
@Tag(name = "数据开发 Data Service Runtime 发布接口")
@RestController
@RequestMapping("/api/v1/data-development/nodes")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentDataServicePublicationController {

  private final DevelopmentDataServicePublicationService service;

  public DevelopmentDataServicePublicationController(
      DevelopmentDataServicePublicationService service) {
    this.service = service;
  }

  @Operation(summary = "查询 Data Service Node Runtime 发布状态")
  @GetMapping("/{nodeId}/data-service/publication")
  public Result<PublicationState> state(@PathVariable("nodeId") long nodeId) {
    return Result.success(service.state(nodeId));
  }

  @Operation(summary = "上线或更新 Data Service Node Runtime")
  @RequiresPermission(DataDevelopmentPermissionCode.RELEASE)
  @PostMapping("/{nodeId}/data-service/publication/online")
  public Result<DataServiceView> online(@PathVariable("nodeId") long nodeId) {
    return Result.success(service.online(nodeId));
  }

  @Operation(summary = "下线 Data Service Node Runtime")
  @RequiresPermission(DataDevelopmentPermissionCode.RELEASE)
  @PostMapping("/{nodeId}/data-service/publication/offline")
  public Result<DataServiceView> offline(@PathVariable("nodeId") long nodeId) {
    return Result.success(service.offline(nodeId));
  }
}
