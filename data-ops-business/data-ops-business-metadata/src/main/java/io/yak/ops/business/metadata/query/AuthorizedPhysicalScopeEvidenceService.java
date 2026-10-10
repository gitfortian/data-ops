package io.yak.ops.business.metadata.query;

import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.metadata.api.PhysicalSourceAccessPort;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * F-039 source projection authorization owner. Permission snapshots are checked on EVERY
 * invocation and datasource existence is re-read from the canonical project-scoped owner.
 * This grants catalog evidence only, never datasource credentials/rows or execution access.
 */
@Service
@ConditionalOnDataSourceEnabled
public class AuthorizedPhysicalScopeEvidenceService {
  private final CurrentProject currentProject;
  private final PhysicalSourceAccessPort dataSources;
  private final PhysicalScopeEvidenceQueryApi evidence;

  public AuthorizedPhysicalScopeEvidenceService(CurrentProject currentProject,
      ProjectDataSourceReadApi dataSources, PhysicalScopeEvidenceQueryApi evidence) {
    this.currentProject = currentProject;
    this.dataSources = dataSources;
    this.evidence = evidence;
  }

  public PhysicalScopeEvidenceQueryApi.Evidence read(long dataSourceId, List<String> assetKeys) {
    Long user = YakSecurityContext.getCurrentUserId();
    long project = currentProject.requireProjectId();
    if (user == null || user <= 0
        || !YakSecurityContext.hasPermission(DataSourcePermissionCode.READ)
        || !YakSecurityContext.hasPermission(MetadataPermissionCode.READ)
        || !YakSecurityContext.canAccessProject(project)) {
      throw new IllegalArgumentException("[F039_SOURCE_ACCESS_DENIED]");
    }
    if (dataSourceId <= 0 || assetKeys == null || assetKeys.isEmpty() || assetKeys.size() > 20) {
      throw new IllegalArgumentException("[F039_INVALID_SOURCE_SELECTION]");
    }
    // This is the original Datasource owner, using its project scoped repository.
    // Do not query its DAO from Agent or accept a caller-supplied ID as proof of access.
    dataSources.requireCurrentProjectSource(dataSourceId);
    var snapshot = evidence.readSelectedTables(assetKeys);
    if (snapshot.projectId() != project
        || !Objects.equals(snapshot.dataSourceId(), Long.toString(dataSourceId))) {
      throw new IllegalStateException("[F039_SOURCE_IDENTITY_DRIFT]");
    }
    return snapshot;
  }
}
