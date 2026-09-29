package io.yak.ops.business.dataset.gateway.taskcatalog;

import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.business.taskcatalog.domain.TaskAssetRevision;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Converts Task Catalog domain objects into Dataset-owned immutable source snapshots. */
@Component
public class TaskCatalogDatasetAdapter implements DatasetTaskCatalogGateway {

  private final TaskCatalogService taskCatalogService;
  private final CurrentProject currentProject;

  @Autowired
  public TaskCatalogDatasetAdapter(
      TaskCatalogService taskCatalogService, CurrentProject currentProject) {
    this.taskCatalogService = taskCatalogService;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped access fails closed. */
  public TaskCatalogDatasetAdapter(TaskCatalogService taskCatalogService) {
    this(taskCatalogService, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public DatasetTaskAssetSnapshot get(long assetId) {
    TaskAsset asset = taskCatalogService.get(assetId);
    requireCurrentProject(asset.projectId());
    long revisionId =
        asset.currentRevision() == null ? 0L : asset.currentRevision().taskRevisionId();
    int revisionNo = asset.currentRevision() == null ? 0 : asset.currentRevision().revisionNo();
    SourceOrigin sourceOrigin =
        asset.source() == TaskAssetSource.DATA_DEVELOPMENT
            ? SourceOrigin.DATA_DEVELOPMENT
            : SourceOrigin.OTHER;
    SourceAvailability availability =
        asset.status() == TaskAssetStatus.ONLINE
            ? SourceAvailability.ONLINE
            : SourceAvailability.NOT_ONLINE;
    return new DatasetTaskAssetSnapshot(
        asset.id(),
        asset.name(),
        asset.sourceRef(),
        sourceOrigin,
        availability,
        asset.taskType(),
        revisionId,
        revisionNo);
  }

  @Override
  public DatasetTaskRevisionSnapshot resolveRevision(long assetId, long revisionId) {
    TaskAssetRevision resolved = taskCatalogService.resolveRevision(assetId, revisionId);
    requireCurrentProject(resolved.asset().projectId());
    requireCurrentProject(resolved.revision().sourceProjectId());
    return new DatasetTaskRevisionSnapshot(
        assetId,
        resolved.revision().revisionId(),
        resolved.revision().revisionNo(),
        resolved.revision().definition().taskType(),
        resolved.revision().definition().content(),
        resolved.revision().definition().configJson());
  }

  private void requireCurrentProject(Long sourceProjectId) {
    Long projectId = currentProject.requireProjectId();
    if (!Objects.equals(projectId, sourceProjectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
  }
}
