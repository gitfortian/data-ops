package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the Data Development x Dataset integration and physical Project contract. */
class DataDevelopmentStage5BProjectSpaceContractTest {

  @Test
  void datasetIntegrationValidatesBothProjectSourceTruths() throws IOException {
    String service = developmentSource("dataset/DevelopmentDatasetNodeService.java");
    String facade = datasetSource("DevelopmentDatasetFacade.java");

    assertThat(service)
        .contains("node.requireProjectId()")
        .contains("requireSameProject(datasetNode, candidate)")
        .contains("dataset.projectId()")
        .contains("Dataset 与数据开发节点 Project 不一致");

    assertThat(facade)
        .contains("dataset.requireProjectId()")
        .contains("long projectId,");
  }

  @Test
  void contractMakesOnlyProjectRootsAndRuntimeRowsPhysicallyRequired() throws IOException {
    String contract = Files.readString(
        moduleRoot()
            .resolve("src/main/resources/db/migration/yak-data-development")
            .resolve("V1__baseline_data_development.sql"));

    assertThat(tableDefinition(contract, "yak_dev_directory"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(tableDefinition(contract, "yak_dev_node"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(tableDefinition(contract, "yak_dev_task_execution"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(tableDefinition(contract, "yak_dev_lineage_outbox"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(tableDefinition(contract, "yak_dev_task_draft")).doesNotContain("project_id");
    assertThat(tableDefinition(contract, "yak_dev_task_revision")).doesNotContain("project_id");
    assertThat(contract.toUpperCase())
        .doesNotContain("UPDATE YAK_DEV_")
        .doesNotContain("PROJECT_ID = 1")
        .doesNotContain("PROJECT_ID = 0");
  }

  private String tableDefinition(String migration, String table) {
    String start = "CREATE TABLE IF NOT EXISTS " + table + " (";
    int definitionStart = migration.indexOf(start);
    if (definitionStart < 0) throw new AssertionError("Missing baseline table: " + table);
    int definitionEnd = migration.indexOf(") ENGINE=", definitionStart);
    if (definitionEnd < 0) throw new AssertionError("Unterminated baseline table: " + table);
    return migration.substring(definitionStart, definitionEnd);
  }

  private String developmentSource(String relative) throws IOException {
    return Files.readString(
        moduleRoot()
            .resolve("src/main/java/io/yak/ops/business/development")
            .resolve(relative));
  }

  private String datasetSource(String relative) throws IOException {
    return Files.readString(
        repositoryRoot()
            .resolve("data-ops-business/data-ops-business-dataset/src/main/java/io/yak/ops/business/dataset")
            .resolve(relative));
  }

  private Path repositoryRoot() {
    return moduleRoot().getParent().getParent();
  }

  private Path moduleRoot() {
    Path local = Path.of(".").toAbsolutePath().normalize();
    if (Files.isRegularFile(local.resolve("pom.xml"))
        && local.getFileName() != null
        && "data-ops-business-data-development".equals(local.getFileName().toString())) {
      return local;
    }
    return Path.of("data-ops-business", "data-ops-business-data-development").toAbsolutePath().normalize();
  }
}
