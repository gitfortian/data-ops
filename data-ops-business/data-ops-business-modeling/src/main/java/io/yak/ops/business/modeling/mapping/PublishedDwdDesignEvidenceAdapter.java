package io.yak.ops.business.modeling.mapping;

import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.modeling.PublishedDwdDesignEvidenceProvider;
import org.springframework.stereotype.Component;

/**
 * Exposes only read-only Modeling truth. DataDev must not query modeling DAO
 * or treat this result as authority for any production write.
 */
@Component
public class PublishedDwdDesignEvidenceAdapter implements PublishedDwdDesignEvidenceProvider {
  private final DwdDataDevHandoffEvidenceService handoff;
  private final CurrentProject currentProject;

  public PublishedDwdDesignEvidenceAdapter(
      DwdDataDevHandoffEvidenceService handoff, CurrentProject currentProject) {
    this.handoff = handoff;
    this.currentProject = currentProject;
  }

  @Override
  public Evidence inspect(long modelId, int versionNo) {
    long projectId = currentProject.requireProjectId();
    var checked = handoff.inspect(modelId, versionNo);
    var version = checked.readiness().version();
    var publication = checked.publication();
    if (publication.mapping() == null || version == null
        || !checked.readiness().modelId().equals(modelId)
        || !publication.modelVersionId().equals(version.versionId())) {
      throw new IllegalStateException("没有完整冻结的 DWD 发布版本与 MappingRevision");
    }
    var mapping = publication.mapping();
    return new Evidence(
        projectId, modelId, version.versionId(), version.versionNo(),
        checked.readiness().modelCode(),
        mapping.mappingRevisionId(), mapping.contentSha256(),
        mapping.logicalSnapshotSha256(), mapping.physicalStructureSha256(),
        checked.readiness().currentStandardEvidenceSha256(),
        "REVIEWABLE_ONLY".equals(checked.status()),
        checked.status());
  }
}
