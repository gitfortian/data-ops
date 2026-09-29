package io.yak.ops.business.analysis.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AnalysisProjectSpaceContractTest {

  @Test
  void persistenceIsFailClosedOnTrustedCurrentProject() throws IOException {
    String dao = read("src/main/java/io/yak/ops/business/analysis/dao/impl/AnalysisDaoImpl.java");
    String po = read("src/main/java/io/yak/ops/business/analysis/dao/model/AnalysisPO.java");

    assertThat(po).contains("private Long projectId;");
    assertThat(dao)
        .contains("CurrentProject")
        .contains("currentProject.requireProjectId()")
        .contains("AnalysisPO::getProjectId")
        .doesNotContain("selectById(analysisId)")
        .doesNotContain("deleteById(analysisId)");
  }

  @Test
  void baselineRequiresProjectOwnershipWithoutFallbackOrBackfill() throws IOException {
    String baseline = read(
        "src/main/resources/db/migration/yak-analysis/V1__baseline_analysis.sql");

    assertThat(baseline)
        .contains("CREATE TABLE IF NOT EXISTS yak_analysis")
        .contains("project_id BIGINT NOT NULL")
        .doesNotContain("project_id = 1")
        .doesNotContain("DEFAULT 0")
        .doesNotContain("JOIN yak_dataset");
  }

  @Test
  void afterCommitProjectionRestoresFrozenProjectContext() throws IOException {
    String event = read(
        "src/main/java/io/yak/ops/business/analysis/definition/AnalysisChangedEvent.java");
    String listener = read(
        "src/main/java/io/yak/ops/business/analysis/lineage/AnalysisLineageRefreshListener.java");

    assertThat(event).contains("long projectId");
    assertThat(listener)
        .contains("ProjectContextScope")
        .contains("new ProjectContext(event.projectId(), null)");
  }

  private String read(String relative) throws IOException {
    return Files.readString(moduleRoot().resolve(relative));
  }

  private Path moduleRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/analysis");
    if (Files.isDirectory(local)) return Path.of(".");
    return Path.of("data-ops-business", "data-ops-business-analysis");
  }
}
