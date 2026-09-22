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
  void firstReleaseNamespaceContainsExactlyOneBaseline() throws IOException {
    List<String> migrations;
    try (var paths = Files.list(migrationRoot())) {
      migrations = paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }

    assertThat(migrations).containsExactly("V1__baseline_data_development.sql");
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
        .contains("project_id BIGINT NULL")
        .contains("updated_by VARCHAR(128) NULL")
        .contains("schema_version INT NOT NULL DEFAULT 1")
        .contains("retry_of_execution_id BIGINT NULL")
        .doesNotContain("ALTER TABLE")
        .doesNotContain("CREATE TABLE IF NOT EXISTS yak_dev_graph")
        .doesNotContain("DROP TABLE");
  }

  private Path configurationSource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/development/config/DataDevelopmentPersistenceConfiguration.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-development", "src", "main", "java", "io", "yak",
        "ops", "business", "development", "config", "DataDevelopmentPersistenceConfiguration.java");
  }

  private Path migrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-data-development");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-development", "src", "main", "resources", "db",
        "migration", "yak-data-development");
  }
}
