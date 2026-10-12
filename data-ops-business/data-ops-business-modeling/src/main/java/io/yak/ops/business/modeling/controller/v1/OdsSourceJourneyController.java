package io.yak.ops.business.modeling.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.importer.OdsSourceCandidateService;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S1: strict source-key → existing ODS design candidates (0/1/N). */
@Tag(name = "ODS 来源恢复")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/source-journey")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class OdsSourceJourneyController {
  private final OdsSourceCandidateService candidates;

  @Operation(summary = "按当前项目+数据源+库+Schema+表查询精确 ODS 候选")
  @GetMapping("/ods-candidates")
  public Result<OdsSourceCandidateService.Lookup> odsCandidates(
      @RequestParam("datasourceId") Long datasourceId,
      @RequestParam("database") String database,
      @RequestParam(value = "schema", required = false) String schema,
      @RequestParam("table") String table) {
    return Result.success(candidates.find(datasourceId, database, schema, table));
  }
}
