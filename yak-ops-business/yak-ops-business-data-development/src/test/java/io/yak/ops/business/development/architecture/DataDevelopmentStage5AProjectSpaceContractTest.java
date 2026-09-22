package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the Stage 5A Project Root / Runtime / Projection ownership boundaries. */
class DataDevelopmentStage5AProjectSpaceContractTest {

  @Test
  void nodeCreateContractDoesNotAcceptProjectOverride() throws IOException {
    String api = source("api/DevelopmentNodeApi.java");
    String controller = source("controller/v1/DevelopmentNodeController.java");

    assertThat(api)
        .contains("record CreateRequest")
        .doesNotContain("Long projectId");
    assertThat(controller)
        .contains("@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)")
        .doesNotContain("request.projectId()")
        .contains("service.create(\n        request.name(),\n        request.type(),\n        request.directoryId())");
  }

  @Test
  void projectRootRepositoriesFailClosedWithoutCurrentProject() throws IOException {
    assertThat(source("repository/DevelopmentDirectoryRepositoryAdapter.java"))
        .contains("currentProject.requireProjectId()")
        .doesNotContain("projectId != null");
    assertThat(source("repository/DevelopmentNodeRepositoryAdapter.java"))
        .contains("currentProject.requireProjectId()")
        .doesNotContain("projectId != null");
  }

  @Test
  void runtimeRecordsPersistProjectAndOnlyDispatcherMayScanAcrossProjects() throws IOException {
    String execution = source("repository/DevelopmentTaskExecutionRepositoryAdapter.java");
    String outbox = source("repository/DevelopmentLineageOutboxRepositoryAdapter.java");
    String worker = source("lineage/DevelopmentLineageWorker.java");

    assertThat(execution)
        .contains("requireOwnerProject(pending.projectId())")
        .contains("listActiveForReconciliation")
        .contains("project_id IS NOT NULL")
        .contains("currentProject.requireProjectId()");
    assertThat(outbox)
        .contains("currentProject.requireProjectId()")
        .contains("WHERE id=? AND project_id=?")
        .contains("INSERT IGNORE INTO yak_dev_lineage_outbox");
    assertThat(worker)
        .contains("new ProjectContext(task.projectId(), null)")
        .contains("node.requireProjectId()")
        .contains("revision.nodeId()");
  }

  @Test
  void taskCatalogProjectionReceivesProjectFromDevelopmentSourceTruth() throws IOException {
    String provider = source("task/DataDevelopmentTaskRevisionProvider.java");
    String publisher = source("task/DevelopmentTaskPublisher.java");

    assertThat(provider)
        .contains("node.orElseThrow().requireProjectId()")
        .contains("sourceProjectId")
        .contains("new TaskSourceRevision(");
    assertThat(publisher)
        .contains("Long sourceProjectId = node.requireProjectId()")
        .contains("sourceProjectId,");
  }

  private String source(String relative) throws IOException {
    return Files.readString(
        moduleRoot()
            .resolve("src/main/java/io/yak/ops/business/development")
            .resolve(relative));
  }

  private Path moduleRoot() {
    Path local = Path.of(".").toAbsolutePath().normalize();
    if (Files.isRegularFile(local.resolve("pom.xml"))
        && local.getFileName() != null
        && "yak-ops-business-data-development".equals(local.getFileName().toString())) {
      return local;
    }
    return Path.of("yak-ops-business", "yak-ops-business-data-development");
  }
}
