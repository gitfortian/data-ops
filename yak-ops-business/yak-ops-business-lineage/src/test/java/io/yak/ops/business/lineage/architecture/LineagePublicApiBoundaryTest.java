package io.yak.ops.business.lineage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.lineage.analysis.sql.SqlProjectionLineageAnalyzer;
import io.yak.ops.business.lineage.maintenance.LineageMaintenanceService;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.lineage.registration.LineageRegistrationService;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Locks the Lineage types that neighboring business modules may compile against. */
class LineagePublicApiBoundaryTest {

  private static final String BASE = "io.yak.ops.business.lineage";

  private static final Set<String> EXPORTED_TYPE_ROOTS =
      Set.of(
          BASE + ".analysis.sql.SqlProjectionLineageAnalyzer",
          BASE + ".domain.LineageAsset",
          BASE + ".domain.LineageAssetType",
          BASE + ".domain.LineageDirection",
          BASE + ".domain.LineageGraph",
          BASE + ".domain.LineageRelation",
          BASE + ".domain.LineageRelationType",
          BASE + ".maintenance.LineageMaintenanceService",
          BASE + ".query.LineageQueryService",
          BASE + ".registration.LineageRegistrationService");

  private static final Set<Class<?>> STABLE_ENTRY_TYPES =
      Set.of(
          SqlProjectionLineageAnalyzer.class,
          LineageMaintenanceService.class,
          LineageQueryService.class,
          LineageRegistrationService.class);

  private static final Set<String> FORBIDDEN_SIGNATURE_TOKENS =
      Set.of(
          BASE + ".config.",
          BASE + ".controller.",
          BASE + ".dao.",
          BASE + ".repository.",
          BASE + ".domain.LineageAssetDraft",
          BASE + ".domain.LineageRelationDraft");

  @Test
  void neighboringProductionModulesUseOnlyDeclaredPublicTypes() throws IOException {
    for (Map.Entry<String, Set<String>> entry : externalLineageImports().entrySet()) {
      String imported = entry.getKey();
      assertThat(imported).doesNotEndWith(".*");
      assertThat(isDeclaredPublicType(imported))
          .as("Undeclared Lineage API import %s used by %s", imported, entry.getValue())
          .isTrue();
    }
  }

  @Test
  void declaredPublicTypeRootsExistAndArePublic() throws ClassNotFoundException {
    for (String typeName : EXPORTED_TYPE_ROOTS) {
      Class<?> type = Class.forName(typeName);
      assertThat(Modifier.isPublic(type.getModifiers())).as(typeName).isTrue();
    }
  }

  @Test
  void stableEntryMethodsDoNotExposeImplementationTypes() {
    for (Class<?> entryType : STABLE_ENTRY_TYPES) {
      for (Method method : entryType.getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers())) continue;
        String signature = method.toGenericString();
        for (String forbidden : FORBIDDEN_SIGNATURE_TOKENS) {
          assertThat(signature).as(signature).doesNotContain(forbidden);
        }
      }
    }
  }

  private boolean isDeclaredPublicType(String imported) {
    return EXPORTED_TYPE_ROOTS.stream()
        .anyMatch(root -> imported.equals(root) || imported.startsWith(root + "."));
  }

  private Map<String, Set<String>> externalLineageImports() throws IOException {
    Path repository = repositoryRoot();
    String ownModule = "/yak-ops-business/yak-ops-business-lineage/src/main/java/";
    Map<String, Set<String>> result = new LinkedHashMap<>();
    try (Stream<Path> files = Files.walk(repository)) {
      for (Path source : files.filter(this::isProductionJava).toList()) {
        String relative = "/" + normalize(repository.relativize(source));
        if (relative.contains(ownModule)) continue;
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
          String value = line.trim();
          if (!value.startsWith("import ") || !value.endsWith(";")) continue;
          String imported = value.substring("import ".length(), value.length() - 1);
          if (!imported.startsWith(BASE + ".")) continue;
          result.computeIfAbsent(imported, ignored -> new LinkedHashSet<>())
              .add(normalize(repository.relativize(source)));
        }
      }
    }
    return result;
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

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }
}
