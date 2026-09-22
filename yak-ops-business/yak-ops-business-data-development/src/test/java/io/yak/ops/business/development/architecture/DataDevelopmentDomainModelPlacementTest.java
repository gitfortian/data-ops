package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DataDevelopmentDomainModelPlacementTest {

  private static final Set<String> RELEASE_READ_MODELS = Set.of(
      "DevelopmentReleaseDetail.java",
      "DevelopmentReleasePage.java",
      "DevelopmentReleaseSummary.java");

  private static final Set<String> EXECUTION_READ_MODELS = Set.of(
      "DevelopmentTaskExecutionDetail.java",
      "DevelopmentTaskExecutionPage.java",
      "DevelopmentTaskExecutionSummary.java",
      "DevelopmentTaskRunResult.java");

  @Test
  void applicationReadModelsStayWithOwningSubsystemInsteadOfCoreDomain() {
    Path root = productionRoot();
    Path domain = root.resolve("domain");

    for (String model : RELEASE_READ_MODELS) {
      assertThat(Files.exists(domain.resolve(model))).as("domain/" + model).isFalse();
      assertThat(Files.isRegularFile(root.resolve("release/model").resolve(model)))
          .as("release/model/" + model)
          .isTrue();
    }

    for (String model : EXECUTION_READ_MODELS) {
      assertThat(Files.exists(domain.resolve(model))).as("domain/" + model).isFalse();
      assertThat(Files.isRegularFile(root.resolve("execution/model").resolve(model)))
          .as("execution/model/" + model)
          .isTrue();
    }
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/development");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-development", "src", "main", "java",
        "io", "yak", "ops", "business", "development");
  }
}
