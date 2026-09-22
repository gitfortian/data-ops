package io.yak.ops.business.dashboard.architecture;

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

class DashboardDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.dashboard";

  @Test
  void dashboardPackageGraphIsAcyclic() throws IOException {
    Map<String, Set<String>> graph = packageGraph();
    Set<String> visited = new HashSet<>();
    Set<String> visiting = new LinkedHashSet<>();
    Deque<String> path = new ArrayDeque<>();

    for (String node : graph.keySet()) {
      detectCycle(node, graph, visited, visiting, path);
    }
  }

  @Test
  void controllerCannotEnterPersistenceGatewaysOrLineageInternals() throws IOException {
    assertPackageDoesNotImport("controller",
        BASE + ".repository",
        BASE + ".dao",
        BASE + ".gateway",
        BASE + ".lineage");
  }

  @Test
  void compositionEntersAnalysisOnlyThroughDashboardOwnedGateway() throws IOException {
    assertPackageDoesNotImport("composition", "io.yak.ops.business.analysis");

    for (Path source : productionJavaFiles()) {
      String relative = relative(source);
      String text = Files.readString(source);
      if (text.contains("import io.yak.ops.business.analysis.AnalysisReferenceService")) {
        assertThat(relative).isEqualTo("gateway/analysis/AnalysisDashboardAdapter.java");
      }
    }
  }

  @Test
  void compositionEntersDatasetOnlyThroughDashboardOwnedGateway() throws IOException {
    assertPackageDoesNotImport("composition", "io.yak.ops.business.dataset");

    for (Path source : productionJavaFiles()) {
      String relative = relative(source);
      String text = Files.readString(source);
      if (text.contains("import io.yak.ops.business.dataset.")) {
        assertThat(relative).isEqualTo("gateway/dataset/DatasetDashboardAdapter.java");
      }
    }
  }

  @Test
  void lineageEntersSharedGraphOnlyThroughDashboardOwnedGateway() throws IOException {
    assertPackageDoesNotImport("lineage", "io.yak.ops.business.lineage");

    for (Path source : productionJavaFiles()) {
      String relative = relative(source);
      String text = Files.readString(source);
      if (text.contains("import io.yak.ops.business.lineage.")) {
        assertThat(relative).isEqualTo("gateway/lineage/LineageDashboardAdapter.java");
      }
    }
  }

  @Test
  void applicationRolesCannotReachDaoDirectly() throws IOException {
    for (String role : List.of("definition", "version", "publication", "composition", "reference", "lineage")) {
      assertPackageDoesNotImport(role, BASE + ".dao");
    }
  }

  @Test
  void referenceGuardCannotReachDaoDirectly() throws IOException {
    Path guard = productionRoot().resolve("reference/DashboardAnalysisDeletionGuard.java");
    String text = Files.readString(guard);
    assertThat(text)
        .contains("DashboardReferenceRepository")
        .doesNotContain("DashboardDao");
  }

  @Test
  void versionAndPublicationResponsibilitiesDoNotCross() throws IOException {
    assertPackageDoesNotImport("version", BASE + ".publication.DashboardPublisher");
    String publisher = Files.readString(productionRoot().resolve("publication/DashboardPublisher.java"));
    assertThat(publisher)
        .doesNotContain("appendVersion(")
        .doesNotContain("nextVersionNo(");
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    for (String bucket : List.of(
        "service", "common", "helper", "helpers", "utils", "util", "base", "persistence", "support")) {
      assertThat(Files.exists(productionRoot().resolve(bucket))).as(bucket).isFalse();
    }
    assertThat(Files.exists(productionRoot().resolve("repository/support"))).isFalse();
  }

  private void assertPackageDoesNotImport(String packageName, String... forbidden) throws IOException {
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
      throw new AssertionError("Dashboard package cycle: " + cycle);
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
    Path local = Path.of("src/main/java/io/yak/ops/business/dashboard");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-dashboard", "src", "main", "java",
        "io", "yak", "ops", "business", "dashboard");
  }
}
