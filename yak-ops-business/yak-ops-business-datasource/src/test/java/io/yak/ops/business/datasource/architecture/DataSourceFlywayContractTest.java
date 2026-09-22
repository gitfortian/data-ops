package io.yak.ops.business.datasource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataSourceFlywayContractTest {

  @Test
  void datasourceOwnsDedicatedFlywayNamespace() throws IOException {
    String configuration = Files.readString(configurationSource());

    assertThat(configuration)
        .contains("classpath:db/migration/yak-datasource")
        .contains("yak_datasource_schema_history")
        .contains("MigrationVersion.fromVersion(\"0\")");
  }

  /**
   * The project-scope expand/contract migrations were folded into the V1
   * baseline (single-file fold convention); the namespace therefore owns
   * exactly one migration whose tables already carry project_id.
   */
  @Test
  void datasourceNamespaceContainsExpandAndContractProjectScopeMigrations() throws IOException {
    assertThat(sqlFiles(migrationRoot()))
        .containsExactly("V1__baseline_datasource.sql");

    String baseline = Files.readString(migrationRoot().resolve("V1__baseline_datasource.sql"));
    assertThat(baseline)
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_data_source")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_sql_execution")
        .contains("CREATE TABLE IF NOT EXISTS yak_ops_sql_statement_execution")
        .contains("project_id BIGINT NOT NULL")
        .doesNotContain("ALTER TABLE")
        .doesNotContain("UPDATE yak_ops_");
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

  private Path configurationSource() {
    Path local = Path.of(
        "src/main/java/io/yak/ops/business/datasource/config/DataSourceConfiguration.java");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-datasource", "src", "main", "java", "io", "yak",
        "ops", "business", "datasource", "config", "DataSourceConfiguration.java");
  }

  private Path migrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-datasource");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-datasource", "src", "main", "resources", "db",
        "migration", "yak-datasource");
  }
}
