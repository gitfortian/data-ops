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
  void baselineMigrationDeclaresProjectScopeWithoutGuessingDefaultId() throws IOException {
    String migration = section(
        read("src/main/resources/db/migration/yak-quality/V1__quality_baseline.sql"),
        "V1__create_quality_mvp.sql");

    assertThat(migration)
        .contains(
            "CREATE TABLE IF NOT EXISTS yak_quality_table_asset (",
            "CREATE TABLE IF NOT EXISTS yak_quality_monitor (",
            "CREATE TABLE IF NOT EXISTS yak_quality_execution (",
            "project_id BIGINT NOT NULL COMMENT 'Yak Security Project ID'",
            "(project_id, data_source_id, database_name, schema_name, table_name)")
        .doesNotContain("project_id BIGINT NOT NULL DEFAULT");
  }

  @Test
  void projectOwnedQueriesCarryProjectPredicates() throws IOException {
    // 语句正文已从 XML 迁到 Mapper 注解：直接读 Mapper 源码，断言口径不变。
    String qualityQueries =
        read("src/main/java/io/yak/ops/business/quality/dao/mapper/QualityQueryMapper.java");
    String overviewQueries =
        read("src/main/java/io/yak/ops/business/quality/dao/mapper/QualityOverviewMapper.java");

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

  /**
   * 合并后的单文件按 {@code -- Source: <原路径>} 分段;按文件名取回原迁移的正文,
   * 使原先针对单个文件的断言继续有效。
   */
  private static String section(String sql, String sourceFileName) {
    // 合并后的单文件可能是 CRLF：先归一化换行，否则行尾 \r 会让 endsWith 失配。
    String[] lines = sql.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
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
        Path.of("data-ops-business", "data-ops-business-quality")
            .toAbsolutePath()
            .normalize();
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Unable to locate Data Quality module root from %s", local)
        .isTrue();
    return repositoryRelative;
  }
}
