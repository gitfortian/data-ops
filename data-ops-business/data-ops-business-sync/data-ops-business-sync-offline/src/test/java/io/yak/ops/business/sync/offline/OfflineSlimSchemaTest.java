package io.yak.ops.business.sync.offline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OfflineSlimSchemaTest {

  private static final String BASELINE =
      "/db/migration/yak-offline-sync/V1__baseline_offline_sync.sql";
  private static final String NOTIFICATION_POLICY =
      "/db/migration/yak-offline-sync/V2__add_offline_notification_config.sql";
  private static final String AUDIT_CARRIER =
      "/db/migration/yak-offline-sync/V4__add_batch_audit_carrier.sql";

  @Test
  void baselineCreatesOnlyCurrentOfflineSyncTables() throws Exception {
    String sql = read(BASELINE);

    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS yak_offline_job_definition"));
    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS yak_offline_batch_execution"));
    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS yak_offline_job_execution"));
    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS yak_offline_execution_event"));
    assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS yak_offline_sync_cursor"));

    assertFalse(sql.contains("yak_offline_engine_node"));
    assertFalse(sql.contains("yak_offline_job_version"));
    assertFalse(sql.contains("yak_offline_connector_schema"));
    assertFalse(sql.contains("yak_offline_worker_preflight"));
    assertFalse(sql.contains("yak_offline_alert_event"));
  }

  @Test
  void baselineContainsCurrentBatchAttemptProjectAndCursorShape() throws Exception {
    String sql = read(BASELINE);

    assertTrue(sql.contains("project_id BIGINT NOT NULL"));
    assertTrue(sql.contains("batch_id BIGINT NULL"));
    assertTrue(sql.contains("logical_job_spec_json LONGTEXT NULL"));
    assertTrue(sql.contains("yak_offline_sync_cursor"));
    assertTrue(sql.contains("idx_yak_offline_batch_trigger_status"));
    assertTrue(sql.contains("idx_yak_offline_execution_project_created"));
    assertTrue(sql.contains("idx_yak_offline_project_schedule_next"));
  }

  @Test
  void baselineContainsDetailedSourceAndSinkExecutionMetrics() throws Exception {
    String sql = read(BASELINE);

    assertTrue(sql.contains("sink_attempted_record_count"));
    assertTrue(sql.contains("sink_committed_record_count"));
    assertTrue(sql.contains("source_average_qps"));
    assertTrue(sql.contains("sink_average_qps"));
    assertTrue(sql.contains("failed_record_count"));
    assertTrue(sql.contains("skipped_record_count"));
    assertTrue(sql.contains("database_commit_millis"));
    assertTrue(sql.contains("sql_execution_millis"));
  }

  @Test
  void baselineDoesNotReplayHistoricalDataMigrations() throws Exception {
    String sql = read(BASELINE).toUpperCase();

    assertFalse(sql.contains("ALTER TABLE"));
    assertFalse(sql.contains("UPDATE YAK_OFFLINE_"));
    assertFalse(sql.contains("SET STATUS = 'UNKNOWN'"));
    assertFalse(sql.contains("SET LAST_JOB_STATUS = 'UNKNOWN'"));
  }

  @Test
  void notificationPolicyMigrationIsAdditiveAndDoesNotBackfillLegacyTasks() throws Exception {
    String sql = read(NOTIFICATION_POLICY);
    String upper = sql.toUpperCase();

    assertTrue(sql.contains("ALTER TABLE yak_offline_job_definition"));
    assertTrue(sql.contains("notification_config_json TEXT NULL"));
    assertFalse(upper.contains("UPDATE YAK_OFFLINE_JOB_DEFINITION"));
    assertFalse(upper.contains("NOT NULL"));
  }

  @Test
  void auditCarrierMigrationIsAdditiveAndDoesNotRewriteExistingBatches() throws Exception {
    String sql = read(AUDIT_CARRIER);
    String upper = sql.toUpperCase();

    assertTrue(sql.contains("ALTER TABLE yak_offline_batch_execution"));
    assertTrue(sql.contains("audit_carrier_json LONGTEXT NULL"));
    assertFalse(upper.contains("UPDATE YAK_OFFLINE_BATCH_EXECUTION"));
    assertFalse(upper.contains("NOT NULL"));
  }

  private String read(String path) throws Exception {
    try (InputStream input = getClass().getResourceAsStream(path)) {
      assertTrue(input != null);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
