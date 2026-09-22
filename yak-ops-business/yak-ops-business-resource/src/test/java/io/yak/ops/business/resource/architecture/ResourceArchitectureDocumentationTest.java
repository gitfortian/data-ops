package io.yak.ops.business.resource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Keeps the permanent Resource architecture contract discoverable from the module README. */
class ResourceArchitectureDocumentationTest {

  private static final List<String> CONTRACT_DOCUMENTS =
      List.of("REQUIREMENTS.md", "DOMAIN.md", "ARCHITECTURE.md", "DEPENDENCIES.md", "REVIEW.md");

  @Test
  void architectureContractDocumentsRemainPresentAndLinked() throws IOException {
    Path module = moduleRoot();
    String readme = Files.readString(module.resolve("README.md"), StandardCharsets.UTF_8);

    for (String document : CONTRACT_DOCUMENTS) {
      assertThat(Files.isRegularFile(module.resolve(document)))
          .as("Resource architecture contract %s must exist", document)
          .isTrue();
      assertThat(readme)
          .as("README must link Resource architecture contract %s", document)
          .contains("./" + document);
    }
  }

  @Test
  void dependencyDocumentNamesExecutableGuards() throws IOException {
    String dependencies = Files.readString(
        moduleRoot().resolve("DEPENDENCIES.md"), StandardCharsets.UTF_8);
    assertThat(dependencies)
        .contains(
            "ResourceDependencyBoundaryTest",
            "ResourceStorageGateway",
            "ResourceChangeDispatcher");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isRegularFile(local.resolve("README.md"))
        && Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/resource"))) {
      return local;
    }
    Path repositoryRelative = Path.of("yak-ops-business", "yak-ops-business-resource")
        .toAbsolutePath()
        .normalize();
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Unable to locate Resource module root from %s", local)
        .isTrue();
    return repositoryRelative;
  }
}
