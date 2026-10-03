package io.yak.ops.business.dataset.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks the first-release Dataset baseline and its additive contract migrations. */
class DatasetFlywayContractTest {

    @Test
    void datasetNamespaceContainsTheBaselineAndAdditiveContractMigrations() throws IOException {
        List<String> migrations = sqlFiles(migrationRoot());
        assertThat(migrations).containsExactly("V1__dataset_baseline.sql");
    }

    @Test
    void baselineShouldContainFinalProjectSchemaAndDiagnosticsTable() throws IOException {
        String baseline = section(
            Files.readString(migrationRoot().resolve("V1__dataset_baseline.sql")),
            "V1__baseline_dataset.sql");

        assertThat(baseline)
                .contains("CREATE TABLE IF NOT EXISTS yak_dataset")
                .contains("project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID'")
                .contains("idx_yak_dataset_update_id")
                .contains("idx_yak_dataset_create_time")
                .contains("idx_yak_dataset_project_status_update")
                .contains("idx_yak_dataset_project_development_node")
                .contains("CREATE TABLE IF NOT EXISTS yak_dataset_query_performance")
                .contains("idx_yak_dataset_query_performance_project_time")
                .doesNotContain("ALTER TABLE")
                .doesNotContain("UPDATE yak_dataset")
                .doesNotContain("project_id = 1")
                .doesNotContain("project_id = 0");
    }

  /**
   * 合并后的单文件按 {@code -- Source: <原路径>} 分段;按文件名取回原迁移的正文,
   * 使原先针对单个文件的断言继续有效。
   */
  private static String section(String sql, String sourceFileName) {
    String[] lines = sql.split("\n");
    int start = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:") && lines[i].trim().endsWith(sourceFileName)) {
        start = i + 1;
        break;
      }
    }
    if (start < 0) throw new IllegalStateException("missing source section: " + sourceFileName);
    StringBuilder body = new StringBuilder();
    for (int i = start; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:")) break;
      body.append(lines[i]).append('\n');
    }
    return body.toString();
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
        Path local = Path.of("src/main/resources/db/migration/yak-dataset");
        if (Files.isDirectory(local)) {
            return local;
        }
        return Path.of(
                "data-ops-business",
                "data-ops-business-dataset",
                "src",
                "main",
                "resources",
                "db",
                "migration",
                "yak-dataset");
    }
}
