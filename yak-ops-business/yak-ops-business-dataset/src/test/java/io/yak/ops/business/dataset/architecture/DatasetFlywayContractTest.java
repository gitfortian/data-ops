package io.yak.ops.business.dataset.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Locks the consolidated first-release Dataset Flyway baseline. */
class DatasetFlywayContractTest {

    @Test
    void datasetNamespaceShouldContainOnlyTheConsolidatedBaseline() throws IOException {
        assertThat(sqlFiles(migrationRoot())).containsExactly("V1__baseline_dataset.sql");
    }

    @Test
    void baselineShouldContainFinalProjectSchemaAndDiagnosticsTable() throws IOException {
        String baseline = Files.readString(migrationRoot().resolve("V1__baseline_dataset.sql"));

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
                "yak-ops-business",
                "yak-ops-business-dataset",
                "src",
                "main",
                "resources",
                "db",
                "migration",
                "yak-dataset");
    }
}
