package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DataDevelopmentDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.development";
  private static final Set<String> LEGACY_SERVICE_ALLOWLIST =
      Set.of(
          "DerivedAwareSqlColumnLineageParser.java",
          "DevelopmentDataServiceNodeService.java",
          "DevelopmentDataServiceSqlCompiler.java",
          "DevelopmentDraftConflictException.java",
          "DevelopmentSqlLineagePreviewService.java",
          "DevelopmentSqlLineageService.java",
          "DevelopmentTaskValidationException.java",
          "SqlColumnLineageParser.java",
          "SqlTableLineageParser.java",
          "TableIdentityResolver.java");

  @Test
  void packageGraphIsAcyclic() throws IOException {
    Map<String, Set<String>> graph = packageGraph();
    Set<String> visited = new HashSet<>();
    Set<String> visiting = new LinkedHashSet<>();
    Deque<String> path = new ArrayDeque<>();
    for (String node : graph.keySet()) {
      detectCycle(node, graph, visited, visiting, path);
    }
  }

  @Test
  void controllerCannotEnterPersistenceDirectly() throws IOException {
    assertPackageDoesNotImport("controller", BASE + ".repository", BASE + ".dao");
  }

  @Test
  void applicationRolesDoNotReachDaoDirectly() throws IOException {
    for (String role : List.of(
        "node", "directory", "task", "execution", "dataset", "dataservice", "release", "lineage", "editor")) {
      assertPackageDoesNotImport(role, BASE + ".dao");
    }
  }

  @Test
  void applicationRolesDoNotOwnJdbcTemplates() throws IOException {
    for (String role : List.of(
        "node", "directory", "task", "execution", "dataset", "dataservice", "release", "lineage", "editor", "service")) {
      assertPackageDoesNotImport(role, "org.springframework.jdbc.core.JdbcTemplate");
    }
  }

  @Test
  void coreDomainDoesNotOwnRuntimeOrPersistenceDependencies() throws IOException {
    assertPackageDoesNotImport(
        "domain",
        BASE + ".repository",
        BASE + ".dao",
        "io.yak.ops.business.job",
        "io.yak.ops.business.lineage",
        "io.yak.ops.business.taskcatalog",
        "org.springframework.jdbc",
        "org.apache.ibatis");
  }

  @Test
  void legacyServiceIslandIsFrozenToExactFileSet() throws IOException {
    Path legacy = productionRoot().resolve("service");
    assertThat(legacy).isDirectory();
    try (Stream<Path> paths = Files.list(legacy)) {
      Set<String> actual =
          paths
              .filter(path -> path.toString().endsWith(".java"))
              .map(path -> path.getFileName().toString())
              .collect(
                  java.util.stream.Collectors.toCollection(LinkedHashSet::new));
      assertThat(actual).containsExactlyInAnyOrderElementsOf(LEGACY_SERVICE_ALLOWLIST);
    }
  }

  @Test
  void dataServiceRuntimeProviderLivesWithItsOwnerContext() {
    assertThat(productionRoot().resolve("dataservice/DevelopmentDataServiceNodeSourceProvider.java"))
        .isRegularFile();
    assertThat(productionRoot().resolve("service/DevelopmentDataServiceNodeSourceProvider.java"))
        .doesNotExist();
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    for (String bucket : List.of("common", "helper", "helpers", "utils", "util", "base")) {
      assertThat(Files.exists(productionRoot().resolve(bucket))).as(bucket).isFalse();
    }
  }

  private void assertPackageDoesNotImport(String packageName, String... forbidden)
      throws IOException {
    Path directory = productionRoot().resolve(packageName);
    if (!Files.exists(directory)) return;
    try (Stream<Path> paths = Files.walk(directory)) {
      for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String text = Files.readString(source);
        for (String value : forbidden) {
          assertThat(text).as(source.toString()).doesNotContain("import " + value);
        }
      }
    }
  }

  private Map<String, Set<String>> packageGraph() throws IOException {
    Map<String, Set<String>> graph = new LinkedHashMap<>();
    for (Path source : productionJavaFiles()) {
      String from = topLevelPackage(relative(source));
      graph.computeIfAbsent(from, ignored -> new LinkedHashSet<>());
      for (String line : Files.readAllLines(source)) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("import " + BASE)) continue;
        String imported = trimmed.substring("import ".length(), trimmed.length() - 1);
        String suffix = imported.substring(BASE.length());
        String first = suffix.startsWith(".") ? suffix.substring(1).split("\\.")[0] : "";
        String to = first.isEmpty() || Character.isUpperCase(first.charAt(0)) ? "root" : first;
        if (!to.equals(from)) graph.get(from).add(to);
      }
    }
    return graph;
  }

  private String topLevelPackage(String relative) {
    int slash = relative.indexOf('/');
    return slash < 0 ? "root" : relative.substring(0, slash);
  }

  private void detectCycle(
      String node,
      Map<String, Set<String>> graph,
      Set<String> visited,
      Set<String> visiting,
      Deque<String> path) {
    if (visited.contains(node)) return;
    if (!visiting.add(node)) {
      List<String> cycle = new ArrayList<>(path);
      cycle.add(node);
      throw new AssertionError("Data Development package cycle: " + cycle);
    }
    path.addLast(node);
    for (String target : graph.getOrDefault(node, Set.of())) {
      if (graph.containsKey(target)) detectCycle(target, graph, visited, visiting, path);
    }
    path.removeLast();
    visiting.remove(node);
    visited.add(node);
  }

  private List<Path> productionJavaFiles() throws IOException {
    try (Stream<Path> paths = Files.walk(productionRoot())) {
      return paths.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }

  private String relative(Path source) {
    return productionRoot().relativize(source).toString().replace('\\', '/');
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/development");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business",
        "yak-ops-business-data-development",
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
