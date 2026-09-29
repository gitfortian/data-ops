package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.impact.ImpactAnalysisService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Impact analysis preview (ticket 46; read-only, no auto modification). */
@Tag(name = "数据建模变更影响分析接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/impact")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class ModelingImpactController {

  private final ImpactAnalysisService impactService;

  @Operation(summary = "按标准字段分析影响范围（各模型各层落地）")
  @GetMapping("/by-standard-field/{processFieldId}")
  public Result<List<ImpactAnalysisService.ImpactItem>> byStandardField(
      @PathVariable("processFieldId") Long processFieldId) {
    return Result.success(impactService.byStandardField(processFieldId));
  }

  @Operation(summary = "按来源列/表分析影响范围（来源映射反查）")
  @GetMapping("/by-source")
  public Result<List<ImpactAnalysisService.ImpactItem>> bySource(
      @RequestParam("datasourceId") @NotNull(message = "数据源不能为空") Long datasourceId,
      @RequestParam(value = "database", required = false) String database,
      @RequestParam(value = "table", required = false) String table,
      @RequestParam(value = "column", required = false) String column) {
    return Result.success(
        impactService.bySourceColumn(datasourceId, database, table, column));
  }
}
