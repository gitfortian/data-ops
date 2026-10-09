package io.yak.ops.business.modeling.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.api.ModelStructureReviewQueryApi;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/structure-review")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingStructureReviewController {
  private final ModelStructureReviewQueryApi reviews;

  @GetMapping
  public Result<ModelStructureReviewQueryApi.Context> read(@PathVariable Long modelId, @RequestParam int baselineVersionNo) {
    return Result.success(reviews.read(modelId, baselineVersionNo, null));
  }
}
