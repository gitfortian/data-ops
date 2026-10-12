package io.yak.ops.business.metadata.controller.v1;


import io.yak.framework.common.Result;
import io.yak.ops.business.metadata.autoingest.AutoIngestReceiptService;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import io.yak.ops.core.security.ActionAuthorization;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S2: source-scoped persistent delivery evidence, never a claim of publishing. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/metadata/auto-ingest")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetadataPermissionCode.READ)
public class MetadataAutoIngestController {
  private final AutoIngestReceiptService receipts;
  private final CurrentProject project;
  private final ActionAuthorization authorization;

  /** The most recently COMMITTED source-table ticket in the active project. */
  @GetMapping("/entities/{entityId}/latest")
  public Result<AutoIngestReceiptService.Receipt> latest(@PathVariable Long entityId) {
    return Result.success(receipts.latest(project.requireProjectId(), entityId));
  }

  /** Explicit dead-letter recovery; no implicit extra harvest or full-provider sweep. */
  @RequiresPermission(MetadataPermissionCode.UPDATE)
  @PostMapping("/events/{eventId}/retry")
  public Result<Boolean> retryDead(@PathVariable Long eventId) {
    authorization.requirePermission(AssetPermissionCode.UPDATE);
    return Result.success(receipts.retryDead(project.requireProjectId(), eventId));
  }

}
