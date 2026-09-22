package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataDevelopmentArchitectureDocumentationTest {

  private static final List<String> DOCUMENTS = List.of(
      "README.md",
      "REQUIREMENTS.md",
      "DOMAIN.md",
      "ARCHITECTURE.md",
      "DEPENDENCIES.md",
      "REVIEW.md",
      "MIGRATIONS.md",
      "EXECUTION_CONTROL_PLANE.md",
      "PROJECT_GOVERNANCE.md",
      "ENGINEERING_HARDENING.md");

  @Test
  void architectureContractDocumentsExistAndReadmeLinksThem() throws IOException {
    Path root = moduleRoot();
    String readme = Files.readString(root.resolve("README.md"));

    for (String document : DOCUMENTS) {
      assertThat(root.resolve(document)).as(document).isRegularFile();
      if (!"README.md".equals(document)) {
        assertThat(readme).as("README link: " + document).contains(document);
      }
    }
  }

  private Path moduleRoot() {
    Path local = Path.of(".").toAbsolutePath().normalize();
    if (Files.isRegularFile(local.resolve("pom.xml"))
        && local.getFileName() != null
        && "yak-ops-business-data-development".equals(local.getFileName().toString())) {
      return local;
    }
    return Path.of("yak-ops-business", "yak-ops-business-data-development");
  }
}
