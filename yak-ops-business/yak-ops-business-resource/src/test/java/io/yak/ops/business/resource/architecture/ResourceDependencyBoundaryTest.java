package io.yak.ops.business.resource.architecture;

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

/** Source-level guard for Resource subsystem direction and narrow integration corridors. */
class ResourceDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.resource";
  private static final Pattern RESOURCE_IMPORT = Pattern.compile(
      "(?m)^import\\s+(?:static\\s+)?("
          + Pattern.quote(BASE)
          + "\\.([A-Za-z0-9_]+)\\.[^;]+);");

  private static final Map<String, Set<String>> ALLOWED_TOP_LEVEL_DEPENDENCIES =
      Map.ofEntries(
          Map.entry(
              "controller",
              Set.of("config", "content", "domain", "exception", "namespace", "storage")),
          Map.entry("resolution", Set.of("config", "content", "domain", "namespace")),
          Map.entry(
              "content",
              Set.of("config", "domain", "exception", "namespace", "repository", "storage", "sync")),
          Map.entry(
              "namespace",
              Set.of("config", "domain", "exception", "repository", "storage", "sync")),
          Map.entry("storage", Set.of("config", "domain", "exception")),
          Map.entry("sync", Set.of("config", "domain")),
          Map.entry("repository", Set.of("config", "dao", "domain")),
          Map.entry("domain", Set.of()),
          Map.entry("exception", Set.of()),
          Map.entry("dao", Set.of()),
          Map.entry("config", Set.of()));

  @Test
  void topLevelPackagesFollowDeclaredDependencyGraph() throws IOException {
    for (Dependency dependency : resourceDependencies()) {
      Set<String> allowed =
          ALLOWED_TOP_LEVEL_DEPENDENCIES.getOrDefault(dependency.sourcePackage(), Set.of());
      assertThat(allowed)
          .as("%s imports %s", dependency.relativePath(), dependency.importedType())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void declaredAndActualGraphsRemainAcyclic() throws IOException {
    assertAcyclic(ALLOWED_TOP_LEVEL_DEPENDENCIES, "declared Resource dependency graph");

    Map<String, Set<String>> actual = new HashMap<>();
    ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet()
        .forEach(key -> actual.put(key, new HashSet<>()));
    for (Dependency dependency : resourceDependencies()) {
      actual.computeIfAbsent(dependency.sourcePackage(), ignored -> new HashSet<>())
          .add(dependency.targetPackage());
    }
    assertAcyclic(actual, "actual Resource import graph");
  }

  @Test
  void contentOnlyUsesNarrowNamespaceRoles() throws IOException {
    assertExactCorridor(
        "content",
        "namespace",
        Set.of(
            BASE + ".namespace.ResourceNamePolicy",
            BASE + ".namespace.ResourceNamespaceReader",
            BASE + ".namespace.ResourceParentResolver"));
  }

  @Test
  void businessStorageCorridorsStayResourceOwned() throws IOException {
    Set<String> businessStorageTypes = Set.of(
        BASE + ".storage.ResourceStorageGateway",
        BASE + ".storage.ResourceStorageLifecycle");
    assertExactCorridor("namespace", "storage", businessStorageTypes);
    assertExactCorridor("content", "storage", businessStorageTypes);
    assertExactCorridor(
        "controller",
        "storage",
        Set.of(BASE + ".storage.ResourceStorageReader"));
  }

  @Test
  void resourceMutationOnlyReachesSyncThroughDispatcher() throws IOException {
    Set<String> dispatcher = Set.of(BASE + ".sync.ResourceChangeDispatcher");
    assertExactCorridor("namespace", "sync", dispatcher);
    assertExactCorridor("content", "sync", dispatcher);
  }

  @Test
  void resolutionOnlyUsesResourceReadSide() throws IOException {
    assertExactCorridor(
        "resolution",
        "content",
        Set.of(BASE + ".content.ResourceContentReader"));
    assertExactCorridor(
        "resolution",
        "namespace",
        Set.of(BASE + ".namespace.ResourceNamespaceReader"));

    for (Dependency dependency : resourceDependencies()) {
      if (!dependency.sourcePackage().equals("resolution")) {
        continue;
      }
      assertThat(dependency.targetPackage())
          .as("Resolution is a consumer adapter: %s", dependency)
          .isNotIn("dao", "repository", "storage", "sync");
      assertThat(dependency.importedType())
          .doesNotContain("ResourceNamespaceManager", "ResourceContentManager");
    }
  }

  @Test
  void controllerNeverEntersPersistenceStorageOrSyncInternals() throws IOException {
    for (Dependency dependency : resourceDependencies()) {
      if (!dependency.sourcePackage().equals("controller")) {
        continue;
      }
      assertThat(dependency.targetPackage())
          .as("Controller boundary: %s", dependency)
          .isNotIn("dao", "repository", "resolution", "sync");
      assertThat(dependency.importedType())
          .doesNotContain(
              "ResourceStorageGateway",
              "ResourceStorageRegistry",
              "StorageOperatorGatewayAdapter");
    }
  }

  @Test
  void lowerPackagesNeverPointBackToApplicationRoles() throws IOException {
    for (Dependency dependency : resourceDependencies()) {
      if (Set.of("domain", "exception", "dao", "config")
          .contains(dependency.sourcePackage())) {
        throw new AssertionError(
            "Bottom Resource package must not import application package: " + dependency);
      }
      if (dependency.sourcePackage().equals("repository")) {
        assertThat(dependency.targetPackage())
            .as("Repository must remain below business roles: %s", dependency)
            .isIn("config", "dao", "domain");
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    Path root = productionRoot();
    for (String forbidden :
        Set.of("service", "common", "helper", "utils", "util", "base", "persistence")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad Resource business bucket '%s' must not exist", forbidden)
          .isFalse();
    }
  }

  private void assertExactCorridor(String source, String target, Set<String> allowedTypes)
      throws IOException {
    for (Dependency dependency : resourceDependencies()) {
      if (!dependency.sourcePackage().equals(source)
          || !dependency.targetPackage().equals(target)) {
        continue;
      }
      assertThat(allowedTypes)
          .as("Unexpected %s -> %s corridor in %s", source, target, dependency.relativePath())
          .contains(dependency.importedType());
    }
  }

  private void assertAcyclic(Map<String, Set<String>> graph, String label) {
    Set<String> nodes = new HashSet<>(graph.keySet());
    graph.values().forEach(nodes::addAll);
    Map<String, Integer> indegree = new HashMap<>();
    Map<String, Set<String>> reverse = new HashMap<>();
    nodes.forEach(node -> indegree.put(node, 0));

    for (Map.Entry<String, Set<String>> entry : graph.entrySet()) {
      for (String target : entry.getValue()) {
        indegree.compute(entry.getKey(), (ignored, value) -> value + 1);
        reverse.computeIfAbsent(target, ignored -> new HashSet<>()).add(entry.getKey());
      }
    }

    ArrayDeque<String> ready = new ArrayDeque<>();
    indegree.forEach((node, degree) -> {
      if (degree == 0) {
        ready.add(node);
      }
    });

    int visited = 0;
    while (!ready.isEmpty()) {
      String node = ready.removeFirst();
      visited++;
      for (String dependent : reverse.getOrDefault(node, Set.of())) {
        int degree = indegree.compute(dependent, (ignored, value) -> value - 1);
        if (degree == 0) {
          ready.add(dependent);
        }
      }
    }
    assertThat(visited).as("%s must remain acyclic", label).isEqualTo(nodes.size());
  }

  private List<Dependency> resourceDependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() < 2) {
          continue;
        }
        String sourcePackage = relative.getName(0).toString();
        Matcher matcher = RESOURCE_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
        while (matcher.find()) {
          String importedType = matcher.group(1);
          String targetPackage = matcher.group(2);
          if (!sourcePackage.equals(targetPackage)) {
            result.add(new Dependency(
                sourcePackage,
                targetPackage,
                importedType,
                normalize(relative)));
          }
        }
      }
    }
    return result;
  }

  private Path productionRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/resource");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java/io/yak/ops/business/resource"))) {
      return local;
    }
    Path repositoryRelative = Path.of("yak-ops-business", "yak-ops-business-resource")
        .toAbsolutePath()
        .normalize();
    assertThat(Files.isDirectory(
            repositoryRelative.resolve("src/main/java/io/yak/ops/business/resource")))
        .as("Unable to locate Resource module root from %s", local)
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
