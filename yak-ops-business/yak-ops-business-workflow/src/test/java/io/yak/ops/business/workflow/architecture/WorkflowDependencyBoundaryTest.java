package io.yak.ops.business.workflow.architecture;

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

/** Source-level guard for the Workflow package graph and narrow cross-subsystem corridors. */
class WorkflowDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.workflow";
  private static final Pattern WORKFLOW_IMPORT = Pattern.compile(
      "(?m)^import\\s+(?:static\\s+)?("
          + Pattern.quote(BASE)
          + "\\.([A-Za-z0-9_]+)\\.[^;]+);");

  private static final Map<String, Set<String>> ALLOWED_TOP_LEVEL_DEPENDENCIES =
      Map.ofEntries(
          Map.entry(
              "controller",
              Set.of("backfill", "definition", "domain", "execution", "runtime", "schedule")),
          Map.entry(
              "backfill",
              Set.of("dao", "definition", "domain", "execution", "repository", "schedule")),
          Map.entry("schedule", Set.of("dao", "definition", "domain", "execution", "repository")),
          Map.entry("execution", Set.of("dao", "domain", "repository", "runtime")),
          Map.entry("definition", Set.of("dao", "domain", "repository", "runtime")),
          Map.entry("runtime", Set.of("domain", "observability", "repository")),
          Map.entry("observability", Set.of()),
          Map.entry("repository", Set.of("dao", "domain")),
          Map.entry("dao", Set.of()),
          Map.entry("domain", Set.of()));

  @Test
  void topLevelPackagesFollowDeclaredDependencyGraph() throws IOException {
    for (Dependency dependency : workflowDependencies()) {
      Set<String> allowed =
          ALLOWED_TOP_LEVEL_DEPENDENCIES.getOrDefault(dependency.sourcePackage(), Set.of());
      assertThat(allowed)
          .as("%s imports %s", dependency.relativePath(), dependency.importedType())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void declaredAndActualGraphsRemainAcyclic() throws IOException {
    assertAcyclic(ALLOWED_TOP_LEVEL_DEPENDENCIES, "declared Workflow dependency graph");

    Map<String, Set<String>> actual = new HashMap<>();
    ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet()
        .forEach(key -> actual.put(key, new HashSet<>()));
    for (Dependency dependency : workflowDependencies()) {
      actual.computeIfAbsent(dependency.sourcePackage(), ignored -> new HashSet<>())
          .add(dependency.targetPackage());
    }
    assertAcyclic(actual, "actual Workflow import graph");
  }

  @Test
  void highRiskCorridorsStayNarrow() throws IOException {
    assertExactCorridor(
        "runtime",
        "observability",
        Set.of(BASE + ".observability.WorkflowEventStream"));
    assertExactCorridor(
        "schedule",
        "definition",
        Set.of(BASE + ".definition.WorkflowDefinitionManager"));
    assertExactCorridor(
        "schedule",
        "execution",
        Set.of(
            BASE + ".execution.WorkflowLauncher",
            BASE + ".execution.WorkflowExecutionReactivationGuard"));
    assertExactCorridor(
        "definition",
        "runtime",
        Set.of(BASE + ".runtime.WorkflowRuntime"));
    assertExactCorridor(
        "backfill",
        "execution",
        Set.of(
            BASE + ".execution.WorkflowBusinessDateRerunGateway",
            BASE + ".execution.WorkflowLauncher"));
  }

  @Test
  void controllerDoesNotReachPersistenceOrSchedulerInternals() throws IOException {
    for (Dependency dependency : workflowDependencies()) {
      if (!dependency.sourcePackage().equals("controller")) continue;
      assertThat(dependency.targetPackage())
          .as("Controller persistence boundary: %s", dependency)
          .isNotIn("dao", "repository");
      assertThat(dependency.importedType())
          .as("Controller must not enter Schedule engine/admission internals: %s", dependency)
          .doesNotContain(
              ".schedule.engine.",
              ".schedule.trigger.WorkflowScheduleTriggerAdmission",
              ".schedule.trigger.WorkflowScheduleTriggerCoordinator");
    }
  }

  @Test
  void scheduleNeverImportsBackfillImplementation() throws IOException {
    for (Dependency dependency : workflowDependencies()) {
      if (dependency.sourcePackage().equals("schedule")) {
        assertThat(dependency.targetPackage())
            .as("Schedule must use WorkflowBackfillTriggerGateway: %s", dependency)
            .isNotEqualTo("backfill");
      }
    }
  }

  @Test
  void executionNeverImportsScheduleOrBackfillImplementation() throws IOException {
    for (Dependency dependency : workflowDependencies()) {
      if (dependency.sourcePackage().equals("execution")) {
        assertThat(dependency.targetPackage())
            .as("Execution must use owner-defined ports: %s", dependency)
            .isNotIn("schedule", "backfill");
      }
    }
  }

  @Test
  void bottomPackagesDoNotPointBackToApplicationSubsystems() throws IOException {
    for (Dependency dependency : workflowDependencies()) {
      if (dependency.sourcePackage().equals("domain") || dependency.sourcePackage().equals("dao")) {
        throw new AssertionError(
            "Bottom Workflow package must not import application package: " + dependency);
      }
      if (dependency.sourcePackage().equals("repository")) {
        assertThat(dependency.targetPackage())
            .as("Repository must remain below application roles: %s", dependency)
            .isIn("dao", "domain");
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotReturn() {
    Path root = productionRoot();
    for (String forbidden :
        Set.of("service", "common", "helper", "utils", "util", "base", "persistence")) {
      assertThat(Files.exists(root.resolve(forbidden)))
          .as("Broad Workflow business bucket '%s' must not exist", forbidden)
          .isFalse();
    }
  }

  private void assertExactCorridor(String source, String target, Set<String> allowedTypes)
      throws IOException {
    for (Dependency dependency : workflowDependencies()) {
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
    assertThat(visited).as("%s must remain acyclic", label).isEqualTo(nodes.size());
  }

  private List<Dependency> workflowDependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        Path relative = root.relativize(file);
        if (relative.getNameCount() < 2) continue;
        String sourcePackage = relative.getName(0).toString();
        Matcher matcher = WORKFLOW_IMPORT.matcher(Files.readString(file, StandardCharsets.UTF_8));
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
    Path local = Path.of("src/main/java/io/yak/ops/business/workflow");
    if (Files.isDirectory(local)) return local;
    Path repositoryRelative = Path.of(
        "yak-ops-business",
        "yak-ops-business-workflow",
        "src",
        "main",
        "java",
        "io",
        "yak",
        "ops",
        "business",
        "workflow");
    assertThat(Files.isDirectory(repositoryRelative))
        .as("Unable to locate Workflow production source root")
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
