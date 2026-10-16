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
      verifyPublishAtomicity(jdbc, manager);
    } finally {
      jdbc.update("DELETE FROM yak_modeling_mapping_revision WHERE project_id IN (?, ?)",
          projectId, otherProjectId);
      jdbc.update("DELETE FROM yak_modeling_mapping_set WHERE project_id IN (?, ?)",
          projectId, otherProjectId);
    }
  }
  /**
   * Cross-table evidence for the real database transaction boundary:
   * a physical DWD design publication must never leave a ModelVersion,
   * MappingRevision or a moved published pointer after a failure.
   * This tests SQL storage/transaction semantics, not the full application API.
   */
  private static void verifyPublishAtomicity(JdbcTemplate jdbc, PlatformTransactionManager manager) {
    long projectId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    String modelCode = "r12_dwd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    jdbc.update("""
        INSERT INTO yak_modeling_model
          (project_id, model_code, model_name, dialect, layer_code, status)
        VALUES (?, ?, 'DWD order-detail acceptance', 'MYSQL', 'DWD', 'DRAFT')
        """, projectId, modelCode);
    Long modelId = jdbc.queryForObject("""
        SELECT id FROM yak_modeling_model WHERE project_id = ? AND model_code = ?
        """, Long.class, projectId, modelCode);
    assertThat(modelId).isPositive();
    try {
      assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> {
        publishFixture(jdbc, projectId, modelId);
        throw new SimulatedPublishFailure();
      })).isInstanceOf(SimulatedPublishFailure.class);

      assertThat(jdbc.queryForObject("""
          SELECT COUNT(*) FROM yak_modeling_mapping_set
          WHERE project_id = ? AND physical_model_id = ?
          """, Long.class, projectId, modelId)).isZero();
      assertThat(jdbc.queryForObject("""
          SELECT COUNT(*) FROM yak_modeling_mapping_revision WHERE project_id = ?
          """, Long.class, projectId)).isZero();
      assertThat(jdbc.queryForObject("""
          SELECT COUNT(*) FROM yak_modeling_model_version
          WHERE project_id = ? AND model_id = ?
          """, Long.class, projectId, modelId)).isZero();
      assertThat(jdbc.queryForObject("""
          SELECT status FROM yak_modeling_model WHERE project_id = ? AND id = ?
          """, String.class, projectId, modelId)).isEqualTo("DRAFT");
      assertThat(jdbc.queryForObject("""
          SELECT latest_version_no FROM yak_modeling_model
          WHERE project_id = ? AND id = ?
          """, Integer.class, projectId, modelId)).isZero();

      // A subsequent clean transaction must be able to publish the same exact
      // identity without seeing abandoned IDs or partially committed data.
      var committed = new TransactionTemplate(manager).execute(status ->
          publishFixture(jdbc, projectId, modelId));
      assertThat(committed).isNotNull();
      assertThat(jdbc.queryForObject("""
          SELECT published_version_id FROM yak_modeling_model
          WHERE project_id = ? AND id = ?
          """, Long.class, projectId, modelId)).isEqualTo(committed.versionId());
      assertThat(jdbc.queryForObject("""
          SELECT latest_version_no FROM yak_modeling_model
          WHERE project_id = ? AND id = ?
          """, Integer.class, projectId, modelId)).isEqualTo(1);
      assertThat(jdbc.queryForObject("""
          SELECT meta_json FROM yak_modeling_model_version
          WHERE project_id = ? AND model_id = ? AND version_no = 1
          """, String.class, projectId, modelId))
          .contains("\"mappingRevisionId\":" + committed.revisionId())
          .contains("\"mappingSetId\":" + committed.mappingSetId());
    } finally {
      jdbc.update("DELETE FROM yak_modeling_model_version WHERE project_id = ? AND model_id = ?",
          projectId, modelId);
      jdbc.update("DELETE FROM yak_modeling_mapping_revision WHERE project_id = ?", projectId);
      jdbc.update("""
          DELETE FROM yak_modeling_mapping_set WHERE project_id = ? AND physical_model_id = ?
          """, projectId, modelId);
      jdbc.update("DELETE FROM yak_modeling_model WHERE project_id = ? AND id = ?",
          projectId, modelId);
    }
  }

  private record PublicationFixture(long mappingSetId, long revisionId, long versionId) {}
  private static final class SimulatedPublishFailure extends RuntimeException {}

  private static PublicationFixture publishFixture(
      JdbcTemplate jdbc, long projectId, long modelId) {
    String logicalSha = "a".repeat(64);
    String physicalSha = "b".repeat(64);
    String contentSha = "c".repeat(64);
    jdbc.update("""
        INSERT INTO yak_modeling_mapping_set
          (project_id, physical_model_id, logical_model_id, logical_version_no,
           logical_snapshot_sha, physical_structure_sha, draft_revision,
           mapping_json, updated_by, updated_at)
        VALUES (?, ?, 77, 2, ?, ?, 1, '[]', 'architecture_ci', CURRENT_TIMESTAMP)
        """, projectId, modelId, logicalSha, physicalSha);
    Long mappingSet = jdbc.queryForObject("""
        SELECT id FROM yak_modeling_mapping_set
        WHERE project_id = ? AND physical_model_id = ?
        """, Long.class, projectId, modelId);
    jdbc.update("""
        INSERT INTO yak_modeling_mapping_revision
          (project_id, mapping_set_id, revision_no, draft_revision,
           logical_snapshot_sha, physical_structure_sha, content_sha,
           frozen_json, frozen_by, frozen_at)
        VALUES (?, ?, 1, 1, ?, ?, ?, '[]', 'architecture_ci', CURRENT_TIMESTAMP)
        """, projectId, mappingSet, logicalSha, physicalSha, contentSha);
    Long revision = jdbc.queryForObject("""
        SELECT id FROM yak_modeling_mapping_revision
        WHERE project_id = ? AND mapping_set_id = ? AND revision_no = 1
        """, Long.class, projectId, mappingSet);
    String meta = "{\"mappingRevisionId\":" + revision
        + ",\"mappingSetId\":" + mappingSet
        + ",\"logicalSnapshotSha256\":\"" + logicalSha + "\"}";
    jdbc.update("""
        INSERT INTO yak_modeling_model_version
          (project_id, model_id, version_no, structure_json, meta_json,
           column_count, checksum, published_by, publish_time)
        VALUES (?, ?, 1, '{}', ?, 0, ?, 'architecture_ci', CURRENT_TIMESTAMP)
        """, projectId, modelId, meta, physicalSha);
    Long version = jdbc.queryForObject("""
        SELECT id FROM yak_modeling_model_version
        WHERE project_id = ? AND model_id = ? AND version_no = 1
        """, Long.class, projectId, modelId);
    assertThat(jdbc.update("""
        UPDATE yak_modeling_model
        SET status = 'PUBLISHED', published_version_id = ?, latest_version_no = 1
        WHERE project_id = ? AND id = ? AND status = 'DRAFT'
        """, version, projectId, modelId)).isEqualTo(1);
    return new PublicationFixture(mappingSet, revision, version);
  }

}
