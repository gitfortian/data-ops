package io.yak.ops.business.dataset.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-level guard for the permanent Dataset package graph and external boundaries. */
class DatasetDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.dataset";
  private static final Pattern DATASET_IMPORT =
      Pattern.compile(
          "(?m)^import\\s+(?:static\\s+)?("
              + Pattern.quote(BASE)
              + "\\.([A-Za-z0-9_]+)\\.[^;]+);");
  private static final Pattern EXTERNAL_IMPORT =
      Pattern.compile(
          "(?m)^import\\s+(io\\.yak\\.ops\\.(?:business\\.(?:taskcatalog|datasource|lineage)|spi\\.datasource\\.execution|core\\.execution\\.sql)\\.[^;]+);");

  private static final Map<String, Set<String>> ALLOWED_TOP_LEVEL_DEPENDENCIES =
      Map.ofEntries(
          Map.entry("controller", Set.of()),
          Map.entry("asset", Set.of("dao", "lineage")),
          Map.entry("definition", Set.of("lineage", "repository")),
          Map.entry(
              "development",
              Set.of("definition", "gateway", "lineage", "publication", "repository", "schema")),
          Map.entry("publication", Set.of("definition", "gateway", "lineage", "repository", "schema")),
          Map.entry("schema", Set.of("gateway", "repository")),
          Map.entry("query", Set.of("gateway", "observability", "repository")),
          Map.entry("observability", Set.of("repository")),
          Map.entry("lineage", Set.of("gateway", "repository")),
          Map.entry("gateway", Set.of()),
          Map.entry("repository", Set.of("dao")),
          Map.entry("dao", Set.of()),
          Map.entry("config", Set.of()));

  @Test
  void topLevelPackagesFollowDeclaredDependencyGraph() throws IOException {
    for (Dependency dependency : datasetDependencies()) {
      Set<String> allowed =
          ALLOWED_TOP_LEVEL_DEPENDENCIES.getOrDefault(dependency.sourcePackage(), Set.of());
      assertThat(allowed)
          .as("%s imports %s", dependency.relativePath(), dependency.importedType())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void declaredAndActualGraphsRemainAcyclic() throws IOException {
    assertAcyclic(ALLOWED_TOP_LEVEL_DEPENDENCIES, "declared Dataset dependency graph");

    Map<String, Set<String>> actual = new HashMap<>();
    ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet()
        .forEach(key -> actual.put(key, new HashSet<>()));
    for (Dependency dependency : datasetDependencies()) {
      actual.computeIfAbsent(dependency.sourcePackage(), ignored -> new HashSet<>())
          .add(dependency.targetPackage());
    }
    assertAcyclic(actual, "actual Dataset import graph");
  }

  @Test
  void narrowCrossSubsystemCorridorsDoNotExpand() throws IOException {
    assertExactCorridor(
        "definition",
        "lineage",
        Set.of(BASE + ".lineage.DatasetLineageRefreshPublisher"));
    assertExactCorridor(
        "publication",
        "definition",
        Set.of(BASE + ".definition.DatasetReader"));
    assertExactCorridor(
        "publication",
        "lineage",
        Set.of(BASE + ".lineage.DatasetLineageRefreshPublisher"));
    assertExactCorridor(
        "query",
        "observability",
        Set.of(BASE + ".observability.DatasetQueryPerformanceRecorder"));
    assertExactCorridor(
        "observability",
        "repository",
        Set.of(BASE + ".repository.DatasetQueryPerformanceStore"));
    assertExactCorridor(
        "asset",
        "lineage",
        Set.of(BASE + ".lineage.DatasetLineageSynchronizer"));

    assertThat(crossing("lineage", "definition"))
        .as("Lineage must not point back to Definition")
        .isEmpty();
  }

  @Test
  void gatewayUsageStaysDatasetOwned() throws IOException {
    for (Dependency dependency : crossing("publication", "gateway")) {
      assertThat(dependency.importedType())
          .as("Publication must use Dataset-owned TaskCatalog gateway")
          .startsWith(BASE + ".gateway.taskcatalog.DatasetTaskCatalogGateway");
    }
    for (Dependency dependency : crossing("schema", "gateway")) {
      assertThat(dependency.importedType())
          .as("Schema must use Dataset-owned gateways")
          .matches(
              Pattern.quote(BASE + ".gateway.taskcatalog.DatasetTaskCatalogGateway")
                  + ".*|"
                  + Pattern.quote(BASE + ".gateway.datasource.DatasetSchemaSqlGateway")
                  + ".*");
    }
    for (Dependency dependency : crossing("lineage", "gateway")) {
      assertThat(dependency.importedType())
          .as("Lineage must use Dataset-owned gateways")
          .matches(
              Pattern.quote(BASE + ".gateway.taskcatalog.DatasetTaskCatalogGateway")
                  + ".*|"
                  + Pattern.quote(BASE + ".gateway.lineage.DatasetProjectionAnalyzerGateway")
                  + ".*|"
                  + Pattern.quote(BASE + ".gateway.lineage.DatasetLineageGraphGateway")
                  + ".*");
    }
  }

  @Test
  void externalModuleImportsAreIsolatedToDeclaredAdapters() throws IOException {
    for (ExternalDependency dependency : externalDependencies()) {
      String type = dependency.importedType();
      String path = dependency.relativePath();
      if (type.startsWith("io.yak.ops.business.taskcatalog.")) {
        assertThat(path).isEqualTo("gateway/taskcatalog/TaskCatalogDatasetAdapter.java");
      } else if (type.startsWith("io.yak.ops.business.datasource.catalog.")) {
        assertThat(path).isEqualTo("gateway/datasource/DataSourceDatasetCatalogAdapter.java");
      } else if (type.startsWith("io.yak.ops.business.datasource.config.")) {
        assertThat(path)
            .isIn(
                "config/DatasetPersistenceConfiguration.java",
                "dao/impl/DatasetDaoImpl.java",
                "asset/DatasetAssetProvider.java");
      } else if (type.startsWith("io.yak.ops.spi.datasource.execution.")) {
        assertThat(path).isEqualTo("gateway/datasource/DataSourceSchemaSqlAdapter.java");
      } else if (type.startsWith("io.yak.ops.business.lineage.")) {
        assertThat(path)
            .isIn(
                "gateway/lineage/LineageProjectionAnalyzerAdapter.java",
                "gateway/lineage/LineageGraphDatasetAdapter.java");
      } else if (type.startsWith("io.yak.ops.core.execution.sql.")) {
        assertThat(path)
            .isIn(
                "query/adapter/QueryRevisionDatasetSourceAdapter.java",
                "query/adapter/SqlQueryDatasetSourceAdapter.java");
      }
    }
  }

  @Test
  void bottomLayersDoNotPointBackToApplicationSubsystems() throws IOException {
    for (Dependency dependency : datasetDependencies()) {
      if (dependency.sourcePackage().equals("dao")) {
        throw new AssertionError("Dataset DAO must not import upper Dataset packages: " + dependency);
      }
      if (dependency.sourcePackage().equals("repository")) {
        assertThat(dependency.targetPackage())
            .as("Repository must remain below application roles: %s", dependency)
            .isEqualTo("dao");
      }
      if (dependency.sourcePackage().equals("config")) {
        throw new AssertionError("Dataset config must not import Dataset business roles: " + dependency);
      }
    }
  }

  private void assertExactCorridor(String source, String target, Set<String> allowedTypes)
      throws IOException {
    for (Dependency dependency : crossing(source, target)) {
      assertThat(allowedTypes)
          .as("Unexpected %s -> %s corridor in %s", source, target, dependency.relativePath())
          .contains(dependency.importedType());
    }
  }

  private List<Dependency> crossing(String source, String target) throws IOException {
    return datasetDependencies().stream()
        .filter(dependency -> dependency.sourcePackage().equals(source))
        .filter(dependency -> dependency.targetPackage().equals(target))
        .toList();
  }

  private void assertAcyclic(Map<String, Set<String>> graph, String label) {
    Set<String> nodes = new HashSet<>(graph.keySet());
    graph.values().forEach(nodes::addAll);
    Map<String, Integer> outgoing = new HashMap<>();
    Map<String, Set<String>> reverse = new HashMap<>();
    nodes.forEach(node -> outgoing.put(node, 0));

    for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
      for (String target : entry.getValue()) {
        outgoing.compute(entry.getKey(), (ignored, value) -> value + 1);
        reverse.computeIfAbsent(target, ignored -> new HashSet<>()).add(entry.getKey());
      }
    }

    ArrayDeque<String> ready = new ArrayDeque<>();
    outgoing.forEach(
        (node, degree) -> {
          if (degree == 0) {
            ready.add(node);
          }
        });

    int visited = 0;
    while (!ready.isEmpty()) {
      String node = ready.removeFirst();
      visited++;
      for (String dependent : reverse.getOrDefault(node, Set.of())) {
        int degree = outgoing.compute(dependent, (ignored, value) -> value - 1);
        if (degree == 0) {
          ready.add(dependent);
        }
      }
    }

    assertThat(visited).as("%s must remain acyclic", label).isEqualTo(nodes.size());
  }

  private List<Dependency> datasetDependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() < 2) {
          continue;
        }
        String sourcePackage = relative.getName(0).toString();
        Matcher matcher = DATASET_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
          String importedType = matcher.group(1);
          String targetPackage = matcher.group(2);
          if (!sourcePackage.equals(targetPackage)) {
            result.add(
                new Dependency(
                    sourcePackage, targetPackage, importedType, normalize(relative)));
          }
        }
      }
    }
    return result;
  }

  private List<ExternalDependency> externalDependencies() throws IOException {
    Path root = productionRoot();
    List<ExternalDependency> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        Matcher matcher = EXTERNAL_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
          result.add(new ExternalDependency(normalize(relative), matcher.group(1)));
        }
      }
    }
    return result;
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/dataset");
    if (Files.isDirectory(local)) {
      return local;
    }
    Path repositoryRelative =
        Path.of(
            "yak-ops-business",
            "yak-ops-business-dataset",
            "src",
            "main",
            "java",
            "io",
            "yak",
            "ops",
            "business",
            "dataset");
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Unable to locate Dataset production source root")
        .isTrue();
    return repositoryRelative;
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  private record Dependency(
      String sourcePackage,
      String targetPackage,
      String importedType,
      String relativePath) {}

  private record ExternalDependency(String relativePath, String importedType) {}
}
