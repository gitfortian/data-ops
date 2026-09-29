package io.yak.ops.business.development.lineage;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.development.service.DevelopmentSqlLineageService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomically replaces table and column lineage in one independent short transaction. */
@Component
public class DevelopmentLineageWriteTransaction {
  private final DevelopmentTaskRevisionRepository revisionRepository;
  private final DevelopmentSqlLineageService lineage;

  public DevelopmentLineageWriteTransaction(DevelopmentTaskRevisionRepository revisionRepository,
      DevelopmentSqlLineageService lineage) {
    this.revisionRepository = revisionRepository;
    this.lineage = lineage;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", propagation = Propagation.REQUIRES_NEW)
  public void writeIfLatest(DevelopmentNode node, DevelopmentTaskRevision revision,
      DevelopmentSqlLineageService.PreparedLineage prepared) {
    Long latestId = revisionRepository.findLatestIdForUpdateByNodeId(node.id()).orElse(null);
    if (!revision.id().equals(latestId)) return;
    lineage.applyPrepared(node, revision, prepared);
  }
}
