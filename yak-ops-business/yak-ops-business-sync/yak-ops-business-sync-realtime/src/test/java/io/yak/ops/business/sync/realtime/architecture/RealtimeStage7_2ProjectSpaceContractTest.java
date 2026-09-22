package io.yak.ops.business.sync.realtime.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the Realtime Sync Stage 7.2 Project Root / Runtime / inherited boundaries. */
class RealtimeStage7_2ProjectSpaceContractTest {

  @Test
  void realtimeBusinessHttpRequiresProjectWhileComputeEnvironmentIsExplicitlyGlobal()
      throws IOException {
    assertThat(source("controller/v1/RealtimeJobController.java"))
        .contains("@ProjectScope")
        .doesNotContain("PROJECT_OPTIONAL");
    assertThat(source("controller/v1/ComputeEnvironmentController.java"))
        .contains("@ProjectScope(ProjectMigrationMode.LEGACY_GLOBAL)");
  }

  @Test
  void ordinaryDaoFailsClosedAndOnlyDispatcherMayScanAcrossProjects() throws IOException {
    String dao = source("dao/impl/RealtimeJobDaoImpl.java");
    assertThat(dao)
        .contains("currentProject.requireProjectId()")
        .contains("findReconcileCandidatesForDispatch")
        .contains("reconcileExecutionsForDispatch")
        .doesNotContain("currentProject.current().map");

    String mapper = source("dao/mapper/RealtimeJobCommandMapper.java");
    assertThat(mapper)
        .contains("lockDefinitionByProject")
        .contains("reconcileDeploymentByProject")
        .contains("reconcileExecutionsForDispatch")
        .doesNotContain("lockDefinition(@Param")
        .doesNotContain("reconcileDeployment(@Param");
  }

  @Test
  void backgroundReconcileRestoresDurableProjectBeforeBusinessIo() throws IOException {
    assertThat(source("reconcile/RealtimeReconcileCoordinator.java"))
        .contains("dispatchStore.findCandidates()")
        .contains("new ProjectContext(candidate.projectId(), null)")
        .contains("projectScope.run(")
        .contains("store.latestDeployment(candidate.definitionId())");
  }

  @Test
  void immutableVersionsInheritProjectFromDefinition() throws IOException {
    assertThat(source("repository/DefinitionVersionRepositoryAdapter.java"))
        .contains("requireTaskOwned")
        .contains("selectByIdAndProject")
        .contains("currentProject.requireProjectId()");
    assertThat(source("dao/mapper/RealtimeDefinitionVersionMapper.java"))
        .contains("JOIN yak_realtime_job_definition")
        .contains("d.project_id = #{projectId}");
  }

  @Test
  void projectLocalIdempotencyUsesProjectNamespacedExternalRuntimeIdentity() throws IOException {
    assertThat(source("engine/RecoverableRealtimeEngineGateway.java"))
        .contains("currentProject.requireProjectId() + \":\" + idempotencyKey")
        .contains("RealtimeRuntimeIdentity.jobName(runtimeIdentitySeed)")
        .contains("identityStore.bind(request.idempotencyKey(), runtimeJobName)");
  }

  @Test
  void sseSubscribersArePartitionedByProject() throws IOException {
    String stream = source("observability/RealtimeEventStream.java");
    assertThat(stream)
        .contains("currentProject.requireProjectId()")
        .contains("ProjectSubscriber")
        .contains("subscriber.projectId() == projectId");
  }

  @Test
  void firstReleaseSchemaContractsOnlyRootAndRuntimeProjectColumns() throws IOException {
    String sql = migration();
    assertThat(sql)
        .contains("yak_realtime_job_definition")
        .contains("project_id BIGINT NOT NULL")
        .contains("UNIQUE KEY uk_realtime_project_name (project_id, job_name)")
        .contains("yak_realtime_job_deployment")
        .contains("UNIQUE KEY uk_realtime_project_idempotency (project_id, idempotency_key)")
        .contains("UNIQUE KEY uk_realtime_runtime_job_name (runtime_job_name)");

    String versionSection = section(
        sql,
        "CREATE TABLE IF NOT EXISTS yak_realtime_definition_version",
        "CREATE TABLE IF NOT EXISTS yak_realtime_job_deployment");
    String eventSection = section(
        sql,
        "CREATE TABLE IF NOT EXISTS yak_realtime_job_event",
        "CREATE TABLE IF NOT EXISTS yak_realtime_runtime_lease");
    String environmentSection = section(
        sql,
        "CREATE TABLE IF NOT EXISTS yak_compute_environment",
        "CREATE TABLE IF NOT EXISTS yak_realtime_job_definition");
    assertThat(versionSection).doesNotContain("project_id");
    assertThat(eventSection).doesNotContain("project_id");
    assertThat(environmentSection).doesNotContain("project_id");
  }

  @Test
  void dataSourceDaoNoLongerHasNoProjectFallback() throws IOException {
    String dao = Files.readString(repoRoot().resolve(
        Path.of(
            "yak-ops-business", "yak-ops-business-datasource", "src", "main", "java",
            "io", "yak", "ops", "business", "datasource", "dao", "impl",
            "DataSourceDaoImpl.java")));
    assertThat(dao)
        .contains("currentProject.requireProjectId()")
        .doesNotContain("if (projectId == null) return dataSourceMapper.selectById(id)")
        .doesNotContain(".eq(projectId != null, DataSourcePO::getProjectId, projectId)");
  }

  private String source(String relative) throws IOException {
    return Files.readString(productionRoot().resolve(relative));
  }

  private String migration() throws IOException {
    Path local = Path.of("src/main/resources/db/migration/yak-realtime-sync/V1__baseline_realtime_sync.sql");
    if (Files.isRegularFile(local)) return Files.readString(local);
    return Files.readString(repoRoot().resolve(
        Path.of(
            "yak-ops-business", "yak-ops-business-sync", "yak-ops-business-sync-realtime",
            "src", "main", "resources", "db", "migration", "yak-realtime-sync",
            "V1__baseline_realtime_sync.sql")));
  }

  private String section(String value, String start, String end) {
    int startIndex = value.indexOf(start);
    int endIndex = value.indexOf(end, startIndex + start.length());
    return value.substring(startIndex, endIndex);
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/sync/realtime");
    if (Files.isDirectory(local)) return local;
    return repoRoot().resolve(Path.of(
        "yak-ops-business", "yak-ops-business-sync", "yak-ops-business-sync-realtime",
        "src", "main", "java", "io", "yak", "ops", "business", "sync", "realtime"));
  }

  private Path repoRoot() {
    Path current = Path.of("").toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve("yak-ops-business"))) return current;
      current = current.getParent();
    }
    throw new IllegalStateException("Cannot locate yak-ops repository root");
  }
}
