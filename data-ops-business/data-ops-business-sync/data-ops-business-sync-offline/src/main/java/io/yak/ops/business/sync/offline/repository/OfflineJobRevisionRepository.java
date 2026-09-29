package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.common.bean.po.sync.offline.OfflineJobRevisionPO;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence operations for append-only published Offline Sync revisions. */
public interface OfflineJobRevisionRepository {

  List<OfflineJobRevisionPO> findAllByJobDefinitionId(Long jobDefinitionId);

  Optional<OfflineJobRevisionPO> findByJobDefinitionIdAndVersionNo(
      Long jobDefinitionId, int versionNo);

  Optional<OfflineJobRevisionPO> findById(Long revisionId);

  List<OfflineJobRevisionPO> findLatestByJobDefinitionIds(Collection<Long> jobDefinitionIds);

  Optional<OfflineJobRevisionPO> findLatestByJobDefinitionId(Long jobDefinitionId);

  int nextVersionNo(Long jobDefinitionId);

  OfflineJobRevisionPO insert(OfflineJobRevisionPO revision);
}
