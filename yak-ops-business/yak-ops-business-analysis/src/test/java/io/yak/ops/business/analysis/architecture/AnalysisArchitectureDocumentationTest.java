package io.yak.ops.business.analysis.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Keeps the durable Analysis architecture contract present and discoverable. */
class AnalysisArchitectureDocumentationTest {

  private static final List<String> DOCUMENTS = List.of(
      "README.md",
      "REQUIREMENTS.md",
      "DOMAIN.md",
      "ARCHITECTURE.md",
      "DEPENDENCIES.md",
      "REVIEW.md");

  @Test
  void architectureDocumentsRemainPresent() {
    for (String document : DOCUMENTS) {
      assertThat(Files.isRegularFile(moduleRoot().resolve(document)))
          .as("Analysis architecture document %s must exist", document)
          .isTrue();
    }
  }

  @Test
  void readmeLinksTheDurableContracts() throws IOException {
    String readme = read("README.md");
    for (String document : List.of(
        "REQUIREMENTS.md", "DOMAIN.md", "ARCHITECTURE.md", "DEPENDENCIES.md", "REVIEW.md")) {
      assertThat(readme).as("README must link %s", document).contains(document);
    }
  }

  @Test
  void contractsStateTheCriticalOwnershipRules() throws IOException {
    assertThat(read("REQUIREMENTS.md"))
        .contains("AnalysisQuerySpec", "AnalysisDatasetGateway", "ANALYSIS_BINDING")
        .contains("AnalysisVersion", "ExecutionInstance");
    assertThat(read("DOMAIN.md"))
        .contains("AnalysisDefinition", "Dataset", "Dashboard", "Lineage");
    assertThat(read("DEPENDENCIES.md"))
        .contains("AnalysisDatasetGateway", "AnalysisLineageGraphGateway", "无环");
  }

  private String read(String file) throws IOException {
    return Files.readString(moduleRoot().resolve(file), StandardCharsets.UTF_8);
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/analysis"))) return local;
    Path repositoryRelative = Path.of("yak-ops-business", "yak-ops-business-analysis")
        .toAbsolutePath()
        .normalize();
    assertThat(Files.isDirectory(repositoryRelative)).isTrue();
    return repositoryRelative;
  }
}
