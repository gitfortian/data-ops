package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.dataservice.publication.DataServicePublicationReader;
import io.yak.ops.business.dataservice.publication.PublicationState;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.constant.dataservice.DataServicePermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据服务发布状态")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service/publication")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataServicePermissionCode.PUBLISH)
public class DataServicePublicationStateController {
  private final DataServicePublicationReader reader;

  @Operation(summary = "查询已发布来源与 Runtime 的同步状态")
  @GetMapping("/state")
  public Result<PublicationState> state(
      @RequestParam("sourceType") String sourceType,
      @RequestParam("sourceRef") String sourceRef) {
    if (reader.managesServiceDefinition(sourceType)) {
      throw new IllegalStateException("该发布来源由所属 authoring context 管理，请从来源工作台查询发布状态");
    }
    return Result.success(reader.state(sourceType, sourceRef));
  }
}
