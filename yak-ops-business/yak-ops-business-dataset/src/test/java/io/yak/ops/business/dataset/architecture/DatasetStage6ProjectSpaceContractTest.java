package io.yak.ops.business.dataset.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks Dataset Stage 6 Project Root / inherited child / projection boundaries. */
class DatasetStage6ProjectSpaceContractTest {

  @Test
  void datasetHttpPlaneRequiresProject() throws IOException {
    assertThat(source("controller/v1/DatasetController.java"))
        .contains("@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)")
        .doesNotContain("PROJECT_OPTIONAL");
  }

  @Test
  void datasetRootOwnsProjectAndRepositoryFailsClosed() throws IOException {
    assertThat(source("Dataset.java"))
        .contains("Long projectId")
        .contains("requireProjectId()");

    String repository = source("repository/DatasetRepositoryAdapter.java");
    assertThat(repository)
        .contains("currentProject.requireProjectId()")
        .contains("datasetDao.selectVersion(requiredProjectId(), versionId)")
        .contains("datasetDao.selectFields(requiredProjectId(), versionId)")
        .doesNotContain("currentProject.current()");

    assertThat(source("observability/DatasetQueryPerformanceReader.java"))
        .contains("currentProject.requireProjectId()")
        .doesNotContain("orElse(null)");
  }

  @Test
  void versionAndFieldOwnershipIsInheritedThroughDatasetJoin() throws IOException {
    assertThat(source("dao/mapper/DatasetVersionMapper.java"))
        .contains("JOIN yak_dataset d ON d.id = v.dataset_id")
        .contains("d.project_id = #{projectId}");
    assertThat(source("dao/mapper/DatasetFieldMapper.java"))
        .contains("JOIN yak_dataset_version v ON v.id = f.version_id")
        .contains("JOIN yak_dataset d ON d.id = v.dataset_id")
        .contains("d.project_id = #{projectId}");
  }

  @Test
  void taskCatalogAndLineageReceiveTrustedProjectSourceTruth() throws IOException {
    assertThat(source("gateway/taskcatalog/TaskCatalogDatasetAdapter.java"))
        .contains("currentProject.requireProjectId()")
        .contains("resolved.revision().sourceProjectId()");

    assertThat(source("lineage/DatasetLineageRefreshRequested.java"))
        .contains("long projectId")
        .contains("long datasetId");
    assertThat(source("lineage/DatasetLineageRefreshListener.java"))
        .contains("new ProjectContext(event.projectId(), null)")
        .contains("projectScope.run(");
    assertThat(source("gateway/lineage/LineageGraphDatasetAdapter.java"))
        .contains("Long sourceProjectId = currentProject.requireProjectId()")
        .contains("sourceProjectId));");
  }

  private String source(String relative) throws IOException {
    return Files.readString(productionRoot().resolve(relative));
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/dataset");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business",
        "yak-ops-business-dataset",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "business",
        "dataset");
  }
}
