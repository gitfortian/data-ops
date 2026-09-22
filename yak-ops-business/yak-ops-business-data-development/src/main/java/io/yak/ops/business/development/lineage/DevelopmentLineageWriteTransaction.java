package io.yak.ops.business.development.lineage;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskRevision;
import io.yak.ops.business.development.service.DevelopmentSqlLineageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Atomically replaces table and column lineage in one independent short transaction. */
@Component
public class DevelopmentLineageWriteTransaction {
  private final JdbcTemplate jdbc;
  private final DevelopmentSqlLineageService lineage;

  public DevelopmentLineageWriteTransaction(JdbcTemplate jdbc,
      DevelopmentSqlLineageService lineage) {
    this.jdbc = jdbc;
    this.lineage = lineage;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", propagation = Propagation.REQUIRES_NEW)
  public void writeIfLatest(DevelopmentNode node, DevelopmentTaskRevision revision,
      DevelopmentSqlLineageService.PreparedLineage prepared) {
    Long latestId = jdbc.query("SELECT id FROM yak_dev_task_revision WHERE node_id=? "
            + "ORDER BY revision_no DESC LIMIT 1 FOR UPDATE",
        rs -> rs.next() ? rs.getLong(1) : null, node.id());
    if (!revision.id().equals(latestId)) return;
    lineage.applyPrepared(node, revision, prepared);
  }
}
