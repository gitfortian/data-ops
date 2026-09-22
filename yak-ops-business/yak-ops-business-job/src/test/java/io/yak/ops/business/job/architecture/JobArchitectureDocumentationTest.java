package io.yak.ops.business.job.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobArchitectureDocumentationTest {

  private static final List<String> REQUIRED = List.of(
      "README.md",
      "REQUIREMENTS.md",
      "DOMAIN.md",
      "ARCHITECTURE.md",
      "DEPENDENCIES.md",
      "REVIEW.md");

  @Test
  void architectureDocumentsArePresentAndLinkedFromReadme() throws IOException {
    Path root = moduleRoot();
    String readme = Files.readString(root.resolve("README.md"));
    for (String document : REQUIRED) {
      assertThat(Files.isRegularFile(root.resolve(document))).as(document).isTrue();
      if (!"README.md".equals(document)) {
        assertThat(readme).as("README must link %s", document).contains(document);
      }
    }
  }

  private Path moduleRoot() {
    Path local = Path.of(".");
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/job"))) return local;
    Path repository = Path.of("yak-ops-business", "yak-ops-business-job");
    assertThat(Files.isDirectory(repository)).isTrue();
    return repository;
  }
}
