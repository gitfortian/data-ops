package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.OfflineJobRevision;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for append-only published Offline Sync revisions. */
public interface OfflineJobRevisionRepository {

  List<OfflineJobRevision> findAllByJobDefinitionId(Long jobDefinitionId);

  Optional<OfflineJobRevision> findByJobDefinitionIdAndVersionNo(
      Long jobDefinitionId, int versionNo);

  Optional<OfflineJobRevision> findById(Long revisionId);

  List<OfflineJobRevision> findLatestByJobDefinitionIds(Collection<Long> jobDefinitionIds);

  Optional<OfflineJobRevision> findLatestByJobDefinitionId(Long jobDefinitionId);

  int nextVersionNo(Long jobDefinitionId);

  OfflineJobRevision insert(OfflineJobRevision revision);
}
