package io.yak.ops.business.dataservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Source-level conventions that keep Data Service role-oriented and explicit. */
class DataServiceCodeStyleConventionTest {

  private static final Path SOURCE_ROOT =
      Path.of("src/main/java/io/yak/ops/business/dataservice");
  private static final Pattern WILDCARD_IMPORT =
      Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?[^;]+\\.\\*;\\s*$");
  private static final Pattern FIELD_INJECTION =
      Pattern.compile("@(Autowired|Resource|Inject)\\b");
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
  void genericImplementationNamesDoNotReturn() throws IOException {
    for (SourceFile source : sources()) {
      String fileName = Path.of(source.relativePath()).getFileName().toString();
      assertThat(fileName)
          .doesNotEndWith("ServiceImpl.java")
          .doesNotEndWith("Helper.java")
          .doesNotEndWith("Utils.java");
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
  void domainMappingDoesNotFallBackToBeanUtilsCopyProperties() throws IOException {
    for (SourceFile source : sources()) {
      assertThat(source.content())
          .as(source.relativePath())
          .doesNotContain("BeanUtils.copyProperties");
    }
  }

  @Test
  void domainDoesNotUseLombokDataOrPublicSetterModel() throws IOException {
    for (SourceFile source : sources()) {
      if (!source.relativePath().startsWith("domain/")) continue;
      assertThat(source.content())
          .as(source.relativePath())
          .doesNotContain("import lombok.Data;")
          .doesNotContain("@Data");
    }
  }

  @Test
  void rawSecretNamesStayOutOfPersistenceAndObservabilityModels() throws IOException {
    for (SourceFile source : sources()) {
      if (!source.relativePath().startsWith("dao/model/")
          && !source.relativePath().startsWith("domain/InvocationRecord.java")) {
        continue;
      }
      assertThat(source.content())
          .as("Raw API key field leaked into persisted/audit model: %s", source.relativePath())
          .doesNotContain("rawKey")
          .doesNotContain("rawSecret");
    }
  }

  @Test
  void sqlAndDatasourceIdDoNotEnterControllerUpdateContractAsWritableFields() throws IOException {
    String controller = Files.readString(SOURCE_ROOT.resolve("controller/v1/DataServiceController.java"));
    int recordStart = controller.indexOf("record UpdateDataServiceRequest");
    assertThat(recordStart).isGreaterThanOrEqualTo(0);
    String recordSection = controller.substring(recordStart);
    int recordEnd = recordSection.indexOf("{}");
    assertThat(recordEnd).isGreaterThanOrEqualTo(0);
    String declaration = recordSection.substring(0, recordEnd);
    assertThat(declaration)
        .doesNotContain("sql")
        .doesNotContain("dataSourceId");
  }

  private List<SourceFile> sources() throws IOException {
    assertThat(Files.isDirectory(SOURCE_ROOT))
        .as("Data Service source root must exist: %s", SOURCE_ROOT)
        .isTrue();
    try (var paths = Files.walk(SOURCE_ROOT)) {
      return paths
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".java"))
          .map(path -> {
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
