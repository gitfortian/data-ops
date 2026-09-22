package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-level guard for low-ambiguity rules from the repository root CODE_STYLE.md. */
class OfflineCodeStyleConventionTest {

  private static final Pattern WILDCARD_IMPORT =
      Pattern.compile("(?m)^import\\s+(?:static\\s+)?[^;]+\\.\\*;");
  private static final Pattern MIGRATION_MARKER =
      Pattern.compile("(?i)\\b(?:stage|wave)[ -]?[0-9]+\\b");

  @Test
  void repositoryUsesOneRootCodeStyleDocument() throws IOException {
    Path repositoryRoot = repositoryRoot();
    List<String> codeStyleDocuments = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(repositoryRoot)) {
      for (Path path :
          paths.filter(Files::isRegularFile)
              .filter(file -> file.getFileName().toString().equals("CODE_STYLE.md"))
              .toList()) {
        codeStyleDocuments.add(normalize(repositoryRoot.relativize(path)));
      }
    }

    assertThat(codeStyleDocuments)
        .containsExactly("CODE_STYLE.md");
  }

  @Test
  void productionSourcesAvoidAmbiguousJavaShortcuts() throws IOException {
    List<Violation> violations = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      reject(violations, file, WILDCARD_IMPORT, "wildcard import");
      rejectLiteral(violations, file, ";import ", "multiple imports on one line");
      rejectLiteral(violations, file, "System.out", "System.out");
      rejectLiteral(violations, file, "System.err", "System.err");
      rejectLiteral(violations, file, "@Autowired", "field/framework injection shortcut");
      rejectLiteral(violations, file, "@Resource", "field/framework injection shortcut");
      rejectLiteral(violations, file, "@Inject", "field/framework injection shortcut");
    }

    assertThat(violations)
        .as("Offline production code must keep dependencies and diagnostics explicit")
        .isEmpty();
  }

  @Test
  void productionCommentsDescribeCurrentContractsNotMigrationStages() throws IOException {
    List<Violation> violations = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      reject(violations, file, MIGRATION_MARKER, "historical Stage/Wave marker");
    }

    assertThat(violations)
        .as("Production code must describe current invariants, not migration history")
        .isEmpty();
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    Path root = productionRoot();
    for (String forbidden : List.of("service", "common", "helper", "utils", "base")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad business bucket '%s' must not exist under offline-sync", forbidden)
          .isFalse();
    }
  }

  private void reject(
      List<Violation> violations, SourceFile file, Pattern pattern, String description) {
    if (pattern.matcher(file.source()).find()) {
      violations.add(new Violation(file.relativePath(), description));
    }
  }

  private void rejectLiteral(
      List<Violation> violations, SourceFile file, String fragment, String description) {
    if (file.source().contains(fragment)) {
      violations.add(new Violation(file.relativePath(), description));
    }
  }

  private List<SourceFile> productionSources() throws IOException {
    Path root = productionRoot();
    List<SourceFile> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        result.add(
            new SourceFile(
                normalize(root.relativize(file)),
                Files.readString(file, StandardCharsets.UTF_8)));
      }
    }
    return result;
  }

  private Path moduleRoot() {
    Path moduleLocal = Path.of(".").toAbsolutePath().normalize();
    if (Files.isDirectory(moduleLocal.resolve("src/main/java/io/yak/ops/business/sync/offline"))) {
      return moduleLocal;
    }

    Path repositoryRelative =
        Path.of(
            "yak-ops-business",
            "yak-ops-business-sync",
            "yak-ops-business-sync-offline")
            .toAbsolutePath()
            .normalize();
    assertThat(Files.isDirectory(repositoryRelative.resolve("src/main/java/io/yak/ops/business/sync/offline")))
        .as("Unable to locate offline-sync module root from %s", moduleLocal)
        .isTrue();
    return repositoryRelative;
  }

  private Path productionRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/sync/offline");
  }

  private Path repositoryRoot() {
    Path current = Path.of(".").toAbsolutePath().normalize();
    if (Files.isRegularFile(current.resolve("CODE_STYLE.md"))) {
      return current;
    }

    Path module = moduleRoot();
    Path repository = module.getParent().getParent().getParent();
    assertThat(Files.isRegularFile(repository.resolve("CODE_STYLE.md")))
        .as("Repository root CODE_STYLE.md must exist from module %s", module)
        .isTrue();
    return repository;
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  private record SourceFile(String relativePath, String source) {}

  private record Violation(String relativePath, String description) {}
}
