package io.yak.ops.business.sync.realtime.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-level guard for low-ambiguity rules from CODE_STYLE.md. */
class RealtimeCodeStyleConventionTest {

  private static final Pattern WILDCARD_IMPORT =
      Pattern.compile("(?m)^import\\s+(?:static\\s+)?[^;]+\\.\\*;");
  private static final Pattern STRING_STATE_COMPARISON =
      Pattern.compile(
          "(?:desiredState|observedState)\\(\\)\\.name\\(\\)\\.equals\\(|"
              + "\\\"[A-Z_]+\\\"\\.equals\\([^\\n]*(?:desiredState|observedState)\\(\\)\\.name\\(\\)\\)");
  private static final Pattern MIGRATION_STAGE_COMMENT =
      Pattern.compile("(?i)\\b(?:stage|wave)[ -]?[0-9]+\\b");

  @Test
  void productionSourcesAvoidAmbiguousJavaShortcuts() throws IOException {
    List<Violation> violations = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      reject(violations, file, WILDCARD_IMPORT, "wildcard import");
      rejectLiteral(violations, file, "System.out", "System.out");
      rejectLiteral(violations, file, "System.err", "System.err");
      rejectLiteral(violations, file, "@Autowired", "field/framework injection shortcut");
      rejectLiteral(violations, file, "@Resource", "field/framework injection shortcut");
      rejectLiteral(violations, file, "@Inject", "field/framework injection shortcut");
    }

    assertThat(violations)
        .as("Realtime production code must keep dependencies and diagnostics explicit")
        .isEmpty();
  }

  @Test
  void executionStateComparisonsStayTyped() throws IOException {
    List<Violation> violations = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      reject(
          violations,
          file,
          STRING_STATE_COMPARISON,
          "string comparison of DesiredState/ObservedState");
    }

    assertThat(violations)
        .as("Execution lifecycle decisions must compare domain enums, not enum names")
        .isEmpty();
  }

  @Test
  void submissionCredentialsUseNamedBindingsInsteadOfArrayPositions() throws IOException {
    List<Violation> violations = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      rejectLiteral(
          violations,
          file,
          "CredentialBinding[]",
          "positional source/sink credential array");
    }

    assertThat(violations)
        .as("Source/sink credential ownership must remain explicit at the Engine boundary")
        .isEmpty();
  }

  @Test
  void productionCommentsDescribeCurrentInvariantsNotMigrationStages() throws IOException {
    List<Violation> violations = new ArrayList<>();
    for (SourceFile file : productionSources()) {
      reject(violations, file, MIGRATION_STAGE_COMMENT, "historical Stage/Wave marker");
    }

    assertThat(violations)
        .as("Production comments must explain current why/invariant/danger, not migration history")
        .isEmpty();
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

  private Path productionRoot() {
    Path moduleLocal = Paths.get("src/main/java/io/yak/ops/business/sync/realtime");
    if (Files.isDirectory(moduleLocal)) {
      return moduleLocal;
    }

    Path repositoryRelative =
        Paths.get(
            "yak-ops-business",
            "yak-ops-business-sync",
            "yak-ops-business-sync-realtime",
            "src/main/java/io/yak/ops/business/sync/realtime");
    assertThat(Files.isDirectory(repositoryRelative))
        .as(
            "Unable to locate realtime-sync production source root from %s",
            Paths.get(".").toAbsolutePath())
        .isTrue();
    return repositoryRelative;
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  private record SourceFile(String relativePath, String source) {}

  private record Violation(String relativePath, String description) {}
}
