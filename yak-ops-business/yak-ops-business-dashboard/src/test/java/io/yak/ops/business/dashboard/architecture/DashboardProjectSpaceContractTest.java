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
  void migrationRequiresCompleteSingleProjectEvidenceThenContracts() throws IOException {
    String expand = read(
        "src/main/resources/db/migration/yak-dashboard/V2__expand_and_backfill_project_scope.sql");
    String contract = read(
        "src/main/resources/db/migration/yak-dashboard/V3__contract_project_scope.sql");

    assertThat(expand)
        .contains("ADD COLUMN project_id BIGINT NULL")
        .contains("tmp_yak_dashboard_project_evidence")
        .contains("COUNT(*) = COUNT(project_id)")
        .contains("MIN(project_id) = MAX(project_id)")
        .doesNotContain("project_id = 1")
        .doesNotContain("DEFAULT 0");
    assertThat(contract)
        .contains("project_id BIGINT NOT NULL")
        .doesNotContainIgnoringCase("UPDATE");
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
    return Path.of("yak-ops-business", "yak-ops-business-dashboard");
  }
}
