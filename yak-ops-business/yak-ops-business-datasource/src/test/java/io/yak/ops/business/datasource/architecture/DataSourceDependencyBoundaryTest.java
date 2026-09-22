package io.yak.ops.business.datasource.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Executable top-level dependency contract for the datasource business module. */
class DataSourceDependencyBoundaryTest {

  private static final Path SOURCE_ROOT =
      Path.of("src/main/java/io/yak/ops/business/datasource");
  private static final String INTERNAL_PREFIX = "io.yak.ops.business.datasource.";

  private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES =
      Map.ofEntries(
          Map.entry(
              "controller",
              Set.of(
                  "api",
                  "catalog",
                  "config",
                  "connection",
                  "domain",
                  "exception",
                  "execution",
                  "gateway",
                  "management",
                  "query")),
          Map.entry(
              "management",
              Set.of("api", "config", "connection", "domain", "exception", "query", "repository")),
          Map.entry(
              "connection",
              Set.of("config", "domain", "exception", "gateway", "query", "repository")),
          Map.entry(
              "catalog",
              Set.of("api", "config", "domain", "exception", "gateway", "query")),
          Map.entry("query", Set.of("config", "domain", "exception", "gateway", "repository")),
          Map.entry(
              "execution",
              Set.of("config", "dao", "domain", "exception", "gateway", "plugin", "repository")),
          Map.entry("gateway", Set.of("config", "domain", "exception", "plugin", "security")),
          Map.entry("repository", Set.of("config", "dao", "domain")),
          Map.entry("plugin", Set.of("config", "exception")),
          Map.entry("dao", Set.of("config")),
          Map.entry("exception", Set.of("config", "security")),
          Map.entry("security", Set.of("config")),
          Map.entry("config", Set.of()),
          Map.entry("domain", Set.of()),
          Map.entry("api", Set.of()));

  private static final Set<String> FORBIDDEN_BUCKETS =
      Set.of("service", "common", "helper", "utils", "util", "base");

  @Test
  void actualTopLevelImportsFollowDeclaredDependencyMatrix() throws IOException {
    for (SourceFile source : sources()) {
      Set<String> targets = internalImports(source.content());
      for (String target : targets) {
        if (target.equals(source.topLevelPackage())) continue;
        Set<String> allowed = ALLOWED_DEPENDENCIES.get(source.topLevelPackage());
        assertThat(allowed)
            .as("Missing dependency contract for %s", source.relativePath())
            .isNotNull();
        assertThat(allowed)
            .as(
                "Forbidden datasource dependency %s -> %s in %s",
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
  void rawDatasourceSpiStaysAtExplicitAdapterBoundaries() throws IOException {
    for (SourceFile source : sources()) {
      if (!source.content().contains("io.yak.ops.spi.datasource")) continue;
      String path = source.relativePath();
      assertThat(
              path.startsWith("plugin/")
                  || path.startsWith("gateway/adapter/")
                  || path.startsWith("execution/adapter/"))
          .as("Raw datasource SPI leaked into %s", path)
          .isTrue();
    }
  }

  @Test
  void transportDtoAndVoStayAtControllerBoundary() throws IOException {
    for (SourceFile source : sources()) {
      boolean usesTransport =
          source.content().contains("io.yak.ops.common.bean.dto.")
              || source.content().contains("io.yak.ops.common.bean.vo.");
      if (!usesTransport) continue;
      assertThat(source.relativePath())
          .as("HTTP transport model leaked into %s", source.relativePath())
          .startsWith("controller/");
    }
  }

  @Test
  void persistenceTypesStayAtPersistenceOrAuditReadBoundary() throws IOException {
    for (SourceFile source : sources()) {
      boolean usesPersistence =
          source.content().contains("com.baomidou.mybatisplus")
              || source.content().contains("io.yak.ops.common.bean.po.");
      if (!usesPersistence) continue;
      String path = source.relativePath();
      assertThat(
              path.startsWith("dao/")
                  || path.startsWith("repository/")
                  || path.startsWith("execution/audit/")
                  // Shared persistence wiring (DataSource/SqlSessionFactory/tx manager)
                  // is infrastructure, not a persistence-type leak.
                  || path.equals("config/BusinessDatabaseConfiguration.java"))
          .as("Persistence type leaked into %s", path)
          .isTrue();
    }
  }

  @Test
  void legacyHttpMapRequestsStayAtControllerBoundary() throws IOException {
    for (SourceFile source : sources()) {
      if (!source.content().matches("(?s).*Map\\s*<\\s*String\\s*,\\s*Object\\s*>\\s+requestBody.*")) {
        continue;
      }
      assertThat(source.relativePath())
          .as("HTTP compatibility Map leaked into %s", source.relativePath())
          .startsWith("controller/");
    }
  }

  @Test
  void applicationRolesDoNotReachIntoAdaptersOrPluginRegistry() throws IOException {
    for (SourceFile source : sources()) {
      String top = source.topLevelPackage();
      boolean mainRole =
          Set.of("management", "query", "connection", "catalog").contains(top)
              || ("execution".equals(top) && !source.relativePath().startsWith("execution/adapter/"));
      if (!mainRole) continue;
      assertThat(source.content())
          .as(source.relativePath())
          .doesNotContain(".gateway.adapter.")
          .doesNotContain(".plugin.DataSourcePluginRegistry")
          .doesNotContain("io.yak.ops.spi.datasource");
    }
  }

  /** Explicit @Service allowlist; anything else must use role-oriented stereotypes. */
  private static final Set<String> SERVICE_ALLOWLIST = Set.of("execution/audit/SqlExecutionAuditStore.java");

  @Test
  void datasourceHasNoImplicitServiceFacade() throws IOException {
    for (SourceFile source : sources()) {
      if (SERVICE_ALLOWLIST.contains(source.relativePath())) continue;
      assertThat(source.content())
          .as("@Service requires an explicit architecture allowlist: %s", source.relativePath())
          .doesNotContain("@Service");
    }
  }

  @Test
  void controllerGatewayCorridorStaysLimitedToViewMasking() throws IOException {
    for (SourceFile source : sources()) {
      if (!"controller".equals(source.topLevelPackage())) continue;
      if (!source.content().contains("io.yak.ops.business.datasource.gateway.")) continue;
      assertThat(source.relativePath())
          .as("Controller gateway edge expanded in %s", source.relativePath())
          .isEqualTo("controller/v1/mapper/DataSourceViewMapper.java");
      assertThat(source.content()).contains("DataSourcePluginGateway");
    }
  }

  private List<SourceFile> sources() throws IOException {
    assertThat(Files.isDirectory(SOURCE_ROOT))
        .as("Datasource source root must exist: %s", SOURCE_ROOT)
        .isTrue();
    List<SourceFile> result = new ArrayList<>();
    try (var paths = Files.walk(SOURCE_ROOT)) {
      for (Path path : paths.filter(Files::isRegularFile).filter(this::isJava).toList()) {
        Path relative = SOURCE_ROOT.relativize(path);
        if (relative.getNameCount() < 2) continue;
        result.add(
            new SourceFile(
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

  private Set<String> internalImports(String source) {
    Set<String> targets = new LinkedHashSet<>();
    for (String rawLine : source.lines().toList()) {
      String line = rawLine.trim();
      if (!line.startsWith("import ")) continue;
      String imported = line.substring("import ".length()).trim();
      if (imported.startsWith("static ")) imported = imported.substring("static ".length()).trim();
      if (imported.endsWith(";")) imported = imported.substring(0, imported.length() - 1);
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
      if (graph.containsKey(target)) {
        visit(target, graph, visiting, visited, path, label);
      }
    }
    path.removeLast();
    visiting.remove(node);
    visited.add(node);
  }

  private record SourceFile(String relativePath, String topLevelPackage, String content) {}
}
