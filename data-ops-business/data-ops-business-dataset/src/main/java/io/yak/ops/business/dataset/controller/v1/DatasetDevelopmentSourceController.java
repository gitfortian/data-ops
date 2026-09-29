package io.yak.ops.business.dataset.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.dataset.development.DatasetDevelopmentSourceService;
import io.yak.ops.business.dataset.development.DatasetDevelopmentSourceService.DevelopmentSource;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dataset-owned provenance lookup used for Governance -> Development navigation. */
@Tag(name = "Dataset 开发来源接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/datasets")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DatasetDevelopmentSourceController {

  private final DatasetDevelopmentSourceService service;

  @Operation(summary = "查询 Dataset 对应的数据开发节点")
  @GetMapping("/{datasetId}/development-source")
  public Result<DevelopmentSource> developmentSource(@PathVariable("datasetId") long datasetId) {
    return Result.success(service.require(datasetId));
  }
}
