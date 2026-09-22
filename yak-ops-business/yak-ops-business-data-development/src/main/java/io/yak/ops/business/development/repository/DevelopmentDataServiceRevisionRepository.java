package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevision;
import io.yak.ops.business.development.domain.DevelopmentDataServiceRevisionSummary;
import java.util.List;
import java.util.Optional;

public interface DevelopmentDataServiceRevisionRepository {

  int nextRevisionNo(Long nodeId);

  DevelopmentDataServiceRevision insert(
      Long nodeId,
      int revisionNo,
      long sourceDraftRevision,
      DevelopmentDataServiceDefinition definition,
      String checksum);

  Optional<DevelopmentDataServiceRevision> findLatestByNodeId(Long nodeId);

  Optional<DevelopmentDataServiceRevision> findByRevisionNo(Long nodeId, int revisionNo);

  List<DevelopmentDataServiceRevisionSummary> listByNodeId(Long nodeId);
}
