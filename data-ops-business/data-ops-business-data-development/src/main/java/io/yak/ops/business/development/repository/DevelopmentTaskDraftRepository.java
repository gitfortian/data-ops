package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.Optional;

/** Durable mutable draft repository with optimistic revision checks. */
public interface DevelopmentTaskDraftRepository {

  Optional<DevelopmentTaskDraft> findByNodeId(Long nodeId);

  Optional<DevelopmentTaskDraft> findByNodeIdForUpdate(Long nodeId);

  /** Returns empty when the expected base revision no longer matches the stored draft. */
  Optional<DevelopmentTaskDraft> save(
      Long nodeId,
      TaskDefinition definition,
      long expectedBaseRevision);
}
