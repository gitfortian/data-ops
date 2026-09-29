package io.yak.ops.business.dataservice.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Executable top-level dependency contract for the Data Service business module. */
class DataServiceDependencyBoundaryTest {

  private static final Path SOURCE_ROOT =
      Path.of("src/main/java/io/yak/ops/business/dataservice");
  private static final String INTERNAL_PREFIX = "io.yak.ops.business.dataservice.";
  private static final String DATASOURCE_PREFIX = "io.yak.ops.business.datasource.";
  private static final String DEVELOPMENT_PREFIX = "io.yak.ops.business.development.";
  private static final String CORE_SQL_PREFIX = "io.yak.ops.core.execution.sql.";

  private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES =
      Map.ofEntries(
          Map.entry(
              "controller",
              Set.of(
                  "access",
                  "documentation",
                  "domain",
                  "execution",
                  "management",
                  "observability",
                  "publication",
                  "query",
                  "runtime")),
          Map.entry(
              "publication",
              Set.of("documentation", "domain", "execution", "management", "query")),
          Map.entry(
              "management",
              Set.of("access", "domain", "query", "repository", "runtime")),
          Map.entry(
              "documentation",
              Set.of("domain", "execution", "query", "repository")),
          Map.entry(
              "execution",
              Set.of("access", "domain", "query", "repository", "runtime")),
          Map.entry("observability", Set.of("domain", "query", "repository")),
          Map.entry("access", Set.of("domain", "query", "repository")),
          Map.entry("runtime", Set.of("domain", "query", "repository")),
          Map.entry("query", Set.of("domain", "repository")),
          Map.entry("repository", Set.of("dao", "domain")),
          Map.entry("dao", Set.of()),
          Map.entry("config", Set.of()),
          Map.entry("domain", Set.of()));

  private static final Set<String> FORBIDDEN_BUCKETS =
      Set.of("service", "common", "helper", "utils", "util", "base");

  @Test
  void everyTopLevelPackageHasAnExplicitDependencyContract() throws IOException {
    try (var paths = Files.list(SOURCE_ROOT)) {
      Set<String> actual = new LinkedHashSet<>(
          paths.filter(Files::isDirectory)
              .map(path -> path.getFileName().toString())
              .toList());
      assertThat(actual)
          .as("Every Data Service top-level package must be declared in DEPENDENCIES.md/guard")
          .isEqualTo(ALLOWED_DEPENDENCIES.keySet());
    }
  }

  @Test
  void actualTopLevelImportsFollowDeclaredDependencyMatrix() throws IOException {
    for (SourceFile source : sources()) {
      for (String target : internalImports(source.content())) {
        if (target.equals(source.topLevelPackage())) continue;
        Set<String> allowed = ALLOWED_DEPENDENCIES.get(source.topLevelPackage());
        assertThat(allowed)
            .as("Missing dependency contract for %s", source.relativePath())
            .isNotNull();
        assertThat(allowed)
            .as(
                "Forbidden Data Service dependency %s -> %s in %s",
                source.topLevelPackage(), target, source.relativePath())
            .contains(target);
      }
    }
  }

  @Test
  void declaredAndActualDependencyGraphsAreAcyclic() throws IOException {
    assertAcyclic(ALLOWED_DEPENDENCIES, "declared dependency matrix");

    Map<String, Set<String>> actual = new LinkedHashMap<>();
    for (String sourcePackage : ALLOWED_DEPENDENCIES.keySet()) {
      actual.put(sourcePackage, new LinkedHashSet<>());
    }
    for (SourceFile source : sources()) {
      for (String target : internalImports(source.content())) {
        if (!target.equals(source.topLevelPackage())) {
          actual.computeIfAbsent(source.topLevelPackage(), ignored -> new LinkedHashSet<>())
              .add(target);
        }
      }
    }
    assertAcyclic(actual, "actual production imports");
  }

  @Test
  void broadBusinessBucketsDoNotReturn() throws IOException {
    try (var paths = Files.list(SOURCE_ROOT)) {
      List<String> topLevelDirectories =
          paths.filter(Files::isDirectory)
              .map(path -> path.getFileName().toString())
              .toList();
      assertThat(topLevelDirectories).doesNotContainAnyElementsOf(FORBIDDEN_BUCKETS);
    }
  }

  @Test
  void queryAndRuntimeDoNotDependBackOnExecution() throws IOException {
    for (SourceFile source : sources()) {
      if (!Set.of("query", "runtime").contains(source.topLevelPackage())) continue;
      assertThat(source.content())
          .as("Cycle-prone reverse execution dependency in %s", source.relativePath())
          .doesNotContain("io.yak.ops.business.dataservice.execution.");
    }
  }

  @Test
  void persistenceTypesStayInsideDaoOrRepositoryAdapters() throws IOException {
    for (SourceFile source : sources()) {
      boolean usesMybatis = source.content().contains("com.baomidou.mybatisplus");
      boolean importsDao = source.content().contains("io.yak.ops.business.dataservice.dao.");
      if (!usesMybatis && !importsDao) continue;
      assertThat(Set.of("dao", "repository"))
          .as("Persistence implementation leaked into %s", source.relativePath())
          .contains(source.topLevelPackage());
    }
  }

  @Test
  void controllerNeverReachesRepositoryOrDao() throws IOException {
    for (SourceFile source : sources()) {
      if (!"controller".equals(source.topLevelPackage())) continue;
      assertThat(source.content())
          .as(source.relativePath())
          .doesNotContain("io.yak.ops.business.dataservice.repository.")
          .doesNotContain("io.yak.ops.business.dataservice.dao.")
          .doesNotContain("com.baomidou.mybatisplus");
    }
  }

  @Test
  void domainRemainsFrameworkAndPersistenceIndependent() throws IOException {
    for (SourceFile source : sources()) {
      if (!"domain".equals(source.topLevelPackage())) continue;
      assertThat(source.content())
          .as(source.relativePath())
          .doesNotContain("org.springframework.")
          .doesNotContain("com.baomidou.mybatisplus")
          .doesNotContain("lombok.")
          .doesNotContain("io.yak.framework.")
          .doesNotContain("io.yak.ops.business.dataservice.repository.")
          .doesNotContain("io.yak.ops.business.dataservice.dao.");
    }
  }

  @Test
  void sourceProviderContractIsAStandaloneExtensionBoundary() throws IOException {
    for (SourceFile source : sources()) {
      if (!source.relativePath().startsWith("publication/source/")) continue;
      for (String imported : imports(source.content())) {
        assertThat(imported)
            .as("Source provider contract must stay JDK-only: %s", source.relativePath())
            .startsWith("java.");
      }
    }
  }

  @Test
  void dataServiceNeverDependsOnDataDevelopmentImplementation() throws IOException {
    for (SourceFile source : sources()) {
      for (String imported : imports(source.content())) {
        assertThat(imported)
            .as("Data Service must not depend on Data Development: %s", source.relativePath())
            .doesNotStartWith(DEVELOPMENT_PREFIX);
      }
    }
  }

  @Test
  void datasourceDependencyIsLimitedToTheEnablementCondition() throws IOException {
    String allowed = DATASOURCE_PREFIX + "config.ConditionalOnDataSourceEnabled";
    for (SourceFile source : sources()) {
      for (String imported : imports(source.content())) {
        if (!imported.startsWith(DATASOURCE_PREFIX)) continue;
        assertThat(imported)
            .as("Datasource implementation leaked into %s", source.relativePath())
            .isEqualTo(allowed);
      }
    }
  }

  @Test
  void coreSqlExecutionContractIsUsedOnlyByExecutionPackage() throws IOException {
    for (SourceFile source : sources()) {
      boolean usesCoreSql = imports(source.content()).stream().anyMatch(item -> item.startsWith(CORE_SQL_PREFIX));
      if (!usesCoreSql) continue;
      assertThat(source.topLevelPackage())
          .as("SqlExecutionRuntime corridor expanded in %s", source.relativePath())
          .isEqualTo("execution");
    }
  }

  @Test
  void dataServiceHasNoImplicitServiceFacade() throws IOException {
    for (SourceFile source : sources()) {
      assertThat(source.content())
          .as("@Service requires explicit architecture approval: %s", source.relativePath())
          .doesNotContain("@Service");
    }
  }

  private List<SourceFile> sources() throws IOException {
    assertThat(Files.isDirectory(SOURCE_ROOT))
        .as("Data Service source root must exist: %s", SOURCE_ROOT)
        .isTrue();
    List<SourceFile> result = new ArrayList<>();
    try (var paths = Files.walk(SOURCE_ROOT)) {
      for (Path path : paths.filter(Files::isRegularFile).filter(this::isJava).toList()) {
        Path relative = SOURCE_ROOT.relativize(path);
        if (relative.getNameCount() < 2) continue;
        result.add(new SourceFile(
            relative.toString().replace('\\', '/'),
            relative.getName(0).toString(),
            Files.readString(path)));
      }
    }
    return result;
  }

  private boolean isJava(Path path) {
    return path.getFileName().toString().endsWith(".java");
  }

  private List<String> imports(String source) {
    List<String> result = new ArrayList<>();
    for (String rawLine : source.lines().toList()) {
      String line = rawLine.trim();
      if (!line.startsWith("import ")) continue;
      String imported = line.substring("import ".length()).trim();
      if (imported.startsWith("static ")) imported = imported.substring("static ".length()).trim();
      if (imported.endsWith(";")) imported = imported.substring(0, imported.length() - 1);
      result.add(imported);
    }
    return result;
  }

  private Set<String> internalImports(String source) {
    Set<String> targets = new LinkedHashSet<>();
    for (String imported : imports(source)) {
      if (!imported.startsWith(INTERNAL_PREFIX)) continue;
      String remainder = imported.substring(INTERNAL_PREFIX.length());
      int separator = remainder.indexOf('.');
      targets.add(separator < 0 ? remainder : remainder.substring(0, separator));
    }
    return targets;
  }

  private void assertAcyclic(Map<String, Set<String>> graph, String label) {
    Set<String> visited = new HashSet<>();
    Set<String> visiting = new HashSet<>();
    Deque<String> path = new ArrayDeque<>();
    for (String node : graph.keySet()) {
      visit(node, graph, visiting, visited, path, label);
    }
  }

  private void visit(
      String node,
      Map<String, Set<String>> graph,
      Set<String> visiting,
      Set<String> visited,
      Deque<String> path,
      String label) {
    if (visited.contains(node)) return;
    if (!visiting.add(node)) {
      fail("Cycle detected in " + label + ": " + String.join(" -> ", path) + " -> " + node);
    }
    path.addLast(node);
    for (String target : graph.getOrDefault(node, Set.of())) {
      if (graph.containsKey(target)) visit(target, graph, visiting, visited, path, label);
    }
    path.removeLast();
    visiting.remove(node);
    visited.add(node);
  }

  private record SourceFile(String relativePath, String topLevelPackage, String content) {}
}
