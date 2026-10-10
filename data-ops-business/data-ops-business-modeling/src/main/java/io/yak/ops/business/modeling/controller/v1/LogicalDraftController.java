package io.yak.ops.business.modeling.controller.v1;

import io.yak.framework.common.Result;
import io.yak.framework.security.extend.CurrentUserProvider;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.modeling.dao.model.LogicalModelPO;
import io.yak.ops.business.modeling.logical.LogicalDraftService;
import io.yak.ops.business.modeling.logical.LogicalDraftService.*;
import io.yak.ops.common.constant.modeling.ModelingPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Logical Model draft authoring. Always bound to trusted Project Space.
 * Version endpoint freezes DRAFT snapshots only; it does not publish/deploy.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/modeling/logical-models")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(ModelingPermissionCode.READ)
public class LogicalDraftController {

  private final LogicalDraftService service;
  private final CurrentUserProvider currentUserProvider;

  @GetMapping
  public Result<List<LogicalModelPO>> list() {
    return Result.success(service.list());
  }

  @GetMapping("/{id}")
  public Result<DraftView> get(@PathVariable Long id) {
    return Result.success(service.get(id));
  }

  @PostMapping
  @RequiresPermission(ModelingPermissionCode.CREATE)
  public Result<DraftView> create(@RequestBody NewDraft request, HttpServletRequest http) {
    return Result.success(service.create(request, currentUserProvider.getCurrentUser(http)));
  }

  @PostMapping("/{id}/entities")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<DraftView> entity(@PathVariable Long id, @RequestBody NewEntity request,
                                  @RequestParam Long expectedRevision, HttpServletRequest http) {
    return Result.success(service.addEntity(id, request, currentUserProvider.getCurrentUser(http), expectedRevision));
  }

  @PostMapping("/{id}/entities/{entityId}/attributes")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<DraftView> attribute(@PathVariable Long id, @PathVariable Long entityId,
                                     @RequestBody NewAttribute request, @RequestParam Long expectedRevision) {
    return Result.success(service.addAttribute(id, entityId, request, expectedRevision));
  }

  @PutMapping("/{id}/entities/{entityId}")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<DraftView> editEntity(@PathVariable Long id, @PathVariable Long entityId,
      @RequestBody NewEntity request, @RequestParam Long expectedRevision) {
    return Result.success(service.updateEntity(id, entityId, request, expectedRevision));
  }

  @PutMapping("/{id}/entities/{entityId}/attributes/{attributeId}")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<DraftView> editAttribute(@PathVariable Long id, @PathVariable Long entityId,
      @PathVariable Long attributeId, @RequestBody NewAttribute request,
      @RequestParam Long expectedRevision) {
    return Result.success(service.updateAttribute(id, entityId, attributeId, request, expectedRevision));
  }

  @PutMapping("/{id}/relations/{relationId}")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<DraftView> editRelation(@PathVariable Long id, @PathVariable Long relationId,
      @RequestBody NewRelation request, @RequestParam Long expectedRevision) {
    return Result.success(service.updateRelation(id, relationId, request, expectedRevision));
  }

  @PostMapping("/{id}/relations")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<DraftView> relation(@PathVariable Long id, @RequestBody NewRelation request,
      @RequestParam Long expectedRevision) {
    return Result.success(service.addRelation(id, request, expectedRevision));
  }

  @PostMapping("/{id}/versions")
  @RequiresPermission(ModelingPermissionCode.UPDATE)
  public Result<VersionView> freeze(@PathVariable Long id, @RequestParam Long expectedRevision,
      HttpServletRequest http) {
    return Result.success(service.freezeDraft(id, currentUserProvider.getCurrentUser(http), expectedRevision));
  }

  @GetMapping("/{id}/versions")
  public Result<List<VersionView>> versions(@PathVariable Long id) {
    return Result.success(service.versions(id));
  }

  @GetMapping("/{id}/versions/{versionNo}")
  public Result<String> versionSnapshot(@PathVariable Long id, @PathVariable int versionNo) {
    return Result.success(service.versionSnapshot(id, versionNo));
  }
}
