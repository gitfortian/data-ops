package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.domain.DevelopmentTaskRevisionSummary;
import io.yak.ops.spi.task.model.TaskDefinition;
import java.util.List;
import java.util.Optional;

/** Durable immutable published revision repository. */
public interface DevelopmentTaskRevisionRepository {

  int nextRevisionNo(Long nodeId);

  DevelopmentTaskRevision insert(
      Long nodeId,
      int revisionNo,
      long sourceDraftRevision,
      TaskDefinition definition,
      String checksum);

  Optional<DevelopmentTaskRevision> findById(Long revisionId);

  Optional<DevelopmentTaskRevision> findLatestByNodeId(Long nodeId);

  Optional<DevelopmentTaskRevision> findByRevisionNo(Long nodeId, int revisionNo);

  List<DevelopmentTaskRevisionSummary> listByNodeId(Long nodeId);
}
