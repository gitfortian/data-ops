package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.view.SourceChangeDetectionService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Source structure change detection (ticket 27; detection only, A5). */
@Tag(name = "数据建模源端变更检测接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/source-diff")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingSourceDiffController {

  private final SourceChangeDetectionService detectionService;

  @Operation(summary = "检测模型引用源表的结构变更（新增/移除/类型漂移；仅检测不自动同步）")
  @GetMapping
  public Result<SourceChangeDetectionService.ModelDiffReport> detect(
      @PathVariable("modelId") Long modelId) {
    return Result.success(detectionService.detect(modelId));
  }
}
