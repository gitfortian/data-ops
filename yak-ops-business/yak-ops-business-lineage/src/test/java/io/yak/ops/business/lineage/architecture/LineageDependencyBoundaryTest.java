package io.yak.ops.business.lineage.architecture;

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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Source-level guard for the final Lineage package roles and dependency boundaries. */
class LineageDependencyBoundaryTest {

  private static final String BASE = "io.yak.ops.business.lineage";

  private static final Map<String, Set<String>> ALLOWED_TOP_LEVEL_DEPENDENCIES =
      Map.ofEntries(
          Map.entry("controller", Set.of("domain", "query", "registration")),
          Map.entry("query", Set.of("domain", "repository")),
          Map.entry("registration", Set.of("domain", "repository")),
          Map.entry("maintenance", Set.of("repository")),
          Map.entry("analysis", Set.of("domain")),
          Map.entry("repository", Set.of("dao", "domain")),
          Map.entry("dao", Set.of("config")),
          Map.entry("domain", Set.of()),
          Map.entry("config", Set.of()));

  private static final Set<String> STABLE_SERVICE_TYPES =
      Set.of(
          "maintenance/LineageMaintenanceService.java",
          "query/LineageQueryService.java",
          "registration/LineageRegistrationService.java");

  private static final Set<String> ANALYSIS_ROLE_TYPES =
      Set.of("sql/SqlProjectionLineageAnalyzer.java");

  private static final Map<String, Set<String>> EXTERNAL_BUSINESS_CORRIDORS =
      Map.of(
          "config/ConditionalOnLineagePersistence.java",
          Set.of("io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled"),
          "config/LineagePersistenceConfiguration.java",
          Set.of(
              "io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration",
              "io.yak.ops.business.datasource.config.DataSourceProperties"));

  private static final Pattern ROOT_TYPE_REFERENCE =
      Pattern.compile("\\bio\\.yak\\.ops\\.business\\.lineage\\.[A-Z][A-Za-z0-9_$]*");

  @Test
  void rootPackageMustRemainEmpty() throws IOException {
    try (Stream<Path> files = Files.list(productionRoot())) {
      assertThat(
              files.filter(Files::isRegularFile)
                  .filter(path -> path.toString().endsWith(".java"))
                  .toList())
          .isEmpty();
    }
  }

  @Test
  void activeTopLevelPackagesAreExact() throws IOException {
    Set<String> actual;
    try (Stream<Path> paths = Files.list(productionRoot())) {
      actual =
          paths.filter(Files::isDirectory)
              .map(path -> path.getFileName().toString())
              .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet());
    assertThat(Files.exists(productionRoot().resolve("service"))).isFalse();
  }

  @Test
  void stableServiceSetIsExplicit() throws IOException {
    Set<String> actual = new LinkedHashSet<>();
    Path root = productionRoot();
    for (Path file : javaFiles(root)) {
      if (Files.readString(file, StandardCharsets.UTF_8).contains("@Service")) {
        actual.add(normalize(root.relativize(file)));
      }
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(STABLE_SERVICE_TYPES);
  }

  @Test
  void analysisRoleSetIsExplicit() throws IOException {
    Path root = productionRoot().resolve("analysis");
    Set<String> actual;
    try (Stream<Path> files = Files.walk(root)) {
      actual =
          files.filter(path -> path.toString().endsWith(".java"))
              .map(root::relativize)
              .map(this::normalize)
              .collect(Collectors.toSet());
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(ANALYSIS_ROLE_TYPES);
  }

  @Test
  void importsFollowDeclaredDependencyGraph() throws IOException {
    for (Dependency dependency : packageDependencies()) {
      assertThat(ALLOWED_TOP_LEVEL_DEPENDENCIES.get(dependency.sourcePackage()))
          .as("%s imports %s", dependency.relativePath(), dependency.importedType())
          .contains(dependency.targetPackage());
    }
  }

  @Test
  void declaredAndActualGraphsRemainAcyclic() throws IOException {
    assertAcyclic(ALLOWED_TOP_LEVEL_DEPENDENCIES, "declared graph");
    Map<String, Set<String>> actual = new LinkedHashMap<>();
    ALLOWED_TOP_LEVEL_DEPENDENCIES.keySet()
        .forEach(packageName -> actual.put(packageName, new LinkedHashSet<>()));
    for (Dependency dependency : packageDependencies()) {
      actual.get(dependency.sourcePackage()).add(dependency.targetPackage());
    }
    assertAcyclic(actual, "actual graph");
  }

  @Test
  void externalBusinessImportsStayInConfigCorridor() throws IOException {
    assertThat(externalBusinessImports())
        .containsExactlyInAnyOrderEntriesOf(EXTERNAL_BUSINESS_CORRIDORS);
  }

  @Test
  void applicationRolesCannotReachPersistenceImplementation() throws IOException {
    for (String packageName : Set.of("query", "registration", "maintenance")) {
      assertNoImports(
          packageName,
          BASE + ".dao.",
          BASE + ".config.",
          BASE + ".controller.",
          "com.baomidou.mybatisplus",
          "JdbcTemplate");
    }
  }

  @Test
  void domainRemainsFrameworkAndInfrastructureFree() throws IOException {
    assertNoImports(
        "domain",
        "org.springframework",
        "com.baomidou.mybatisplus",
        BASE + ".controller.",
        BASE + ".query.",
        BASE + ".registration.",
        BASE + ".maintenance.",
        BASE + ".repository.",
        BASE + ".dao.",
        BASE + ".analysis.");
  }

  @Test
  void repositoryAndDaoDoNotPointBackUpward() throws IOException {
    assertNoImports("repository", BASE + ".controller.", BASE + ".query.",
        BASE + ".registration.", BASE + ".maintenance.");
    assertNoImports("dao", BASE + ".controller.", BASE + ".query.",
        BASE + ".registration.", BASE + ".maintenance.", BASE + ".analysis.",
        BASE + ".repository.", BASE + ".domain.");
  }

  @Test
  void rootTypesCannotReturnAcrossProductionSources() throws IOException {
    try (Stream<Path> files = Files.walk(repositoryRoot())) {
      for (Path source : files.filter(this::isProductionJava).toList()) {
        String value = Files.readString(source, StandardCharsets.UTF_8);
        assertThat(ROOT_TYPE_REFERENCE.matcher(value).find())
            .as("Root-package Lineage type reference in %s", normalize(source))
            .isFalse();
      }
    }
  }

  @Test
  void broadBusinessBucketsCannotAppear() {
    for (String forbidden : Set.of("service", "common", "helper", "utils", "base")) {
      assertThat(Files.exists(productionRoot().resolve(forbidden))).as(forbidden).isFalse();
    }
  }

  private List<Dependency> packageDependencies() throws IOException {
    Path root = productionRoot();
    List<Dependency> result = new ArrayList<>();
    for (Path source : javaFiles(root)) {
      Path relative = root.relativize(source);
      if (relative.getNameCount() < 2) continue;
      String sourcePackage = relative.getName(0).toString();
      for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
        String imported = importedType(line);
        if (imported == null || !imported.startsWith(BASE + ".")) continue;
        String suffix = imported.substring((BASE + ".").length());
        int separator = suffix.indexOf('.');
        String targetPackage = separator < 0 ? "root" : suffix.substring(0, separator);
        if (!sourcePackage.equals(targetPackage)) {
          result.add(new Dependency(
              sourcePackage, targetPackage, imported, normalize(relative)));
        }
      }
    }
    return result;
  }

  private Map<String, Set<String>> externalBusinessImports() throws IOException {
    Path root = productionRoot();
    Map<String, Set<String>> result = new LinkedHashMap<>();
    for (Path source : javaFiles(root)) {
      for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
        String imported = importedType(line);
        if (imported == null
            || !imported.startsWith("io.yak.ops.business.")
            || imported.startsWith(BASE + ".")) {
          continue;
        }
        result.computeIfAbsent(normalize(root.relativize(source)), ignored -> new LinkedHashSet<>())
            .add(imported);
      }
    }
    return result;
  }

  private String importedType(String line) {
    String value = line.trim();
    String prefix;
    if (value.startsWith("import static ")) {
      prefix = "import static ";
    } else if (value.startsWith("import ")) {
      prefix = "import ";
    } else {
      return null;
    }
    return value.endsWith(";") ? value.substring(prefix.length(), value.length() - 1) : null;
  }

  private void assertNoImports(String packageName, String... forbiddenTokens) throws IOException {
    Path root = productionRoot().resolve(packageName);
    if (!Files.isDirectory(root)) return;
    for (Path file : javaFiles(root)) {
      String imports =
          Files.readString(file, StandardCharsets.UTF_8).lines()
              .filter(line -> line.startsWith("import "))
              .collect(Collectors.joining("\n"));
      for (String token : forbiddenTokens) {
        assertThat(imports).as("%s in %s", token, normalize(file)).doesNotContain(token);
      }
    }
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
    outgoing.forEach((node, degree) -> { if (degree == 0) ready.add(node); });
    int visited = 0;
    while (!ready.isEmpty()) {
      String node = ready.removeFirst();
      visited++;
      for (String dependent : reverse.getOrDefault(node, Set.of())) {
        int degree = outgoing.compute(dependent, (ignored, value) -> value - 1);
        if (degree == 0) ready.add(dependent);
      }
    }
    assertThat(visited).as(label).isEqualTo(nodes.size());
  }

  private Set<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(path -> path.toString().endsWith(".java"))
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
  }

  private boolean isProductionJava(Path path) {
    String normalized = normalize(path);
    return normalized.endsWith(".java")
        && normalized.contains("/src/main/java/")
        && !normalized.contains("/target/");
  }

  private Path repositoryRoot() {
    Path current = Paths.get(".").toAbsolutePath().normalize();
    if (Files.isDirectory(current.resolve("yak-ops-business"))) return current;
    Path candidate = current.resolve("../..").normalize();
    assertThat(Files.isDirectory(candidate.resolve("yak-ops-business"))).isTrue();
    return candidate;
  }

  private Path productionRoot() {
    Path local = Paths.get("src/main/java/io/yak/ops/business/lineage");
    if (Files.isDirectory(local)) return local;
    Path repositoryRelative = Paths.get(
        "yak-ops-business", "yak-ops-business-lineage", "src/main/java/io/yak/ops/business/lineage");
    assertThat(Files.isDirectory(repositoryRelative)).isTrue();
    return repositoryRelative;
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }

  private record Dependency(
      String sourcePackage, String targetPackage, String importedType, String relativePath) {}
}
