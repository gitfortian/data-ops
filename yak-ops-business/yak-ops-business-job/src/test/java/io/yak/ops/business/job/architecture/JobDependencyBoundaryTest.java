package io.yak.ops.business.job.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class JobDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.job.";
  private static final Pattern JOB_IMPORT = Pattern.compile(
      "(?m)^import\\s+(?:static\\s+)?io\\.yak\\.ops\\.business\\.job\\.([A-Za-z0-9_]+)\\.[^;]+;");

  private static final Map<String, Set<String>> ALLOWED = Map.ofEntries(
      Map.entry("controller", Set.of("task", "environment")),
      Map.entry("discovery", Set.of("task")),
      Map.entry("adapter", Set.of("runtime", "task")),
      Map.entry("runtime", Set.of("task", "environment")),
      Map.entry("environment", Set.of("dao")),
      Map.entry("task", Set.of()),
      Map.entry("dao", Set.of()),
      Map.entry("config", Set.of()));

  @Test
  void topLevelPackagesFollowDeclaredAcyclicGraph() throws IOException {
    Map<String, Set<String>> graph = new HashMap<>();
    ALLOWED.keySet().forEach(key -> graph.put(key, new LinkedHashSet<>()));

    for (Dependency dependency : dependencies()) {
      if (dependency.source().equals(dependency.target())) continue;
      assertThat(ALLOWED.get(dependency.source()))
          .as("missing dependency rule for %s", dependency.source())
          .isNotNull()
          .contains(dependency.target());
      graph.get(dependency.source()).add(dependency.target());
    }

    Set<String> visited = new HashSet<>();
    Set<String> visiting = new HashSet<>();
    Deque<String> path = new ArrayDeque<>();
    for (String node : graph.keySet()) detectCycle(node, graph, visited, visiting, path);
  }

  @Test
  void coreDoesNotDependOnConcreteTaskBusinessDomains() throws IOException {
    for (Path source : productionJavaFiles()) {
      String text = Files.readString(source);
      assertThat(text)
          .as(relative(source))
          .doesNotContain("import io.yak.ops.business.sync.offline")
          .doesNotContain("import io.yak.ops.business.workflow")
          .doesNotContain("import io.yak.ops.business.development");
    }
  }

  @Test
  void controllerAndRuntimeDoNotReachPersistenceDirectly() throws IOException {
    for (String role : List.of("controller", "discovery", "runtime", "adapter")) {
      Path directory = productionRoot().resolve(role);
      if (!Files.exists(directory)) continue;
      try (Stream<Path> paths = Files.walk(directory)) {
        for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
          assertThat(Files.readString(source))
              .as(relative(source))
              .doesNotContain("import io.yak.ops.business.job.dao");
        }
      }
    }
  }

  @Test
  void legacySyncTaskCompatibilityBoundaryCannotReturn() throws IOException {
    for (Path source : productionJavaFiles()) {
      assertThat(Files.readString(source))
          .as(relative(source))
          .doesNotContain(
              "SyncTaskRunner",
              "SyncTaskExecutorAdapter",
              "SyncTaskExecution");
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    for (String bucket : List.of(
        "service", "common", "helper", "helpers", "utils", "util", "base", "support")) {
      assertThat(Files.exists(productionRoot().resolve(bucket))).as(bucket).isFalse();
    }
  }

  private List<Dependency> dependencies() throws IOException {
    List<Dependency> result = new ArrayList<>();
    for (Path source : productionJavaFiles()) {
      String relative = relative(source);
      String from = topLevel(relative);
      Matcher matcher = JOB_IMPORT.matcher(Files.readString(source));
      while (matcher.find()) {
        result.add(new Dependency(relative, from, matcher.group(1)));
      }
    }
    return result;
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
      throw new AssertionError("Job package cycle: " + cycle);
    }
    path.addLast(node);
    for (String next : graph.getOrDefault(node, Set.of())) {
      detectCycle(next, graph, visited, visiting, path);
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

  private String topLevel(String relative) {
    int slash = relative.indexOf('/');
    return slash < 0 ? "root" : relative.substring(0, slash);
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/job");
    if (Files.isDirectory(local)) return local;
    Path repository = Path.of(
        "yak-ops-business", "yak-ops-business-job", "src", "main", "java",
        "io", "yak", "ops", "business", "job");
    assertThat(Files.isDirectory(repository)).isTrue();
    return repository;
  }

  private record Dependency(String path, String source, String target) {}
}
