package io.yak.ops.business.sync.realtime.repository;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.DefinitionDigest;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persistence boundary for immutable published definition versions.
 *
 * <p>The compatibility CdcPipelineSpec snapshot remains a persistence representation for existing
 * published versions. It is not a second editable Definition truth model.
 */
public interface DefinitionVersionRepository {

  StoredVersion findOrCreate(PublicationCandidate candidate);

  Optional<PublicationSnapshot> find(long definitionVersionId);

  Optional<Long> publishedDefinitionVersionId(long taskId);

  void bindPublishedReference(
      long taskId,
      long definitionVersionId,
      int expectedDraftRevision,
      String expectedSourceConfigDigest);

  enum DomainMappingState {
    MAPPED,
    LEGACY_UNMAPPED
  }

  record PublicationCandidate(
      long taskId,
      int sourceDraftRevision,
      long runtimeEnvironmentId,
      CdcPipelineSpec compatibilityDefinition,
      String sourceConfigDigest,
      SyncDefinition domainDefinition,
      DefinitionDigest definitionDigest,
      DomainMappingState domainMappingState) {

    public PublicationCandidate {
      if (taskId <= 0) throw new IllegalArgumentException("TaskId 必须大于 0");
      if (sourceDraftRevision <= 0) {
        throw new IllegalArgumentException("Source DraftRevision 必须大于 0");
      }
      if (runtimeEnvironmentId <= 0) {
        throw new IllegalArgumentException("RuntimeEnvironmentId 必须大于 0");
      }
      if (compatibilityDefinition == null) {
        throw new IllegalArgumentException("Published definition snapshot 不能为空");
      }
      if (sourceConfigDigest == null || !sourceConfigDigest.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("Source config digest 无效");
      }
      if (domainMappingState == null) {
        throw new IllegalArgumentException("Domain mapping state 不能为空");
      }
      if (domainMappingState == DomainMappingState.MAPPED
          && (domainDefinition == null || definitionDigest == null)) {
        throw new IllegalArgumentException("MAPPED version 必须包含 Core Definition 与 DefinitionDigest");
      }
      if (domainMappingState == DomainMappingState.LEGACY_UNMAPPED
          && (domainDefinition != null || definitionDigest != null)) {
        throw new IllegalArgumentException("LEGACY_UNMAPPED version 不应伪造 Core Definition");
      }
    }
  }

  record StoredVersion(
      long id,
      long taskId,
      int versionNo,
      int sourceDraftRevision,
      String definitionDigest,
      String sourceConfigDigest,
      DomainMappingState domainMappingState,
      LocalDateTime createTime) {}

  record PublicationSnapshot(
      StoredVersion version,
      long runtimeEnvironmentId,
      CdcPipelineSpec compatibilityDefinition) {}
}
