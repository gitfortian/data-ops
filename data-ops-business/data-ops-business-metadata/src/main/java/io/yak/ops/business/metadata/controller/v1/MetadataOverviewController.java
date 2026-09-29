package io.yak.ops.business.metadata.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metadata.config.ConditionalOnMetadataPersistence;
import io.yak.ops.business.metadata.stat.MetadataOverviewService;
import io.yak.ops.business.metadata.stat.MetadataOverviewService.Overview;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "元数据技术运维总览")
@RestController
@RequiredArgsConstructor
@ConditionalOnMetadataPersistence
@RequestMapping("/api/v1/metadata/overview")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetadataPermissionCode.READ)
public class MetadataOverviewController {

  private final MetadataOverviewService overviewService;

  @Operation(summary = "查询当前项目的目录实体、采集运行和治理待办概览")
  @GetMapping
  public Result<Overview> overview() {
    return Result.success(overviewService.overview());
  }
}
