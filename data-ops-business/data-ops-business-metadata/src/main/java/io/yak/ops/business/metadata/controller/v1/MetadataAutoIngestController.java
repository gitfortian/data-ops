package io.yak.ops.business.metadata.controller.v1;

import io.yak.ops.business.metadata.dao.mapper.AutoIngestEventMapper;

import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metadata.autoingest.AutoIngestEvent;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.LocalDateTime;
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
  private final AutoIngestEventMapper events;
  private final CurrentProject project;
  private final ActionAuthorization authorization;

  /** The most recently COMMITTED source-table ticket in the active project. */
  @GetMapping("/entities/{entityId}/latest")
  public Result<Receipt> latest(@PathVariable Long entityId) {
    AutoIngestEvent e = entityId == null || entityId <= 0
        ? null : events.latest(project.requireProjectId(), entityId);
    return Result.success(e == null
        ? new Receipt(null, null, entityId, "NEVER", 0, null, null)
        : view(e));
  }

  /** Explicit dead-letter recovery; no implicit extra harvest or full-provider sweep. */
  @RequiresPermission(MetadataPermissionCode.UPDATE)
  @PostMapping("/events/{eventId}/retry")
  public Result<Boolean> retryDead(@PathVariable Long eventId) {
    authorization.requirePermission(AssetPermissionCode.UPDATE);
    if (eventId == null || eventId <= 0) return Result.success(false);
    return Result.success(events.retryDead(project.requireProjectId(),
        eventId, LocalDateTime.now()) == 1);
  }

  private static Receipt view(AutoIngestEvent e) {
    return new Receipt(e.getId(), e.getRunId(), e.getEntityId(),
        e.getStatus(), e.getAttemptCount(), e.getErrorMessage(), e.getUpdatedAt());
  }

  /** Delivery outcome only; current asset status must still be queried from Asset Owner. */
  public record Receipt(Long eventId, Long runId, Long entityId, String status,
                        Integer attemptCount, String errorMessage, LocalDateTime updatedAt) {}
}
