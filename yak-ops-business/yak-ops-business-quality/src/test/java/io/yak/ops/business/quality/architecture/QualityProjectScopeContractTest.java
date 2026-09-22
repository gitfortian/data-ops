package io.yak.ops.business.quality.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level guard for Quality Project Space persistence and background context propagation. */
class QualityProjectScopeContractTest {

  @Test
  void migrationDerivesProjectWithoutGuessingDefaultId() throws IOException {
    String migration = read(
        "src/main/resources/db/migration/yak-quality/V6__add_quality_project_scope.sql");

    assertThat(migration)
        .contains(
            "ADD COLUMN project_id BIGINT NULL",
            "INNER JOIN yak_ops_data_source",
            "MODIFY COLUMN project_id BIGINT NOT NULL",
            "(project_id, data_source_id, database_name, schema_name, table_name)")
        .doesNotContain("project_id = 1", "DEFAULT 1", "VALUES (1");
  }

  @Test
  void projectOwnedQueriesCarryProjectPredicates() throws IOException {
    String qualityQueries =
        read("src/main/resources/mapper/quality/QualityQueryMapper.xml");
    String overviewQueries =
        read("src/main/resources/mapper/quality/QualityOverviewMapper.xml");

    assertThat(qualityQueries)
        .contains(
            "m.project_id = #{projectId}",
            "asset.project_id = #{projectId}",
            "e.project_id = #{projectId}");
    assertThat(overviewQueries)
        .contains(
            "m.project_id = #{projectId}",
            "e.project_id = #{projectId}");
  }

  @Test
  void executionAndScheduleRestorePersistedProject() throws IOException {
    String plan = read(
        "src/main/java/io/yak/ops/business/quality/domain/execution/QualityExecutionPlan.java");
    String dispatcher = read(
        "src/main/java/io/yak/ops/business/quality/execution/QualityExecutionDispatcher.java");
    String schedule = read(
        "src/main/java/io/yak/ops/business/quality/schedule/QualityScheduleEngineBridge.java");
    String handler = read(
        "src/main/java/io/yak/ops/business/quality/schedule/QualityScheduleHandler.java");

    assertThat(plan).contains("long projectId");
    assertThat(dispatcher)
        .contains(
            "new ProjectContext(plan.projectId(), null)",
            "projectScope.run(project");
    assertThat(schedule)
        .contains(
            "payload.put(\"projectId\", projectId)",
            "metadata.put(\"projectId\", String.valueOf(projectId))");
    assertThat(handler)
        .contains(
            "context.requiredLong(\"projectId\")",
            "projectScope.call(");
  }

  private String read(String relative) throws IOException {
    Path module = moduleRoot();
    return Files.readString(module.resolve(relative), StandardCharsets.UTF_8);
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/quality"))) {
      return local;
    }
    Path repositoryRelative =
        Path.of("yak-ops-business", "yak-ops-business-quality")
            .toAbsolutePath()
            .normalize();
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Unable to locate Data Quality module root from %s", local)
        .isTrue();
    return repositoryRelative;
  }
}
