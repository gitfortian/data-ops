package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared real MySQL / PostgreSQL V7 contract: one DWS design per project/model. */
final class DwsGrainSqlSmoke {
  private DwsGrainSqlSmoke() {}

  static void verify(JdbcTemplate jdbc, PlatformTransactionManager txManager, long projectId) {
    long modelId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
    String digest = "b".repeat(64);
    var transaction = new TransactionTemplate(txManager);
    transaction.execute(status -> {
      insert(jdbc, projectId, modelId, digest);
      assertThat(jdbc.queryForObject(
          "SELECT COUNT(*) FROM yak_modeling_dws_grain_design "
              + "WHERE project_id=? AND model_id=?", Long.class, projectId, modelId))
          .isEqualTo(1L);
      status.setRollbackOnly();
      return null;
    });
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM yak_modeling_dws_grain_design "
            + "WHERE project_id=? AND model_id=?", Long.class, projectId, modelId))
        .isZero();
    insert(jdbc, projectId, modelId, digest);
    assertThatThrownBy(() -> insert(jdbc, projectId, modelId, digest))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.update("UPDATE yak_modeling_dws_grain_design "
            + "SET revision=revision+1 WHERE project_id=? AND model_id=? AND revision=1",
            projectId, modelId))
        .isEqualTo(1);
    assertThat(jdbc.update("UPDATE yak_modeling_dws_grain_design "
            + "SET revision=revision+1 WHERE project_id=? AND model_id=? AND revision=1",
            projectId, modelId))
        .isZero();
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM yak_modeling_dws_grain_design "
            + "WHERE project_id=? AND model_id=?", Long.class, projectId + 1, modelId))
        .isZero();

    assertThat(jdbc.update(
        "INSERT INTO yak_modeling_dws_grain_revision "
            + "(project_id,model_id,revision,structure_fingerprint,design_sha256,"
            + "design_json,updated_by,updated_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
        projectId, modelId, 1, "a".repeat(64), digest, "{}", "ci"))
        .isEqualTo(1);
    assertThatThrownBy(() -> jdbc.update(
        "INSERT INTO yak_modeling_dws_grain_revision "
            + "(project_id,model_id,revision,structure_fingerprint,design_sha256,"
            + "design_json,updated_by,updated_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
        projectId, modelId, 1, "a".repeat(64), digest, "{}", "ci"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM yak_modeling_dws_grain_revision "
            + "WHERE project_id=? AND model_id=? AND revision=1",
        Long.class, projectId, modelId)).isEqualTo(1L);
  }

  private static void insert(JdbcTemplate jdbc, long projectId, long modelId, String digest) {
    assertThat(jdbc.update(
        "INSERT INTO yak_modeling_dws_grain_design "
            + "(project_id,model_id,structure_fingerprint,design_sha256,"
            + "design_json,revision,updated_by,updated_at) "
            + "VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
        projectId, modelId, "a".repeat(64), digest, "{}", 1, "ci"))
        .isEqualTo(1);
  }
}
