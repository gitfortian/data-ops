package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DataDevelopmentRoleConventionTest {

  private static final Set<String> STABLE_SERVICE_ENTRIES =
      Set.of(
          "dataservice/DevelopmentDataServicePublicationService.java",
          "dataset/DevelopmentDatasetNodeService.java",
          "directory/DevelopmentDirectoryService.java",
          "editor/DevelopmentEditorSettingsService.java",
          "execution/DevelopmentTaskExecutionControlService.java",
          "execution/DevelopmentTaskExecutionService.java",
          "execution/DevelopmentTaskRunService.java",
          "node/DevelopmentNodeService.java",
          "release/DevelopmentReleaseService.java",
          "standard/DevelopmentStandardCheckService.java",
          "task/DevelopmentTaskService.java");

  @Test
  void serviceStereotypeIsReservedForStableApplicationEntries() throws IOException {
    Set<String> actual = new LinkedHashSet<>();
    try (Stream<Path> paths = Files.walk(productionRoot())) {
      for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = relative(source);
        if (relative.startsWith("service/")) continue;
        if (Files.readString(source).contains("@Service")) actual.add(relative);
      }
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(STABLE_SERVICE_ENTRIES);
  }

  @Test
  void technicalRolesOutsideLegacyIslandDoNotMasqueradeAsServices() throws IOException {
    try (Stream<Path> paths = Files.walk(productionRoot())) {
      for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = relative(source);
        if (relative.startsWith("service/")) continue;
        String name = source.getFileName().toString();
        if (name.matches(
            ".*(Analyzer|Parser|Resolver|Validator|Normalizer|Publisher|Reader|Provider|Calculator|Worker|Outbox|WriteTransaction)\\.java")) {
          assertThat(Files.readString(source)).as(relative).doesNotContain("@Service");
        }
      }
    }
  }

  @Test
  void movedRolesAreNotDuplicatedAsProductionCompatibilityWrappers() {
    for (String moved :
        Set.of(
            "DataDevelopmentTaskRevisionProvider.java",
            "DevelopmentDataServiceNodeSourceProvider.java",
            "DevelopmentDatasetNodeService.java",
            "DevelopmentDirectoryService.java",
            "DevelopmentEditorSettingsService.java",
            "DevelopmentLineageOutbox.java",
            "DevelopmentLineageWorker.java",
            "DevelopmentLineageWriteTransaction.java",
            "DevelopmentNodeService.java",
            "DevelopmentReleaseService.java",
            "DevelopmentSqlProjectionLineageAnalyzer.java",
            "DevelopmentTaskExecutionService.java",
            "DevelopmentTaskRunService.java",
            "DevelopmentTaskService.java")) {
      assertThat(Files.exists(productionRoot().resolve("service").resolve(moved)))
          .as(moved)
          .isFalse();
    }
  }

  @Test
  void projectionAnalyzerLivesWithLineageRoles() {
    assertThat(
            Files.exists(
                productionRoot()
                    .resolve("lineage")
                    .resolve("analysis")
                    .resolve("DevelopmentSqlProjectionLineageAnalyzer.java")))
        .isTrue();
  }

  private String relative(Path source) {
    return productionRoot().relativize(source).toString().replace('\\', '/');
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/development");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "data-ops-business",
        "data-ops-business-data-development",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "business",
        "development");
  }
}
