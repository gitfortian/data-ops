package io.yak.ops.business.sync.offline.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import java.util.List;
import java.util.Optional;

/** 离线同步任务定义领域仓储。 */
public interface OfflineJobDefinitionRepository {
  void lock(Long id);
  Optional<OfflineJobDefinition> findById(Long id);
  Optional<OfflineJobDefinition> findForViewById(Long id);
  boolean insert(OfflineJobDefinition definition);
  boolean update(OfflineJobDefinition definition);
  boolean delete(Long id);
  boolean existsByName(String jobName, Long excludeId);
  PageData<OfflineJobDefinition> page(OfflineDefinitionQuery query);
  PageData<OfflineJobDefinition> pageForView(OfflineDefinitionQuery query);

  /** Cross-Project dispatcher identity only; callers must restore Project before business IO. */
  List<ProjectDefinitionRef> findScheduledForReconciliation();

  record ProjectDefinitionRef(long projectId, long definitionId) {
    public ProjectDefinitionRef {
      if (projectId <= 0L) throw new IllegalArgumentException("projectId 必须大于 0");
      if (definitionId <= 0L) throw new IllegalArgumentException("definitionId 必须大于 0");
    }
  }
}
