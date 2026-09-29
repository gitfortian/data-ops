package io.yak.ops.business.development.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.lineage.DevelopmentLineageEvidenceService;
import io.yak.ops.business.development.lineage.DevelopmentLineageEvidenceService.Evidence;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only governance evidence for immutable Data Development revisions. */
@Tag(name = "数据开发治理证据接口")
@RestController
@RequestMapping("/api/v1/data-development/nodes")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentLineageEvidenceController {

  private final DevelopmentLineageEvidenceService service;

  public DevelopmentLineageEvidenceController(DevelopmentLineageEvidenceService service) {
    this.service = service;
  }

  @Operation(summary = "查询已发布版本的 durable Lineage evidence 状态")
  @GetMapping("/{nodeId}/revisions/{revisionNo}/lineage-evidence")
  public Result<Evidence> get(
      @PathVariable("nodeId") long nodeId,
      @PathVariable("revisionNo") int revisionNo) {
    return Result.success(service.get(nodeId, revisionNo));
  }
}
