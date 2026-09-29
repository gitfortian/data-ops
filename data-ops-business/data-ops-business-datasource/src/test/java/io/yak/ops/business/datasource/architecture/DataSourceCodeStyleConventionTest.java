package io.yak.ops.business.datasource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Source-level conventions that keep datasource role-oriented instead of drifting back to generic layers. */
class DataSourceCodeStyleConventionTest {

  private static final Path SOURCE_ROOT =
      Path.of("src/main/java/io/yak/ops/business/datasource");
  private static final Pattern WILDCARD_IMPORT =
      Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?[^;]+\\.\\*;\\s*$");
  /** Field injection only: annotation line directly above a field declaration. Constructor
   * injection (`@Autowired` above `public ClassName(`) stays legal. */
  private static final Pattern FIELD_INJECTION =
      Pattern.compile(
          "(?m)@(?:Autowired|Resource|Inject)\\s*\\n\\s*(?:private|protected|public)\\s+"
              + "(?![A-Za-z_$][\\w$]*\\s*\\()");
  private static final Pattern HISTORICAL_MIGRATION_MARKER =
      Pattern.compile("(?i)\\b(stage|phase|wave)[ -]?\\d+\\b");

  @Test
  void wildcardImportsAreForbidden() throws IOException {
    for (SourceFile source : sources()) {
      assertThat(WILDCARD_IMPORT.matcher(source.content()).find())
          .as("Wildcard import found in %s", source.relativePath())
          .isFalse();
    }
  }

  @Test
  void stdoutAndStderrAreForbidden() throws IOException {
    for (SourceFile source : sources()) {
      assertThat(source.content())
          .as(source.relativePath())
          .doesNotContain("System.out.")
          .doesNotContain("System.err.");
    }
  }

  @Test
  void fieldInjectionIsForbidden() throws IOException {
    for (SourceFile source : sources()) {
      assertThat(FIELD_INJECTION.matcher(source.content()).find())
          .as("Field injection marker found in %s", source.relativePath())
          .isFalse();
    }
  }

  @Test
  void serviceImplAndGenericHelperNamesDoNotReturn() throws IOException {
    for (SourceFile source : sources()) {
      String fileName = Path.of(source.relativePath()).getFileName().toString();
      assertThat(fileName).doesNotEndWith("ServiceImpl.java");
      assertThat(fileName).doesNotEndWith("Helper.java");
      assertThat(fileName).doesNotEndWith("Utils.java");
    }
  }

  @Test
  void productionCommentsDoNotDescribeHistoricalMigrationStages() throws IOException {
    for (SourceFile source : sources()) {
      assertThat(HISTORICAL_MIGRATION_MARKER.matcher(source.content()).find())
          .as("Historical migration marker found in %s", source.relativePath())
          .isFalse();
    }
  }

  @Test
  void aggregateMappingDoesNotFallBackToBeanUtilsCopyProperties() throws IOException {
    for (SourceFile source : sources()) {
      assertThat(source.content())
          .as(source.relativePath())
          .doesNotContain("BeanUtils.copyProperties");
    }
  }

  @Test
  void domainDoesNotUseLombokData() throws IOException {
    for (SourceFile source : sources()) {
      if (!source.relativePath().startsWith("domain/")) continue;
      assertThat(source.content())
          .as(source.relativePath())
          .doesNotContain("import lombok.Data;")
          .doesNotContain("@Data");
    }
  }

  private List<SourceFile> sources() throws IOException {
    assertThat(Files.isDirectory(SOURCE_ROOT))
        .as("Datasource source root must exist: %s", SOURCE_ROOT)
        .isTrue();
    try (var paths = Files.walk(SOURCE_ROOT)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .map(
              path -> {
                try {
                  return new SourceFile(
                      SOURCE_ROOT.relativize(path).toString().replace('\\', '/'),
                      Files.readString(path));
                } catch (IOException exception) {
                  throw new IllegalStateException("Failed to read " + path, exception);
                }
              })
          .toList();
    }
  }

  private record SourceFile(String relativePath, String content) {}
}
