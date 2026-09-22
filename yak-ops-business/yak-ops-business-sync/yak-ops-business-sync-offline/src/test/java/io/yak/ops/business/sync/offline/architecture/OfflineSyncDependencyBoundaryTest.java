package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

/** Permanent guard for offline-sync top-level dependencies and cross-subsystem corridors. */
class OfflineSyncDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.sync.offline.";
  private static final Pattern OFFLINE_IMPORT =
      Pattern.compile(
          "(?m)^import\\s+(?:static\\s+)?(io\\.yak\\.ops\\.business\\.sync\\.offline\\.([A-Za-z0-9_]+)\\.[^;]+);");

  private static final Map<String, Set<String>> ALLOWED_TOP_LEVEL_DEPENDENCIES =
      Map.ofEntries(
          Map.entry("controller", Set.of("config", "definition", "execution", "backfill")),
          Map.entry(
              "backfill", Set.of("config", "cursor", "definition", "domain", "execution", "repository")),
          Map.entry("reconcile", Set.of("config", "domain", "engine", "execution", "repository")),
          Map.entry(
              "execution",
              Set.of(
                  "config",
                  "cursor",
                  "definition",
                  "domain",
                  "engine",
                  "mapping",
                  "repository",
                  "schedule")),
          Map.entry("definition", Set.of("config", "domain", "engine", "mapping", "repository", "schedule")),
          Map.entry("schedule", Set.of("config", "domain", "repository")),
          Map.entry("cursor", Set.of("config", "domain", "repository")),
          Map.entry("mapping", Set.of("domain", "engine")),
          Map.entry("repository", Set.of("config", "dao", "domain")),
          Map.entry("dao", Set.of("config")),
          Map.entry("engine", Set.of("config")),
          Map.entry("domain", Set.of()),
          Map.entry("config", Set.of()));

  private static final Map<String, Set<String>> EXECUTION_CORRIDORS =
      Map.ofEntries(
          Map.entry("controller", Set.of(BASE + "execution.OfflineJobExecutionService")),
          Map.entry(
              "backfill",
              Set.of(
                  BASE + "execution.OfflineJobExecutionService",
                  BASE + "execution.OfflineExecutionScopeValidator")),
          Map.entry("reconcile", Set.of(BASE + "execution.OfflineJobExecutionService")));

  private static final Map<String, Set<String>> SCHEDULE_CORRIDORS =
      Map.ofEntries(
          Map.entry(
              "definition",
              Set.of(
                  BASE + "schedule.OfflineScheduleLifecycle",
                  BASE + "schedule.OfflineScheduleSupport")),
          Map.entry("execution", Set.of(BASE + "schedule.OfflineScheduleExecutionGateway")));

  @Test
  void topLevelPackagesFollowDeclaredDependencyGraph() throws IOException {
    for (Dependency dependency : dependencies()) {
      if (dependency.sourcePackage().equals(dependency.targetPackage())) {
        continue;
      }
      Set<String> allowed = ALLOWED_TOP_LEVEL_DEPENDENCIES.get(dependency.sourcePackage());
      assertThat(allowed)
          .as("%s must have a declared dependency rule", dependency.sourcePackage())
          .isNotNull();
      assertThat(allowed)
          .as(
              "%s must not depend on %s via %s",
              dependency.relativePath(), dependency.targetPackage(), dependency.importName())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void topLevelPackageDependencyGraphIsAcyclic() throws IOException {
    Map<String, Set<String>> graph = new HashMap<>();
    for (String source : ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet()) {
      graph.put(source, new HashSet<>());
    }
    for (Dependency dependency : dependencies()) {
      if (!dependency.sourcePackage().equals(dependency.targetPackage())) {
        graph.computeIfAbsent(dependency.sourcePackage(), ignored -> new HashSet<>())
            .add(dependency.targetPackage());
      }
    }

    for (String start : graph.keySet()) {
      assertThat(reaches(start, start, graph, new HashSet<>(), true))
          .as(
              "offline-sync top-level package dependency graph must be acyclic; cycle starts at %s",
              start)
          .isFalse();
    }
  }

  @Test
  void executionCrossPackageImportsUseDeclaredCorridors() throws IOException {
    for (Dependency dependency : dependencies()) {
      if (!"execution".equals(dependency.targetPackage())
          || "execution".equals(dependency.sourcePackage())) {
        continue;
      }
      Set<String> allowed =
          EXECUTION_CORRIDORS.getOrDefault(dependency.sourcePackage(), Set.of());
      assertThat(allowed)
          .as(
              "%s must enter execution through a declared corridor instead of %s",
              dependency.relativePath(), dependency.importName())
          .contains(dependency.importName());
    }
  }

  @Test
  void cursorCrossPackageImportsUseGatewayOnly() throws IOException {
    for (Dependency dependency : dependencies()) {
      if (!"cursor".equals(dependency.targetPackage())
          || "cursor".equals(dependency.sourcePackage())) {
        continue;
      }
      assertThat(dependency.importName())
          .as("%s must use OfflineCursorGateway", dependency.relativePath())
          .isEqualTo(BASE + "cursor.OfflineCursorGateway");
    }
  }

  @Test
  void scheduleCrossPackageImportsUseDeclaredCorridors() throws IOException {
    for (Dependency dependency : dependencies()) {
      if (!"schedule".equals(dependency.targetPackage())
          || "schedule".equals(dependency.sourcePackage())) {
        continue;
      }
      Set<String> allowed =
          SCHEDULE_CORRIDORS.getOrDefault(dependency.sourcePackage(), Set.of());
      assertThat(allowed)
          .as(
              "%s must enter schedule through a declared corridor instead of %s",
              dependency.relativePath(), dependency.importName())
          .contains(dependency.importName());
    }
  }

  @Test
  void domainAndPersistenceBottomLayersDoNotPointBackUp() throws IOException {
    for (Dependency dependency : dependencies()) {
      if ("domain".equals(dependency.sourcePackage())) {
        assertThat(dependency.targetPackage())
            .as("Domain must remain independent: %s", dependency.relativePath())
            .isEqualTo("domain");
      }
      if ("dao".equals(dependency.sourcePackage())) {
        assertThat(dependency.targetPackage())
            .as("DAO must not depend on business packages: %s", dependency.relativePath())
            .isIn("dao", "config");
      }
      if ("repository".equals(dependency.sourcePackage())) {
        assertThat(dependency.targetPackage())
            .as(
                "Repository adapter must point only to persistence/domain/config: %s",
                dependency.relativePath())
            .isIn("repository", "dao", "domain", "config");
      }
    }
  }

  private boolean reaches(
      String current,
      String target,
      Map<String, Set<String>> graph,
      Set<String> visited,
      boolean first) {
    if (!first && current.equals(target)) {
      return true;
    }
    if (!visited.add(current)) {
      return false;
    }
    for (String next : graph.getOrDefault(current, Set.of())) {
      if (reaches(next, target, graph, new HashSet<>(visited), false)) {
        return true;
      }
    }
    return false;
  }

  private List<Dependency> dependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        String sourcePackage = relative.substring(0, relative.indexOf('/'));
        Matcher matcher = OFFLINE_IMPORT.matcher(Files.readString(file));
        while (matcher.find()) {
          result.add(
              new Dependency(relative, sourcePackage, matcher.group(2), matcher.group(1)));
        }
      }
    }
    return result;
  }

  private Path productionRoot() {
    Path moduleRoot = Path.of("src/main/java/io/yak/ops/business/sync/offline");
    if (Files.isDirectory(moduleRoot)) {
      return moduleRoot;
    }

    Path repositoryRoot =
        Path.of(
            "yak-ops-business",
            "yak-ops-business-sync",
            "yak-ops-business-sync-offline",
            "src",
            "main",
            "java",
            "io",
            "yak",
            "ops",
            "business",
            "sync",
            "offline");
    assertThat(Files.isDirectory(repositoryRoot))
        .as("offline-sync production source root must be available to dependency test")
        .isTrue();
    return repositoryRoot;
  }

  private record Dependency(
      String relativePath,
      String sourcePackage,
      String targetPackage,
      String importName) {}
}
