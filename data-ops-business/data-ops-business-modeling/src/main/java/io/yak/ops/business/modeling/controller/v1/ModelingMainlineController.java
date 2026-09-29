package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.view.MainlineViewService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Business-process mainline view (ticket 47). */
@Tag(name = "数据建模业务过程主线视图接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/mainline")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingMainlineController {

  private final MainlineViewService viewService;

  @Operation(summary = "全量业务过程主线覆盖")
  @GetMapping
  public Result<List<MainlineViewService.ProcessCoverage>> mainline() {
    return Result.success(viewService.mainline());
  }

  @Operation(summary = "单业务过程覆盖详情")
  @GetMapping("/{processId}")
  public Result<MainlineViewService.ProcessCoverage> coverage(
      @PathVariable("processId") Long processId) {
    return Result.success(viewService.coverageOf(processId));
  }
}
