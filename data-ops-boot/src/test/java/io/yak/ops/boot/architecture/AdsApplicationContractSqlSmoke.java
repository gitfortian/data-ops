package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs against the isolated CI MySQL / PostgreSQL migration databases only. */
final class AdsApplicationContractSqlSmoke {
  private AdsApplicationContractSqlSmoke() {}

  static void verify(JdbcTemplate jdbc, PlatformTransactionManager manager) {
    long projectId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    long otherProjectId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    long modelId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    var tx = new TransactionTemplate(manager);
    String design = "yak_modeling_ads_application_design";
    String revision = "yak_modeling_ads_application_revision";
    String insertDesign = "INSERT INTO " + design
        + "(project_id,model_id,revision,contract_sha256,contract_json,updated_by,updated_at)"
        + " VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)";
    String insertRevision = "INSERT INTO " + revision
        + "(project_id,model_id,revision,contract_sha256,contract_json,updated_by,updated_at)"
        + " VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)";
    try {
      tx.executeWithoutResult(status -> {
        jdbc.update(insertDesign, projectId, modelId, 1L, "a".repeat(64), "{}", "ci");
        jdbc.update(insertRevision, projectId, modelId, 1L, "a".repeat(64), "{}", "ci");
        // Same model ID under another project is a distinct tenant identity.
        jdbc.update(insertDesign, otherProjectId, modelId, 1L, "b".repeat(64), "{}", "ci");
        jdbc.update(insertRevision, otherProjectId, modelId, 1L, "b".repeat(64), "{}", "ci");
      });
      assertThat(jdbc.queryForObject(
          "SELECT COUNT(*) FROM " + design + " WHERE model_id=?", Long.class, modelId))
          .isEqualTo(2L);
      assertThat(jdbc.queryForObject(
          "SELECT COUNT(*) FROM " + revision + " WHERE model_id=?", Long.class, modelId))
          .isEqualTo(2L);

      assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
        jdbc.update("UPDATE " + design
            + " SET revision=2,contract_sha256=?,contract_json='{}' WHERE project_id=? AND model_id=?",
            "c".repeat(64), projectId, modelId);
        jdbc.update(insertRevision, projectId, modelId, 2L, "c".repeat(64), "{}", "ci");
        // Must fail on scoped immutable revision unique key and roll back the entire write.
        jdbc.update(insertRevision, projectId, modelId, 2L, "d".repeat(64), "{}", "ci");
      })).isInstanceOf(DuplicateKeyException.class);
      assertThat(jdbc.queryForObject(
          "SELECT revision FROM " + design + " WHERE project_id=? AND model_id=?",
          Long.class, projectId, modelId)).isEqualTo(1L);
      assertThat(jdbc.queryForObject(
          "SELECT COUNT(*) FROM " + revision + " WHERE project_id=? AND model_id=?",
          Long.class, projectId, modelId)).isEqualTo(1L);
    } finally {
      tx.executeWithoutResult(status -> {
        jdbc.update("DELETE FROM " + revision + " WHERE model_id=? AND project_id IN (?,?)",
            modelId, projectId, otherProjectId);
        jdbc.update("DELETE FROM " + design + " WHERE model_id=? AND project_id IN (?,?)",
            modelId, projectId, otherProjectId);
      });
    }
  }
}
