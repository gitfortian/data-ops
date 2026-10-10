package io.yak.ops.business.metadata.controller.v1;

import io.yak.framework.common.Result;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi;
import io.yak.ops.business.metadata.query.AuthorizedPhysicalScopeEvidenceService;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.ProjectMigrationMode;
import io.yak.ops.core.project.ProjectScope;
import java.util.List;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only bounded schema preview; a preview does NOT create or approve an Agent task. */
@RestController
@ConditionalOnDataSourceEnabled
@RequestMapping("/api/v1/metadata/source-semantic")
@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)
@RequiresPermission(MetadataPermissionCode.READ)
public class SourceSemanticEvidenceController {
  private final AuthorizedPhysicalScopeEvidenceService authorized;
  public SourceSemanticEvidenceController(AuthorizedPhysicalScopeEvidenceService authorized) {
    this.authorized = authorized;
  }

  public record PreviewRequest(long dataSourceId, List<String> tableAssetKeys) {}

  @PostMapping("/preview")
  public Result<PhysicalScopeEvidenceQueryApi.Evidence> preview(@RequestBody PreviewRequest request) {
    Objects.requireNonNull(request, "request");
    return Result.success(authorized.read(request.dataSourceId(), request.tableAssetKeys()));
  }
}
