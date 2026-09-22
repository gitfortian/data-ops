package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the Stage 5B Data Development x Dataset integration and physical Project contract. */
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
            .resolve("V2__contract_project_scope.sql"));

    assertThat(contract)
        .contains("ALTER TABLE yak_dev_directory")
        .contains("ALTER TABLE yak_dev_node")
        .contains("ALTER TABLE yak_dev_task_execution")
        .contains("ALTER TABLE yak_dev_lineage_outbox")
        .doesNotContain("yak_dev_task_draft")
        .doesNotContain("yak_dev_task_revision");

    assertThat(contract.split("MODIFY COLUMN project_id BIGINT NOT NULL", -1).length - 1)
        .isEqualTo(4);
    assertThat(contract.toUpperCase())
        .doesNotContain("UPDATE YAK_DEV_")
        .doesNotContain("PROJECT_ID = 1")
        .doesNotContain("PROJECT_ID = 0");
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
            .resolve("yak-ops-business/yak-ops-business-dataset/src/main/java/io/yak/ops/business/dataset")
            .resolve(relative));
  }

  private Path repositoryRoot() {
    return moduleRoot().getParent().getParent();
  }

  private Path moduleRoot() {
    Path local = Path.of(".").toAbsolutePath().normalize();
    if (Files.isRegularFile(local.resolve("pom.xml"))
        && local.getFileName() != null
        && "yak-ops-business-data-development".equals(local.getFileName().toString())) {
      return local;
    }
    return Path.of("yak-ops-business", "yak-ops-business-data-development").toAbsolutePath().normalize();
  }
}
