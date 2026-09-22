package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the Offline Sync Stage 7.1 Project Root / Runtime / inherited boundaries. */
class OfflineStage7_1ProjectSpaceContractTest {

  @Test
  void offlineBusinessHttpPlaneRequiresProjectWhileEngineHealthRemainsGlobal()
      throws IOException {
    assertThat(source("controller/OfflineJobDefinitionController.java"))
        .contains("@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)")
        .doesNotContain("PROJECT_OPTIONAL");
    assertThat(source("controller/OfflineBackfillController.java"))
        .contains("@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)");
    assertThat(source("controller/OfflineControlPlaneController.java"))
        .contains("@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)");

    String executionController = source("controller/OfflineJobExecutionController.java");
    assertThat(executionController)
        .contains("@ProjectScope(ProjectMigrationMode.PROJECT_REQUIRED)")
        .contains("@ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)")
        .contains("/api/v1/job/batch-execution/health")
        .contains("/api/v1/executor/health");
  }

  @Test
  void definitionBatchAndExecutionUseTrustedCurrentProject() throws IOException {
    assertThat(source("domain/OfflineJobDefinition.java"))
        .contains("Long projectId")
        .contains("requireProjectId()");

    assertThat(source("repository/OfflineJobDefinitionRepositoryAdapter.java"))
        .contains("currentProject.requireProjectId()")
        .contains("bindCurrentProject(definition)")
        .contains("findScheduledForReconciliation()");
    assertThat(source("repository/OfflineBatchExecutionRepositoryAdapter.java"))
        .contains("currentProject.requireProjectId()")
        .contains("findPendingBackfillsForDispatch")
        .contains("ProjectBatchRef");
    assertThat(source("repository/OfflineJobExecutionRepositoryAdapter.java"))
        .contains("currentProject.requireProjectId()")
        .contains("findActiveExecutionsForReconciliation")
        .contains("findRetryCandidatesForReconciliation")
        .contains("ProjectExecutionRef");

    assertThat(source("dao/impl/OfflineJobDefinitionDaoImpl.java"))
        .contains("currentProject.requireProjectId()")
        .contains("selectWithCronForReconciliation()")
        .doesNotContain("currentProject.current()");
    assertThat(source("dao/impl/OfflineBatchExecutionDaoImpl.java"))
        .contains("currentProject.requireProjectId()")
        .contains("selectPendingBackfillsForDispatch")
        .doesNotContain("currentProject.current()");
    assertThat(source("dao/impl/OfflineJobExecutionDaoImpl.java"))
        .contains("currentProject.requireProjectId()")
        .contains("selectActiveExecutionsForReconciliation")
        .contains("selectRetryCandidatesForReconciliation")
        .doesNotContain("currentProject.current()");
  }

  @Test
  void backgroundDispatchersRestoreDurableProjectBeforeBusinessIo() throws IOException {
    assertThat(source("backfill/OfflineBackfillDispatcher.java"))
        .contains("findPendingBackfillsForDispatch")
        .contains("new ProjectContext(candidate.projectId(), null)")
        .contains("projectScope.run(")
        .contains("batchRepository.findById(candidate.batchId())");

    assertThat(source("reconcile/OfflineExecutionReconciler.java"))
        .contains("findActiveExecutionsForReconciliation")
        .contains("findRetryCandidatesForReconciliation")
        .contains("new ProjectContext(candidate.projectId(), null)")
        .contains("executionRepository.findById(candidate.executionId())");
  }

  @Test
  void schedulePersistsAndRestoresProjectIdentity() throws IOException {
    assertThat(source("schedule/OfflineScheduleEngineBridge.java"))
        .contains("payload.put(\"projectId\", projectId)")
        .contains("metadata.put(\"projectId\", String.valueOf(projectId))")
        .contains("definition.requireProjectId()");
    assertThat(source("schedule/OfflineScheduleHandler.java"))
        .contains("context.requiredLong(\"projectId\")")
        .contains("new ProjectContext(projectId, null)")
        .contains("projectScope.call(");
    assertThat(source("schedule/OfflineScheduleReconciler.java"))
        .contains("findScheduledForReconciliation()")
        .contains("new ProjectContext(candidate.projectId(), null)")
        .contains("projectScope.call(");
  }

  @Test
  void inheritedEventAndCursorMustResolveTheirOwningProjectRoot() throws IOException {
    assertThat(source("repository/OfflineExecutionEventRepositoryAdapter.java"))
        .contains("executionRepository.findById(executionId)")
        .contains("Project 从所属 Execution 继承");
    assertThat(source("repository/OfflineSyncCursorRepositoryAdapter.java"))
        .contains("definitionRepository.findById(taskId)")
        .contains("Project 从所属 Definition 继承");
  }

  @Test
  void globalJobRegistryCannotEnumerateOfflineTasksWithoutProject() throws IOException {
    assertThat(source("definition/OfflineSyncTaskProvider.java"))
        .contains("if (!currentProject.isPresent()) return List.of();")
        .contains("currentProject.requireProjectId()")
        .contains("definition.requireProjectId()");
  }

  @Test
  void overviewProjectionAlwaysRequiresProject() throws IOException {
    assertThat(source("repository/OfflineExecutionOverviewRepositoryAdapter.java"))
        .contains("currentProject.requireProjectId()")
        .doesNotContain("currentProject.current().map");
  }

  private String source(String relative) throws IOException {
    return Files.readString(productionRoot().resolve(relative));
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/sync/offline");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business",
        "yak-ops-business-sync",
        "yak-ops-business-sync-offline",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "business",
        "sync",
        "offline");
  }
}
