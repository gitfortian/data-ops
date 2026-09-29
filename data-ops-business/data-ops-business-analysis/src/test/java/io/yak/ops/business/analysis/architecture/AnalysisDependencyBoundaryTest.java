package io.yak.ops.business.analysis.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-level guard for Analysis package direction and cross-module corridors. */
class AnalysisDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.analysis";
  private static final Pattern ANALYSIS_IMPORT = Pattern.compile(
      "(?m)^import\\s+(?:static\\s+)?("
          + Pattern.quote(BASE)
          + "\\.([A-Za-z0-9_]+)\\.[^;]+);");

  private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
      Map.entry("controller", Set.of("domain", "query", "visualization")),
      Map.entry("asset", Set.of("dao", "lineage")),
      Map.entry("definition", Set.of("domain", "gateway", "query", "repository", "visualization")),
      Map.entry("domain", Set.of("query", "visualization")),
      Map.entry("query", Set.of("visualization")),
      Map.entry("visualization", Set.of()),
      Map.entry("reference", Set.of("definition")),
      Map.entry("lineage", Set.of("definition", "domain", "gateway", "query")),
      Map.entry("gateway", Set.of()),
      Map.entry("repository", Set.of("dao", "domain", "query", "visualization")),
      Map.entry("dao", Set.of()),
      Map.entry("config", Set.of()));

  @Test
  void topLevelPackagesFollowDeclaredGraph() throws IOException {
    for (Dependency dependency : dependencies()) {
      assertThat(ALLOWED.getOrDefault(dependency.sourcePackage(), Set.of()))
          .as("%s imports %s", dependency.relativePath(), dependency.importedType())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void declaredAndActualGraphsRemainAcyclic() throws IOException {
    assertAcyclic(ALLOWED, "declared Analysis dependency graph");

    java.util.Map<String, Set<String>> actual = new java.util.HashMap<>();
    ALLOWED.keySet().forEach(key -> actual.put(key, new HashSet<>()));
    for (Dependency dependency : dependencies()) {
      actual.computeIfAbsent(dependency.sourcePackage(), ignored -> new HashSet<>())
          .add(dependency.targetPackage());
    }
    assertAcyclic(actual, "actual Analysis import graph");
  }

  @Test
  void highRiskCorridorsStayNarrow() throws IOException {
    for (Dependency dependency : dependencies()) {
      if (dependency.sourcePackage().equals("definition")
          && dependency.targetPackage().equals("gateway")) {
        assertThat(dependency.importedType())
            .as("Definition may enter Dataset only through AnalysisDatasetGateway")
            .isEqualTo(BASE + ".gateway.dataset.AnalysisDatasetGateway");
      }
      if (dependency.sourcePackage().equals("lineage")
          && dependency.targetPackage().equals("gateway")) {
        assertThat(dependency.importedType())
            .as("Lineage projection may enter shared Lineage only through its owner-defined port")
            .startsWith(BASE + ".gateway.lineage.AnalysisLineageGraphGateway");
      }
      if (dependency.sourcePackage().equals("reference")
          && dependency.targetPackage().equals("definition")) {
        assertThat(dependency.importedType())
            .as("Reference read-side must stay narrow")
            .isEqualTo(BASE + ".definition.AnalysisReader");
      }
    }
  }

  @Test
  void controllerNeverEntersPersistenceGatewaysOrProjectionInternals() throws IOException {
    for (Dependency dependency : dependencies()) {
      if (!dependency.sourcePackage().equals("controller")) continue;
      assertThat(dependency.targetPackage())
          .as("Controller boundary: %s", dependency)
          .isNotIn("repository", "dao", "gateway", "lineage");
    }
  }

  @Test
  void datasetAndLineageImplementationsStayBehindGatewayAdapters() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = normalize(root.relativize(file));
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (source.contains("io.yak.ops.business.dataset.")) {
          assertThat(relative)
              .as("Dataset implementation type leaked outside gateway adapter: %s", relative)
              .startsWith("gateway/dataset/");
        }
        if (source.contains("io.yak.ops.business.lineage.")) {
          assertThat(relative)
              .as("Lineage implementation type leaked outside gateway adapter: %s", relative)
              .startsWith("gateway/lineage/");
        }
      }
    }
  }

  @Test
  void lowLevelPackagesDoNotPointBackToApplicationRoles() throws IOException {
    for (Dependency dependency : dependencies()) {
      if (dependency.sourcePackage().equals("dao")
          || dependency.sourcePackage().equals("visualization")
          || dependency.sourcePackage().equals("gateway")
          || dependency.sourcePackage().equals("config")) {
        throw new AssertionError("Low-level Analysis package must not import upper package: " + dependency);
      }
      if (dependency.sourcePackage().equals("repository")) {
        assertThat(dependency.targetPackage())
            .as("Repository must remain below application roles: %s", dependency)
            .isIn("dao", "domain", "query", "visualization");
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() throws IOException {
    Set<String> forbidden = Set.of(
        "service", "support", "common", "helper", "utils", "util", "base", "persistence");
    try (Stream<Path> paths = Files.walk(productionRoot())) {
      for (Path path : paths.filter(Files::isDirectory).toList()) {
        Path name = path.getFileName();
        if (name != null) {
          assertThat(forbidden)
              .as("Broad Analysis business bucket must not exist: %s", path)
              .doesNotContain(name.toString());
        }
      }
    }
  }

  private List<Dependency> dependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() < 2) continue; // root stable compatibility API
        String sourcePackage = relative.getName(0).toString();
        Matcher matcher = ANALYSIS_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
          String importedType = matcher.group(1);
          String targetPackage = matcher.group(2);
          if (!sourcePackage.equals(targetPackage)) {
            result.add(new Dependency(
                sourcePackage, targetPackage, importedType, normalize(relative)));
          }
        }
      }
    }
    return result;
  }

  private void assertAcyclic(Map<String, Set<String>> graph, String label) {
    Set<String> visited = new HashSet<>();
    Set<String> visiting = new HashSet<>();
    for (String node : graph.keySet()) {
      visit(node, graph, visiting, visited, label);
    }
  }

  private void visit(
      String node,
      Map<String, Set<String>> graph,
      Set<String> visiting,
      Set<String> visited,
      String label) {
    if (visited.contains(node)) return;
    assertThat(visiting.add(node)).as("%s has a cycle through %s", label, node).isTrue();
    for (String target : graph.getOrDefault(node, Set.of())) {
      visit(target, graph, visiting, visited, label);
    }
    visiting.remove(node);
    visited.add(node);
  }

  private Path productionRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/analysis");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/analysis"))) return local;
    Path repositoryRelative = Path.of("data-ops-business", "data-ops-business-analysis")
        .toAbsolutePath()
        .normalize();
    assertThat(Files.isDirectory(
            repositoryRelative.resolve("src/main/java/io/yak/ops/business/analysis")))
        .as("Unable to locate Analysis module root from %s", local)
        .isTrue();
    return repositoryRelative;
  }

  private String normalize(Path path) {
    return path.toString().replace(java.io.File.separatorChar, '/');
  }

  private record Dependency(
      String sourcePackage,
      String targetPackage,
      String importedType,
      String relativePath) {}
}
