package io.yak.ops.business.dashboard.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardProjectSpaceContractTest {

  @Test
  void rootCrudOverviewAndReferenceChecksAreProjectScoped() throws IOException {
    String dao = read("src/main/java/io/yak/ops/business/dashboard/dao/impl/DashboardDaoImpl.java");
    String mapper = read("src/main/java/io/yak/ops/business/dashboard/dao/mapper/DashboardMapper.java");
    String overview = read(
        "src/main/java/io/yak/ops/business/dashboard/dao/mapper/DashboardOverviewMapper.java");
    String po = read("src/main/java/io/yak/ops/business/dashboard/dao/model/DashboardPO.java");

    assertThat(po).contains("private Long projectId;");
    assertThat(dao)
        .contains("CurrentProject")
        .contains("currentProject.requireProjectId()")
        .contains("DashboardPO::getProjectId")
        .doesNotContain("dashboardMapper.selectById(dashboardId)")
        .doesNotContain("dashboardMapper.deleteById(dashboardId)");
    assertThat(mapper).contains("d.project_id = #{projectId}");
    assertThat(overview).contains("WHERE project_id = #{projectId}");
  }

  @Test
  void datasetAndAnalysisReferencesAreProvedInsideCurrentProject() throws IOException {
    String composition = read(
        "src/main/java/io/yak/ops/business/dashboard/composition/DashboardCompositionNormalizer.java");
    String widgets = read(
        "src/main/java/io/yak/ops/business/dashboard/composition/DashboardWidgetPolicy.java");

    assertThat(composition).contains("datasets.requireExists(activeDatasetId)");
    assertThat(widgets)
        .contains("analyses.requireExists(value.analysisId())")
        .contains("datasets.requireExists(datasetId)");
  }

  @Test
  void baselineRequiresProjectOwnershipOnlyOnTheDashboardRoot() throws IOException {
    String baseline = read(
        "src/main/resources/db/migration/yak-dashboard/V1__baseline_dashboard.sql");

    assertThat(tableDefinition(baseline, "yak_dashboard"))
        .contains("project_id BIGINT NOT NULL")
        .doesNotContain("project_id BIGINT NOT NULL DEFAULT");
    for (String childTable : new String[] {
      "yak_dashboard_version",
      "yak_dashboard_widget",
      "yak_dashboard_filter",
      "yak_dashboard_filter_binding",
      "yak_dashboard_interaction"
    }) {
      assertThat(tableDefinition(baseline, childTable)).doesNotContain("project_id");
    }
  }

  private String tableDefinition(String migration, String table) {
    String start = "CREATE TABLE IF NOT EXISTS " + table + " (";
    int definitionStart = migration.indexOf(start);
    if (definitionStart < 0) throw new AssertionError("Missing baseline table: " + table);
    int definitionEnd = migration.indexOf(") ENGINE=", definitionStart);
    if (definitionEnd < 0) throw new AssertionError("Unterminated baseline table: " + table);
    return migration.substring(definitionStart, definitionEnd);
  }

  @Test
  void afterCommitLineageRestoresFrozenProjectContext() throws IOException {
    String event = read(
        "src/main/java/io/yak/ops/business/dashboard/change/DashboardChangedEvent.java");
    String listener = read(
        "src/main/java/io/yak/ops/business/dashboard/lineage/DashboardLineageRefreshListener.java");

    assertThat(event).contains("long projectId");
    assertThat(listener)
        .contains("ProjectContextScope")
        .contains("new ProjectContext(event.projectId(), null)");
  }

  private String read(String relative) throws IOException {
    return Files.readString(moduleRoot().resolve(relative));
  }

  private Path moduleRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/dashboard");
    if (Files.isDirectory(local)) return Path.of(".");
    return Path.of("data-ops-business", "data-ops-business-dashboard");
  }
}
