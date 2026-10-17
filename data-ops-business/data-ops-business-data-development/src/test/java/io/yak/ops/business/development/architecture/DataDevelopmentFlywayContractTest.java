package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataDevelopmentFlywayContractTest {

  @Test
  void dataDevelopmentOwnsDedicatedFlywayNamespace() throws IOException {
    String configuration = Files.readString(configurationSource());

    assertThat(configuration)
        .contains("classpath:db/migration/yak-data-development")
        .contains("yak_data_development_schema_history")
        .contains("MigrationVersion.fromVersion(\"0\")");
  }

  @Test
  void preservesAppliedV1V2AndAddsForwardV3RuntimeAttemptMigration() throws IOException {
    List<String> migrations;
    try (var paths = Files.list(migrationRoot())) {
      migrations = paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }

    assertThat(migrations).containsExactly(
        "V1__baseline_data_development.sql",
        "V2__dwd_published_task_binding.sql",
        "V3__dwd_frozen_execution_attempt.sql");
    assertThat(Files.exists(
        postgresqlMigrationRoot().resolve("V2__dwd_published_task_binding.sql"))).isTrue();
  }

  @Test
  void baselineCreatesCurrentSchemaDirectlyWithoutHistoricalDeltas() throws IOException {
    String migration = Files.readString(migrationRoot().resolve("V1__baseline_data_development.sql"));

    assertThat(migration)
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_directory")
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_node")
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_task_draft")
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_task_revision")
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_editor_setting")
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_task_execution")
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_data_service_draft")
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_data_service_revision")
        .contains("CREATE TABLE IF NOT EXISTS yak_dev_lineage_outbox")
        .contains("CREATE TABLE IF NOT EXISTS yak_system_env_var")
        .contains("project_id BIGINT NOT NULL")
        .contains("updated_by VARCHAR(128) NULL")
        .contains("schema_version INT NOT NULL DEFAULT 1")
        .contains("retry_of_execution_id BIGINT NULL")
        .doesNotContain("ALTER TABLE")
        .doesNotContain("CREATE TABLE IF NOT EXISTS yak_dev_graph")
        .doesNotContain("DROP TABLE");
  }

  @Test
  void immutableDwdBindingIsForwardOnlyAndProjectIsolatedForBothVendors()
      throws IOException {
    for (Path root : List.of(migrationRoot(), postgresqlMigrationRoot())) {
      String v2 = Files.readString(root.resolve("V2__dwd_published_task_binding.sql"));
      assertThat(v2)
          .contains("CREATE TABLE yak_dev_model_task_binding")
          .contains("project_id")
          .contains("model_version_id")
          .contains("mapping_revision_id")
          .contains("task_revision_id")
          .contains("task_definition_sha256")
          .contains("target_datasource_id")
          .contains("idempotency_key")
          .contains("uk_yak_dev_binding_project_request")
          .contains("uk_yak_dev_binding_project_revision")
          .contains("idx_yak_dev_binding_model")
          .doesNotContain("ALTER TABLE yak_dev_task_revision")
          .doesNotContain("DROP TABLE")
          .doesNotContain("INSERT INTO yak_dev_model_task_binding");
    }
  }

  @Test
  void dwdRuntimeAttemptV3IsProjectScopedAppendOnlyAndContainsUniqueRequestKey()
      throws IOException {
    for (Path root : List.of(migrationRoot(), postgresqlMigrationRoot())) {
      String v3 = Files.readString(root.resolve("V3__dwd_frozen_execution_attempt.sql"));
      assertThat(v3)
          .contains("CREATE TABLE yak_dev_dwd_execution_attempt")
          .contains("project_id")
          .contains("binding_id")
          .contains("binding_digest")
          .contains("request_key")
          .contains("attempt_digest")
          .contains("runtime_execution_id")
          .contains("uk_dwd_attempt_project_key")
          .contains("idx_dwd_attempt_binding")
          .doesNotContain("DROP TABLE")
          .doesNotContain("ALTER TABLE yak_dev_model_task_binding");
    }
  }

  private Path postgresqlMigrationRoot() {
    Path local = Path.of("src/main/resources/db/migration-postgresql/yak-data-development");
    if (Files.isDirectory(local)) return local;
    return Path.of("data-ops-business", "data-ops-business-data-development",
        "src", "main", "resources", "db", "migration-postgresql", "yak-data-development");
  }

  private Path configurationSource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/development/config/DataDevelopmentPersistenceConfiguration.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "data-ops-business", "data-ops-business-data-development", "src", "main", "java", "io", "yak",
        "ops", "business", "development", "config", "DataDevelopmentPersistenceConfiguration.java");
  }

  private Path migrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-data-development");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "data-ops-business", "data-ops-business-data-development", "src", "main", "resources", "db",
        "migration", "yak-data-development");
  }
}
