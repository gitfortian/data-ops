package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs the same DataDev V2 rollback and uniqueness acceptance on disposable MySQL and PostgreSQL. */
final class DwdTaskBindingSqlSmoke {
  private DwdTaskBindingSqlSmoke() {}

  /**
   * R12-2 forward V2 storage smoke on the disposable CI PostgreSQL database.
   * No binding API is enabled and no SQL Runtime execution is performed.
   */
  static void verifyDwdBindingForwardMigration(
      JdbcTemplate jdbc, PlatformTransactionManager manager, long projectId) {
    String sql = "INSERT INTO yak_dev_model_task_binding "
        + "(project_id, model_id, model_version_id, model_version_no, "
        + "mapping_revision_id, mapping_sha256, logical_sha256, physical_sha256, "
        + "current_standard_sha256, node_id, task_revision_id, task_revision_no, "
        + "task_definition_sha256, target_datasource_id, target_database, "
        + "target_schema, target_table, write_mode, binding_digest, "
        + "idempotency_key, state, create_time) "
        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)";
    var tx = new TransactionTemplate(manager);
    String key = UUID.randomUUID().toString();
    tx.execute(status -> {
      insertDwdBindingRow(jdbc, sql, projectId, 7001L, 8001L, key);
      assertThat(jdbc.queryForObject(
          "SELECT COUNT(*) FROM yak_dev_model_task_binding "
              + "WHERE project_id=? AND idempotency_key=?", Long.class, projectId, key))
          .isEqualTo(1L);
      status.setRollbackOnly();
      return null;
    });
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM yak_dev_model_task_binding "
            + "WHERE project_id=? AND idempotency_key=?", Long.class, projectId, key))
        .isZero();

    insertDwdBindingRow(jdbc, sql, projectId, 7001L, 8001L, key);
    assertThatThrownBy(() ->
        insertDwdBindingRow(jdbc, sql, projectId, 7002L, 8002L, key))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
        insertDwdBindingRow(jdbc, sql, projectId, 7001L, 8001L,
            UUID.randomUUID().toString()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject(
        "SELECT COUNT(*) FROM yak_dev_model_task_binding "
            + "WHERE project_id=? AND node_id=? AND task_revision_id=?",
        Long.class, projectId, 7001L, 8001L)).isEqualTo(1L);
  }

  private static void insertDwdBindingRow(
      JdbcTemplate jdbc, String sql, long projectId, long nodeId, long revisionId, String key) {
    int count = jdbc.update(sql, projectId, 501L, 901L, 3, 9011L,
        "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64),
        nodeId, revisionId, 2, "e".repeat(64), 9001L,
        "warehouse", "public", "dwd_order_detail", "INSERT",
        "f".repeat(64), key, "BINDING_RECORDED_NOT_EXECUTABLE");
    assertThat(count).isEqualTo(1);
  }

}
