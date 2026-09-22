package io.yak.ops.business.dashboard.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardArchitectureDocumentationTest {

  private static final List<String> CONTRACTS = List.of(
      "README.md",
      "REQUIREMENTS.md",
      "DOMAIN.md",
      "ARCHITECTURE.md",
      "DEPENDENCIES.md",
      "REVIEW.md");

  @Test
  void architectureContractsExistAndAreLinkedFromReadme() throws IOException {
    Path module = moduleRoot();
    String readme = Files.readString(module.resolve("README.md"));

    for (String contract : CONTRACTS) {
      assertThat(module.resolve(contract)).exists();
      if (!contract.equals("README.md")) {
        assertThat(readme).contains(contract);
      }
    }
    assertThat(readme).contains("CODE_STYLE.md");
  }

  @Test
  void domainContractLocksVersionAndOwnershipSemantics() throws IOException {
    String domain = Files.readString(moduleRoot().resolve("DOMAIN.md"));

    assertThat(domain)
        .contains("currentVersionId")
        .contains("publishedVersionId")
        .containsIgnoringCase("append-only")
        .contains("Restore is copy-forward")
        .contains("Analysis owns reusable analytical definitions")
        .contains("Lineage owns graph truth")
        .contains("ProjectContextScope")
        .contains("version allocation concurrency");
  }

  @Test
  void dependencyContractNamesAllCrossModuleGatewaysAndProjectCorridors() throws IOException {
    String dependencies = Files.readString(moduleRoot().resolve("DEPENDENCIES.md"));

    assertThat(dependencies)
        .contains("DashboardAnalysisGateway")
        .contains("AnalysisDashboardAdapter")
        .contains("DashboardDatasetGateway")
        .contains("DatasetDashboardAdapter")
        .contains("DashboardLineageGraphGateway")
        .contains("LineageDashboardAdapter")
        .contains("DashboardReferenceRepository")
        .contains("CurrentProject")
        .contains("ProjectContextScope")
        .contains("acyclic");
  }

  private Path moduleRoot() {
    Path local = Path.of("README.md");
    if (Files.isRegularFile(local)) {
      return Path.of(".");
    }
    return Path.of("yak-ops-business", "yak-ops-business-dashboard");
  }
}
