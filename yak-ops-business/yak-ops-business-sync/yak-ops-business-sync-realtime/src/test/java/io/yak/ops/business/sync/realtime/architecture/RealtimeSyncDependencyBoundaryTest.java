package io.yak.ops.business.sync.realtime.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

/** Source-level architecture guard for the final realtime-sync package graph and narrow corridors. */
class RealtimeSyncDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.sync.realtime";
  private static final Pattern IMPORT =
      Pattern.compile("(?m)^import\\s+(?:static\\s+)?(" + Pattern.quote(BASE) + "\\.([A-Za-z0-9_]+)\\.[^;]+);");
  private static final Pattern SERVICE = Pattern.compile("@Service(?:\\s*\\([^)]*\\))?");

  private static final Map<String, Set<String>> ALLOWED_TOP_LEVEL_DEPENDENCIES =
      Map.ofEntries(
          Map.entry("controller", Set.of("definition", "domain", "environment", "execution", "observability")),
          Map.entry("definition", Set.of("domain", "engine", "environment", "execution", "repository")),
          Map.entry("execution", Set.of("domain", "engine", "environment", "reconcile", "repository")),
          Map.entry("reconcile", Set.of("config", "domain", "engine", "environment", "repository")),
          Map.entry("observability", Set.of("domain", "engine", "environment", "repository")),
          Map.entry("environment", Set.of("domain", "engine", "repository")),
          Map.entry("engine", Set.of("config", "domain", "repository")),
          Map.entry("repository", Set.of("config", "dao", "domain")),
          Map.entry("dao", Set.of("config")),
          Map.entry("config", Set.of("domain")),
          Map.entry("domain", Set.of()));

  @Test
  void topLevelPackagesFollowDeclaredDependencyGraph() throws IOException {
    for (Dependency dependency : dependencies()) {
      Set<String> allowed =
          ALLOWED_TOP_LEVEL_DEPENDENCIES.getOrDefault(dependency.sourcePackage(), Set.of());
      assertThat(allowed)
          .as("%s imports %s", dependency.relativePath(), dependency.importedType())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void declaredTopLevelDependencyGraphIsAcyclic() {
    Set<String> nodes = new HashSet<>(ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet());
    Map<String, Integer> indegree = new HashMap<>();
    Map<String, Set<String>> reverse = new HashMap<>();
    nodes.forEach(node -> indegree.put(node, 0));

    for (Map.Entry<String, Set<String>> entry : ALLOWED_TOP_LEVEL_DEPENDENCIES.entrySet()) {
      for (String target : entry.getValue()) {
        if (!nodes.contains(target)) continue;
        indegree.compute(entry.getKey(), (ignored, value) -> value + 1);
        reverse.computeIfAbsent(target, ignored -> new HashSet<>()).add(entry.getKey());
      }
    }

    ArrayDeque<String> ready = new ArrayDeque<>();
    indegree.forEach((node, degree) -> {
      if (degree == 0) ready.add(node);
    });
    int visited = 0;
    while (!ready.isEmpty()) {
      String node = ready.removeFirst();
      visited++;
      for (String dependent : reverse.getOrDefault(node, Set.of())) {
        int degree = indegree.compute(dependent, (ignored, value) -> value - 1);
        if (degree == 0) ready.add(dependent);
      }
    }

    assertThat(visited)
        .as("Declared realtime-sync dependency graph must remain acyclic")
        .isEqualTo(nodes.size());
  }

  @Test
  void crossSubsystemCorridorsStayNarrow() throws IOException {
    assertCorridor(
        "definition",
        "execution",
        Set.of(BASE + ".execution.RealtimeJobExecutionService"));

    assertCorridor(
        "execution",
        "reconcile",
        Set.of(
            BASE + ".reconcile.RealtimeReconcileCoordinator",
            BASE + ".reconcile.RealtimeDeleteSafetyChecker"));

    assertCorridor(
        "engine",
        "repository",
        Set.of(BASE + ".repository.RealtimeRuntimeIdentityStore"));

    for (String source : Set.of("definition", "execution", "reconcile", "observability")) {
      assertCorridor(
          source,
          "environment",
          Set.of(BASE + ".environment.RealtimeRuntimeResolver"));
    }

    assertCorridor(
        "controller",
        "definition",
        Set.of(BASE + ".definition.RealtimeJobDefinitionService"));
    assertCorridor(
        "controller",
        "execution",
        Set.of(
            BASE + ".execution.RealtimeJobExecutionService",
            BASE + ".execution.query.RealtimeJobQueryService"));
    assertCorridor(
        "controller",
        "observability",
        Set.of(BASE + ".observability.RealtimeObservabilityService"));
    assertCorridor(
        "controller",
        "environment",
        Set.of(BASE + ".environment.ComputeEnvironmentService"));
  }

  @Test
  void bottomLayersDoNotPointBackToApplicationSubsystems() throws IOException {
    for (Dependency dependency : dependencies()) {
      if (dependency.sourcePackage().equals("domain")) {
        throw new AssertionError(
            "Core domain must not import realtime application packages: " + dependency);
      }
      if (dependency.sourcePackage().equals("repository")) {
        assertThat(dependency.targetPackage())
            .as("Repository must remain below application subsystems: %s", dependency)
            .isIn("config", "dao", "domain");
      }
      if (dependency.sourcePackage().equals("dao")) {
        assertThat(dependency.targetPackage())
            .as("DAO must not depend on application/repository/engine layers: %s", dependency)
            .isEqualTo("config");
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    Path root = productionRoot();
    for (String forbidden : Set.of("service", "common", "helper", "utils", "base")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad business bucket '%s' must not exist under realtime-sync", forbidden)
          .isFalse();
    }
  }

  @Test
  void serviceStereotypeIsReservedForStableApplicationFacades() throws IOException {
    Set<String> actual = new HashSet<>();
    Path root = productionRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (SERVICE.matcher(source).find()) {
          actual.add(normalize(root.relativize(file)));
        }
      }
    }

    assertThat(actual)
        .containsExactlyInAnyOrder(
            "definition/RealtimeJobDefinitionService.java",
            "execution/RealtimeJobExecutionService.java",
            "execution/query/RealtimeJobQueryService.java",
            "observability/RealtimeObservabilityService.java",
            "environment/ComputeEnvironmentService.java");
  }

  @Test
  void persistenceCompatibilityMapperLivesBelowApplicationSubsystems() {
    Path root = productionRoot();
    assertThat(
            Files.exists(
                root.resolve("repository/support/CdcPipelineSpecCompatibilityMapper.java")))
        .isTrue();
    assertThat(
            Files.exists(
                root.resolve("definition/adapter/CdcPipelineSpecCompatibilityMapper.java")))
        .isFalse();
    assertThat(Files.exists(root.resolve("domain/CdcPipelineSpecCompatibilityMapper.java")))
        .isFalse();
  }

  private void assertCorridor(String source, String target, Set<String> allowedTypes)
      throws IOException {
    List<Dependency> crossing =
        dependencies().stream()
            .filter(dependency -> dependency.sourcePackage().equals(source))
            .filter(dependency -> dependency.targetPackage().equals(target))
            .toList();
    for (Dependency dependency : crossing) {
      assertThat(allowedTypes)
          .as("Unexpected %s -> %s corridor in %s", source, target, dependency.relativePath())
          .contains(dependency.importedType());
    }
  }

  private List<Dependency> dependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() < 2) continue;
        String sourcePackage = relative.getName(0).toString();
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
          String importedType = matcher.group(1);
          String targetPackage = matcher.group(2);
          if (sourcePackage.equals(targetPackage)) continue;
          result.add(
              new Dependency(
                  sourcePackage, targetPackage, importedType, normalize(relative)));
        }
      }
    }
    return result;
  }

  private Path productionRoot() {
    Path moduleLocal = Paths.get("src/main/java/io/yak/ops/business/sync/realtime");
    if (Files.isDirectory(moduleLocal)) return moduleLocal;

    Path repositoryRelative =
        Paths.get(
            "yak-ops-business",
            "yak-ops-business-sync",
            "yak-ops-business-sync-realtime",
            "src/main/java/io/yak/ops/business/sync/realtime");
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Unable to locate realtime-sync production source root from %s", Paths.get(".").toAbsolutePath())
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
}
