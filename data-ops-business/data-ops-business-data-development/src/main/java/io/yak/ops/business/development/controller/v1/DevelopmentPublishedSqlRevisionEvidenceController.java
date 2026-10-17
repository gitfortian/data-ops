package io.yak.ops.business.development.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.development.task.DevelopmentPublishedSqlRevisionEvidenceService;
import io.yak.ops.common.constant.development.DataDevelopmentPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only SQL revision integrity, not a new TaskRevision run endpoint. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-development/nodes")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(DataDevelopmentPermissionCode.READ)
public class DevelopmentPublishedSqlRevisionEvidenceController {
  private final DevelopmentPublishedSqlRevisionEvidenceService evidence;

  @GetMapping("/{nodeId}/revisions/{revisionNo}/immutable-evidence")
  public Result<DevelopmentPublishedSqlRevisionEvidenceService.Report> inspect(
      @PathVariable Long nodeId, @PathVariable int revisionNo) {
    return Result.success(evidence.inspect(nodeId, revisionNo));
  }
}
