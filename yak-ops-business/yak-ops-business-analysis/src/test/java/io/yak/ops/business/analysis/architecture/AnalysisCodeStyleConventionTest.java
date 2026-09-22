package io.yak.ops.business.analysis.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Low-ambiguity repository-wide code-style rules for Analysis production code. */
class AnalysisCodeStyleConventionTest {

  private static final Pattern WILDCARD_IMPORT =
      Pattern.compile("(?m)^import\\s+(?:static\\s+)?[^;]+\\*;");
  private static final Pattern FIELD_INJECTION = Pattern.compile(
      "(?m)@(Autowired|Resource|Inject)\\s*\\R\\s*"
          + "(?:private|protected|public)\\s+[^\\n(]+;");
  private static final Pattern SERVICE_CLASS = Pattern.compile(
      "(?s)@Service\\s*(?:\\R|@[^\\R]+\\R)*public\\s+class\\s+([A-Za-z0-9_]+)");
  private static final Set<String> STABLE_SERVICES =
      Set.of("AnalysisService", "AnalysisReferenceService");

  @Test
  void productionSourceFollowsRepositoryStyle() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        String relative = root.relativize(file).toString();
        assertThat(WILDCARD_IMPORT.matcher(source).find())
            .as("%s must not use wildcard imports", relative)
            .isFalse();
        assertThat(FIELD_INJECTION.matcher(source).find())
            .as("%s must use constructor injection", relative)
            .isFalse();
        assertThat(source)
            .as("%s must avoid low-signal shortcuts", relative)
            .doesNotContain("System.out.", "System.err.", "BeanUtils.copyProperties");
      }
    }
  }

  @Test
  void serviceStereotypeIsReservedForStableFacades() throws IOException {
    Set<String> found = new HashSet<>();
    try (Stream<Path> files = Files.walk(productionRoot())) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Matcher matcher = SERVICE_CLASS.matcher(Files.readString(file, StandardCharsets.UTF_8));
        if (!matcher.find()) continue;
        String className = matcher.group(1);
        found.add(className);
        assertThat(STABLE_SERVICES)
            .as("@Service must be a stable Analysis facade: %s", file)
            .contains(className);
      }
    }
    assertThat(found).containsExactlyInAnyOrderElementsOf(STABLE_SERVICES);
  }

  @Test
  void domainAndSemanticValueObjectsRemainFrameworkFree() throws IOException {
    assertFrameworkFreeDirectory(productionRoot().resolve("domain"));

    for (String relative : List.of(
        "query/AnalysisAggregation.java",
        "query/AnalysisFilterBinding.java",
        "query/AnalysisFilterOperator.java",
        "query/AnalysisMetricBinding.java",
        "query/AnalysisQuerySpec.java",
        "query/AnalysisSortBinding.java",
        "query/AnalysisSortDirection.java",
        "visualization/AnalysisChartType.java",
        "visualization/AnalysisVisualConfig.java")) {
      assertFrameworkFree(productionRoot().resolve(relative));
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
        .as("Analysis must not maintain a module-local CODE_STYLE.md")
        .isFalse();

    long count;
    try (Stream<Path> paths = Files.walk(repository, 6)) {
      count = paths.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().equals("CODE_STYLE.md"))
          .count();
    }
    assertThat(count).as("Yak Ops keeps one repository-wide CODE_STYLE.md").isEqualTo(1L);
  }

  private void assertFrameworkFreeDirectory(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        assertFrameworkFree(file);
      }
    }
  }

  private void assertFrameworkFree(Path file) throws IOException {
    String source = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(source)
        .as("Analysis semantic value must remain framework-free: %s", file)
        .doesNotContain(
            "org.springframework.",
            "com.baomidou.mybatisplus.",
            "io.yak.ops.business.analysis.controller.",
            "io.yak.ops.business.analysis.dao.",
            "io.yak.ops.common.bean.po.",
            "lombok.Data",
            "@Data");
  }

  private Path productionRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/analysis");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/analysis"))) return local;
    return Path.of("yak-ops-business", "yak-ops-business-analysis").toAbsolutePath().normalize();
  }
}
