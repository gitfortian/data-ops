package io.yak.ops.business.dataset.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Enforces low-ambiguity repository-wide CODE_STYLE rules for Dataset production code. */
class DatasetCodeStyleConventionTest {

  private static final Pattern WILDCARD_IMPORT =
      Pattern.compile("(?m)^import\\s+(?:static\\s+)?[^;]+\\*;");
  private static final Pattern FIELD_INJECTION =
      Pattern.compile("@(Autowired|Resource|Inject)\\b");
  private static final Pattern MIGRATION_MARKER =
      Pattern.compile("(?i)\\b(Stage|Wave|Phase)\\s*[-:]?\\s*\\d+\\b");
  private static final Pattern ENUM_NAME_EQUALS =
      Pattern.compile("\\.name\\(\\)\\.equals(?:IgnoreCase)?\\s*\\(");
  private static final Pattern REVERSE_ENUM_NAME_EQUALS =
      Pattern.compile("\"[A-Z][A-Z0-9_]*\"\\.equals(?:IgnoreCase)?\\s*\\([^;]*\\.name\\(\\)");

  private static final Set<String> SERVICE_FACADE_FILES =
      Set.of("DatasetService.java", "DatasetQueryService.java", "DevelopmentDatasetFacade.java");

  @Test
  void productionSourceFollowsRepositoryCodeStyle() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = normalize(root.relativize(file));
        String source = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(WILDCARD_IMPORT.matcher(source).find())
            .as("%s must not use wildcard imports", relative)
            .isFalse();
        assertThat(FIELD_INJECTION.matcher(source).find())
            .as("%s must use constructor injection / explicit provider boundaries", relative)
            .isFalse();
        assertThat(source)
            .as("%s must not write directly to stdout/stderr", relative)
            .doesNotContain("System.out.", "System.err.");
        assertThat(source)
            .as("%s must not use BeanUtils.copyProperties for Dataset boundaries", relative)
            .doesNotContain("BeanUtils.copyProperties");
        assertThat(MIGRATION_MARKER.matcher(source).find())
            .as("%s production comments/messages must describe current contracts", relative)
            .isFalse();
        assertThat(ENUM_NAME_EQUALS.matcher(source).find())
            .as("%s must compare typed enums before persistence/transport boundaries", relative)
            .isFalse();
        assertThat(REVERSE_ENUM_NAME_EQUALS.matcher(source).find())
            .as("%s must not downgrade typed enums to strings", relative)
            .isFalse();
      }
    }
  }

  @Test
  void serviceStereotypeIsReservedForThreeStableFacades() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (!source.contains("@Service")
            && !source.contains("org.springframework.stereotype.Service")) {
          continue;
        }
        assertThat(root.relativize(file).getNameCount())
            .as("@Service must stay in Dataset root facade package: %s", file)
            .isEqualTo(1);
        assertThat(SERVICE_FACADE_FILES)
            .as("Unexpected Dataset @Service: %s", file)
            .contains(file.getFileName().toString());
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    Path root = productionRoot();
    for (String forbidden : List.of("service", "common", "helper", "utils", "util", "base")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad Dataset business bucket '%s' must not exist", forbidden)
          .isFalse();
    }
  }

  @Test
  void repositoryUsesSingleRootCodeStyleDocument() throws IOException {
    Path module = moduleRoot();
    Path repository = module.getParent().getParent();
    assertThat(Files.isRegularFile(repository.resolve("CODE_STYLE.md")))
        .as("Yak Ops root CODE_STYLE.md must exist")
        .isTrue();
    assertThat(Files.exists(module.resolve("CODE_STYLE.md")))
        .as("Dataset must not maintain a module-local CODE_STYLE.md")
        .isFalse();

    long count;
    try (Stream<Path> paths = Files.walk(repository, 6)) {
      count =
          paths.filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().equals("CODE_STYLE.md"))
              .count();
    }
    assertThat(count)
        .as("Yak Ops must keep one repository-wide CODE_STYLE.md")
        .isEqualTo(1L);
  }

  private Path productionRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/dataset");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/dataset"))) {
      return local;
    }
    Path repositoryRelative =
        Path.of("yak-ops-business", "yak-ops-business-dataset").toAbsolutePath().normalize();
    assertThat(Files.isDirectory(repositoryRelative.resolve("src/main/java/io/yak/ops/business/dataset")))
        .as("Unable to locate Dataset module root from %s", local)
        .isTrue();
    return repositoryRelative;
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }
}
