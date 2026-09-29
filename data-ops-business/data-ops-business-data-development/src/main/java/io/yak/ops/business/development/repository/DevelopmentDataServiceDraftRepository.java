package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.domain.DevelopmentDataServiceDefinition;
import io.yak.ops.business.development.domain.DevelopmentDataServiceDraft;
import java.util.Optional;

public interface DevelopmentDataServiceDraftRepository {

  Optional<DevelopmentDataServiceDraft> findByNodeId(Long nodeId);

  Optional<DevelopmentDataServiceDraft> findByNodeIdForUpdate(Long nodeId);

  Optional<DevelopmentDataServiceDraft> save(
      Long nodeId,
      DevelopmentDataServiceDefinition definition,
      long expectedBaseRevision);
}
