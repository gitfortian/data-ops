package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HomeReadModelBoundaryTest {

  @Test
  void dataCenterMustComposeDomainReadersInsteadOfPersistenceInternals() throws IOException {
    String source = readHomeSource("HomeDataCenterService.java");

    assertThat(source)
        .contains(
            "OfflineExecutionOverviewReader",
            "WorkflowExecutionOverviewReader",
            "QualityExecutionOverviewReader")
        .doesNotContain(
            ".dao.mapper.",
            ".bean.po.",
            "LambdaQueryWrapper",
            "JdbcTemplate");
  }

  @Test
  void cockpitMustComposeStableDomainReadSidesInsteadOfPersistenceInternals() throws IOException {
    String source = readHomeSource("HomeCockpitService.java");

    assertThat(source)
        .contains(
            "DataSourceReader",
            "OfflineExecutionOverviewReader",
            "DevelopmentNodeService",
            "WorkflowExecutionOverviewReader",
            "QualityOverviewReader",
            "QualityExecutionOverviewReader",
            "DataServiceReader",
            "DashboardService",
            "DigitalScreenApplicationService")
        .doesNotContain(
            ".dao.mapper.",
            ".bean.po.",
            "LambdaQueryWrapper",
            "JdbcTemplate");
  }

  @Test
  void cockpitBackendMustNotOwnFrontendRoutes() throws IOException {
    String source = readHomeSource("HomeCockpitService.java");

    assertThat(source)
        .doesNotContain(
            "\"/data-source\"",
            "\"/sync/",
            "\"/data-development",
            "\"/workflow/",
            "\"/data-quality/",
            "\"/data-analysis/",
            "\"/data-service/",
            "\"/dashboard\"");
  }

  private String readHomeSource(String fileName) throws IOException {
    Path local = Path.of("src/main/java/io/yak/ops/boot/home", fileName);
    if (Files.isRegularFile(local)) {
      return Files.readString(local, StandardCharsets.UTF_8);
    }

    Path repositoryRelative = Path.of(
        "yak-ops-boot",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "boot",
        "home",
        fileName);
    assertThat(Files.isRegularFile(repositoryRelative))
        .as(fileName + " source must be available")
        .isTrue();
    return Files.readString(repositoryRelative, StandardCharsets.UTF_8);
  }
}
