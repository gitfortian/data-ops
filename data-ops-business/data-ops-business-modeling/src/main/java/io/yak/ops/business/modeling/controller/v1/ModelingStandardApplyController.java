package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.controller.v1.dto.ModelingStandardRecommendDTO;
import io.yak.ops.business.modeling.recommend.ModelingStandardApplyService;
import io.yak.ops.business.semantic.api.StandardRecommendApi;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Standard recommendation for the structure editor (ticket 39). */
@Tag(name = "数据建模标准推荐接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{modelId}/standards")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingStandardApplyController {

  private final ModelingStandardApplyService service;

  @Operation(summary = "字段标准推荐（命名校验 + 类型/码值/单位/口径/安全候选；仅提示不阻断）")
  @PostMapping("/recommend")
  public Result<StandardRecommendApi.RecommendationReport> recommend(
      @PathVariable("modelId") Long modelId,
      @Valid @RequestBody ModelingStandardRecommendDTO request) {
    return Result.success(
        service.recommend(modelId, request.getColumnName(), request.getDataType(), request.getRole()));
  }
}
