package io.yak.ops.business.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HomeReadModelBoundaryTest {

  @Test
  void dataCenterMustComposeDomainReadersInsteadOfPersistenceInternals() throws IOException {
    String source = readHomeSource("datacenter", "HomeDataCenterReader.java");

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
    String source = readHomeSource("cockpit", "HomeCockpitReader.java");

    assertThat(source)
        .contains(
            "DataSourceReader",
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
  void cockpitReaderMustNotOwnFrontendRoutes() throws IOException {
    String source = readHomeSource("cockpit", "HomeCockpitReader.java");

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

  private String readHomeSource(String rolePackage, String fileName) throws IOException {
    Path relative = Path.of(
        "src/main/java/io/yak/ops/business/home", rolePackage, fileName);
    if (!Files.isRegularFile(relative)) {
      relative = Path.of(
          "data-ops-business",
          "data-ops-business-home",
          "src",
          "main",
          "java",
          "io",
          "yak",
          "ops",
          "business",
          "home",
          rolePackage,
          fileName);
    }
    assertThat(Files.isRegularFile(relative))
        .as(fileName + " source must be available at " + relative)
        .isTrue();
    return Files.readString(relative, StandardCharsets.UTF_8);
  }
}
