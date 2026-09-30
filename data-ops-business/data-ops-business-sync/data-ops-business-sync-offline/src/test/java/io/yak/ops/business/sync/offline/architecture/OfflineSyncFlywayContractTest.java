package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks Offline Sync migration ownership, baseline shape and additive post-baseline changes. */
class OfflineSyncFlywayContractTest {

  @Test
  void offlineSyncOwnsOrderedMigrationSequence() throws IOException {
    List<String> migrations = sqlFiles(migrationRoot());
    assertThat(migrations.stream().filter(name -> name.startsWith("V")).toList())
        .containsExactly(
            "V1__baseline_offline_sync.sql",
            "V2__add_offline_notification_config.sql",
            "V3__add_offline_editor_meta.sql",
            "V4__add_batch_audit_carrier.sql",
            "V5__bind_offline_cursor_source_route.sql",
            "V6__offline_job_revision_versioning.sql");
    assertThat(migrations).contains("B6__offline_sync_baseline.sql");

    String baseline = Files.readString(migrationRoot().resolve("V1__baseline_offline_sync.sql"));
    assertThat(baseline)
        .contains("CREATE TABLE IF NOT EXISTS yak_offline_job_definition")
        .contains("CREATE TABLE IF NOT EXISTS yak_offline_batch_execution")
        .contains("CREATE TABLE IF NOT EXISTS yak_offline_job_execution")
        .contains("CREATE TABLE IF NOT EXISTS yak_offline_execution_event")
        .contains("CREATE TABLE IF NOT EXISTS yak_offline_sync_cursor")
        .doesNotContain("ALTER TABLE")
        .doesNotContain("audit_carrier_json");
  }

  @Test
  void auditCarrierIsAnAdditiveNullableBatchCorrelationField() throws IOException {
    String migration =
        Files.readString(migrationRoot().resolve("V4__add_batch_audit_carrier.sql"));

    assertThat(migration)
        .contains("ALTER TABLE yak_offline_batch_execution")
        .contains("audit_carrier_json LONGTEXT NULL")
        .doesNotContain("UPDATE yak_offline_batch_execution")
        .doesNotContain("NOT NULL");
  }

  @Test
  void onlyProjectRootAndRuntimeFactsPersistProjectDirectly() throws IOException {
    String sql = Files.readString(migrationRoot().resolve("V1__baseline_offline_sync.sql"));

    assertThat(table(sql, "yak_offline_job_definition"))
        .contains("project_id BIGINT NOT NULL")
        .contains("UNIQUE KEY uk_yak_offline_project_job_name (project_id, job_name)");
    assertThat(table(sql, "yak_offline_batch_execution"))
        .contains("project_id BIGINT NOT NULL")
        .contains("idx_yak_offline_batch_project_status");
    assertThat(table(sql, "yak_offline_job_execution"))
        .contains("project_id BIGINT NOT NULL")
        .contains("idx_yak_offline_execution_project_status");

    assertThat(table(sql, "yak_offline_execution_event"))
        .doesNotContain("project_id");
    assertThat(table(sql, "yak_offline_sync_cursor"))
        .doesNotContain("project_id");
  }

  private String table(String sql, String table) {
    String marker = "CREATE TABLE IF NOT EXISTS " + table;
    int start = sql.indexOf(marker);
    if (start < 0) throw new IllegalStateException("missing table: " + table);
    int end = sql.indexOf(";", start);
    if (end < 0) throw new IllegalStateException("unterminated table: " + table);
    return sql.substring(start, end + 1);
  }

  private List<String> sqlFiles(Path root) throws IOException {
    try (var paths = Files.list(root)) {
      return paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }
  }

  private Path migrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-offline-sync");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "data-ops-business",
        "data-ops-business-sync",
        "data-ops-business-sync-offline",
        "src",
        "main",
        "resources",
        "db",
        "migration",
        "yak-offline-sync");
  }
}
