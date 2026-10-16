package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real database contract for the R12 V6 mapping roots and frozen revisions.
 * Only invoked by CI's isolated MySQL / PostgreSQL migration smoke tests.
 * Does not claim that a logical/physical design has been published or executed.
 */
final class DwdMappingSqlSmoke {
  private DwdMappingSqlSmoke() {}

  static void verify(DataSource dataSource, PlatformTransactionManager manager) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    long projectId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    long otherProjectId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    long physicalModelId = UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE;
    String logicalSha = "a".repeat(64);
    String physicalSha = "b".repeat(64);
    String contentSha = "c".repeat(64);
    String mappingInsert = """
        INSERT INTO yak_modeling_mapping_set
          (project_id, physical_model_id, logical_model_id, logical_version_no,
           logical_snapshot_sha, physical_structure_sha, draft_revision,
           mapping_json, updated_by, updated_at)
        VALUES (?, ?, 77, 2, ?, ?, 1, '[]', 'architecture_ci', CURRENT_TIMESTAMP)
        """;
    String revisionInsert = """
        INSERT INTO yak_modeling_mapping_revision
          (project_id, mapping_set_id, revision_no, draft_revision,
           logical_snapshot_sha, physical_structure_sha, content_sha,
           frozen_json, frozen_by, frozen_at)
        VALUES (?, ?, 1, 1, ?, ?, ?, '[]', 'architecture_ci', CURRENT_TIMESTAMP)
        """;
    String countRoots = """
        SELECT COUNT(*) FROM yak_modeling_mapping_set
        WHERE project_id = ? AND physical_model_id = ?
        """;
    try {
      // Both append-only revision and draft root must roll back together.
      new TransactionTemplate(manager).execute(status -> {
        jdbc.update(mappingInsert, projectId, physicalModelId, logicalSha, physicalSha);
        Long root = jdbc.queryForObject(
            "SELECT id FROM yak_modeling_mapping_set WHERE project_id = ? AND physical_model_id = ?",
            Long.class, projectId, physicalModelId);
        assertThat(root).isPositive();
        jdbc.update(revisionInsert, projectId, root, logicalSha, physicalSha, contentSha);
        status.setRollbackOnly();
        return null;
      });
      assertThat(jdbc.queryForObject(countRoots, Long.class, projectId, physicalModelId))
          .isZero();
      assertThat(jdbc.queryForObject("""
          SELECT COUNT(*) FROM yak_modeling_mapping_revision WHERE project_id = ?
          """, Long.class, projectId)).isZero();

      jdbc.update(mappingInsert, projectId, physicalModelId, logicalSha, physicalSha);
      // Same physical ID in another project is a distinct authorized root.
      jdbc.update(mappingInsert, otherProjectId, physicalModelId, logicalSha, physicalSha);
      assertThatThrownBy(
          () -> jdbc.update(mappingInsert, projectId, physicalModelId, logicalSha, physicalSha))
          .isInstanceOf(DuplicateKeyException.class);
      Long root = jdbc.queryForObject("""
          SELECT id FROM yak_modeling_mapping_set
          WHERE project_id = ? AND physical_model_id = ?
          """, Long.class, projectId, physicalModelId);
      assertThat(root).isPositive();
      jdbc.update(revisionInsert, projectId, root, logicalSha, physicalSha, contentSha);
      assertThatThrownBy(
          () -> jdbc.update(revisionInsert, projectId, root, logicalSha, physicalSha, contentSha))
          .isInstanceOf(DuplicateKeyException.class);
      assertThat(jdbc.queryForObject("""
          SELECT COUNT(*) FROM yak_modeling_mapping_revision
          WHERE project_id = ? AND mapping_set_id = ?
          """, Long.class, projectId, root)).isEqualTo(1L);
      assertThat(jdbc.queryForObject(countRoots, Long.class, otherProjectId, physicalModelId))
          .isEqualTo(1L);
    } finally {
      jdbc.update("DELETE FROM yak_modeling_mapping_revision WHERE project_id IN (?, ?)",
          projectId, otherProjectId);
      jdbc.update("DELETE FROM yak_modeling_mapping_set WHERE project_id IN (?, ?)",
          projectId, otherProjectId);
    }
  }
}
