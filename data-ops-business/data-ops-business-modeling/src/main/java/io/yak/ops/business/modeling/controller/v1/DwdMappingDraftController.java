package io.yak.ops.business.modeling.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.mapping.DwdMappingDraftService;
import io.yak.ops.business.modeling.mapping.DwdMappingPublicationService;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.modeling.mapping.DwdMappingDraftService.Draft;
import io.yak.ops.business.modeling.mapping.DwdMappingDraftService.Frozen;
import io.yak.ops.business.modeling.mapping.DwdMappingDraftService.SaveRequest;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * R12 DWD-only mapping authoring. No physical model publish or DataDev write path.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/models/{id}/logical-mapping")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class DwdMappingDraftController {
  private final DwdMappingDraftService service;
  private final DwdMappingPublicationService publisher;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping
  public Result<Draft> get(@PathVariable Long id) {
    return Result.success(service.get(id));
  }

  @PutMapping
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<Draft> save(@PathVariable Long id, @RequestBody SaveRequest request,
                            HttpServletRequest http) {
    return Result.success(service.save(id, request, currentUserProvider.getCurrentUser(http)));
  }

  @PostMapping("/revisions")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<Frozen> freeze(@PathVariable Long id, @RequestParam Long expectedDraftRevision,
                               HttpServletRequest http) {
    return Result.success(service.freeze(id, expectedDraftRevision,
        currentUserProvider.getCurrentUser(http)));
  }

  @GetMapping("/revisions")
  public Result<List<Frozen>> listRevisions(@PathVariable Long id) {
    return Result.success(service.revisions(id));
  }

  @GetMapping("/publication-policy")
  public Result<DwdMappingPublicationService.PublicationPolicy> publicationPolicy(
      @PathVariable Long id) {
    return Result.success(publisher.policy(id));
  }

  /** Explicit opt-in only; the legacy physical publish is unchanged. */
  @PostMapping("/publish-with-mapping")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<DwdMappingPublicationService.BoundReceipt> publishWithMapping(
      @PathVariable Long id, @RequestParam Long mappingRevisionId, HttpServletRequest http) {
    return Result.success(publisher.publish(id, mappingRevisionId,
        currentUserProvider.getCurrentUser(http)));
  }

  @PostMapping("/publish-approval-with-mapping")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<ApprovalInstanceView> submitPublishApproval(
      @PathVariable Long id, @RequestParam Long mappingRevisionId, HttpServletRequest http) {
    return Result.success(publisher.submitApproval(id, mappingRevisionId,
        currentUserProvider.getCurrentUser(http)));
  }

  @GetMapping("/published-versions/{versionNo}")
  public Result<DwdMappingPublicationService.History> readPublishedBinding(
      @PathVariable Long id, @PathVariable int versionNo) {
    return Result.success(publisher.history(id, versionNo));
  }

  @GetMapping("/revisions/{revisionId}")
  public Result<DwdMappingDraftService.HistoricalRevision> revisionDetail(
      @PathVariable Long id, @PathVariable Long revisionId) {
    return Result.success(service.revisionDetail(id, revisionId));
  }
}
