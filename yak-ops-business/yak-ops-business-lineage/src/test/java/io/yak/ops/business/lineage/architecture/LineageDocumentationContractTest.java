package io.yak.ops.business.lineage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Keeps the Lineage documentation set aligned with the final architecture contract. */
class LineageDocumentationContractTest {

  private static final Set<String> MODULE_DOCUMENTS =
      Set.of(
          "ARCHITECTURE.md",
          "DEPENDENCIES.md",
          "DOMAIN.md",
          "README.md",
          "REQUIREMENTS.md",
          "REVIEW.md");

  private static final Map<String, Set<String>> REQUIRED_MARKERS =
      Map.of(
          "README.md",
          Set.of("Active Package Shape", "Public API", "Architecture Guards"),
          "ARCHITECTURE.md",
          Set.of("Active Package Map", "Public API Boundary", "Extension Protocol"),
          "DEPENDENCIES.md",
          Set.of("Active Dependency Graph", "Public API Corridors", "Maven Boundary"),
          "REQUIREMENTS.md",
          Set.of("Public Contract", "Non-goals", "Acceptance"),
          "REVIEW.md",
          Set.of("Public API", "Reject Signals"),
          "DOMAIN.md",
          Set.of("Asset", "Relation", "Graph"));

  @Test
  void moduleDocumentationSetIsExplicit() throws IOException {
    Set<String> actual = new LinkedHashSet<>();
    try (Stream<Path> files = Files.list(moduleRoot())) {
      files.filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".md"))
          .forEach(actual::add);
    }

    assertThat(actual)
        .as("Lineage documentation files form one maintained contract")
        .containsExactlyInAnyOrderElementsOf(MODULE_DOCUMENTS);
  }

  @Test
  void readmeLinksEveryCompanionDocument() throws IOException {
    String readme = read("README.md");
    for (String document : MODULE_DOCUMENTS) {
      if ("README.md".equals(document)) continue;
      assertThat(readme)
          .as("README must link %s", document)
          .contains("(./" + document + ")");
    }
  }

  @Test
  void documentsDescribeTheFinalContractInsteadOfMigrationStages() throws IOException {
    for (String document : MODULE_DOCUMENTS) {
      String content = read(document);
      assertThat(content).as(document).startsWith("# ");
      assertThat(content).as(document).doesNotContain(
          "Stage 1",
          "Stage 2",
          "Stage 3",
          "Stage 4",
          "Stage 5",
          "Stage 6",
          "阶段 1",
          "阶段 2",
          "阶段 3",
          "阶段 4",
          "阶段 5",
          "阶段 6");

      for (String marker : REQUIRED_MARKERS.getOrDefault(document, Set.of())) {
        assertThat(content).as("%s must describe %s", document, marker).contains(marker);
      }
    }
  }

  private String read(String filename) throws IOException {
    return Files.readString(moduleRoot().resolve(filename), StandardCharsets.UTF_8);
  }

  private Path moduleRoot() {
    Path current = Paths.get(".").toAbsolutePath().normalize();
    if (Files.isRegularFile(current.resolve("README.md"))
        && Files.isDirectory(current.resolve("src/main/java"))) {
      return current;
    }

    Path repositoryRelative =
        current.resolve("yak-ops-business/yak-ops-business-lineage").normalize();
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Unable to locate Lineage module from %s", current)
        .isTrue();
    return repositoryRelative;
  }
}
