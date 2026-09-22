package io.yak.ops.business.lineage.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Code conventions that make Lineage roles visible from the source tree. */
class LineageCodeStyleConventionTest {

  private static final Pattern PUBLIC_TOP_LEVEL_TYPE =
      Pattern.compile("(?m)^public\\s+(?:final\\s+|sealed\\s+|non-sealed\\s+|abstract\\s+)?(?:class|interface|record|enum|@interface)\\s+([A-Za-z0-9_$]+)");

  @Test
  void javaSourcesUseStableTextConventions() throws IOException {
    for (Path source : allJavaFiles()) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      assertThat(text).as(normalize(source)).endsWith("\n").doesNotContain("\t");
      for (String line : text.split("\n", -1)) {
        assertThat(line).as(normalize(source)).doesNotMatch(".*[ \\t]+$");
      }
      boolean wildcardImport =
          text.lines().anyMatch(line ->
              (line.startsWith("import ") || line.startsWith("import static "))
                  && line.endsWith(".*;"));
      assertThat(wildcardImport).as(normalize(source)).isFalse();
    }
  }

  @Test
  void packageDeclarationsMatchSourcePaths() throws IOException {
    assertPackages(mainJavaRoot(), "io.yak.ops.business.lineage");
    assertPackages(testJavaRoot(), "io.yak.ops.business.lineage");
  }

  @Test
  void publicProductionTypeMatchesItsFileName() throws IOException {
    for (Path source : javaFiles(mainJavaRoot())) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      Matcher matcher = PUBLIC_TOP_LEVEL_TYPE.matcher(text);
      List<String> publicTypes = new ArrayList<>();
      while (matcher.find()) publicTypes.add(matcher.group(1));
      String filename = source.getFileName().toString();
      String expected = filename.substring(0, filename.length() - ".java".length());
      assertThat(publicTypes).as(normalize(source)).containsExactly(expected);
    }
  }

  @Test
  void roleNamesStayInTheirOwningPackages() throws IOException {
    Path root = mainJavaRoot();
    for (Path source : javaFiles(root)) {
      String relative = normalize(root.relativize(source));
      String filename = source.getFileName().toString();
      if (filename.endsWith("Controller.java")) assertThat(relative).startsWith("controller/");
      if (filename.endsWith("Service.java")) {
        assertThat(relative)
            .isIn(
                "query/LineageQueryService.java",
                "registration/LineageRegistrationService.java",
                "maintenance/LineageMaintenanceService.java");
      }
      if (filename.endsWith("Reader.java")) assertThat(relative).startsWith("query/");
      if (filename.endsWith("Registrar.java") || filename.endsWith("DraftFactory.java")) {
        assertThat(relative).startsWith("registration/");
      }
      if (filename.endsWith("Coordinator.java") || filename.endsWith("Guard.java")) {
        assertThat(relative).startsWith("maintenance/");
      }
      if (filename.endsWith("Analyzer.java")) assertThat(relative).startsWith("analysis/");
      if (filename.endsWith("Repository.java")
          || filename.endsWith("RepositoryAdapter.java")
          || filename.endsWith("Codec.java")) {
        assertThat(relative).startsWith("repository/");
      }
      if (filename.endsWith("Dao.java") || filename.endsWith("DaoImpl.java")) {
        assertThat(relative).startsWith("dao/");
      }
      if (filename.endsWith("Mapper.java")) {
        assertThat(relative.startsWith("dao/mapper/")
                || relative.startsWith("controller/v1/converter/"))
            .as(relative)
            .isTrue();
      }
      if (filename.endsWith("Configuration.java") || filename.startsWith("ConditionalOn")) {
        assertThat(relative).startsWith("config/");
      }
    }
  }

  private void assertPackages(Path root, String basePackage) throws IOException {
    for (Path source : javaFiles(root)) {
      Path relative = root.relativize(source);
      Path parent = relative.getParent();
      String suffix = parent == null ? "" : "." + normalize(parent).replace('/', '.');
      String expected = "package " + basePackage + suffix + ";";
      String firstNonBlank = Files.readString(source, StandardCharsets.UTF_8).lines()
          .filter(line -> !line.isBlank()).findFirst().orElse("");
      assertThat(firstNonBlank).as(normalize(source)).isEqualTo(expected);
    }
  }

  private Set<Path> allJavaFiles() throws IOException {
    Set<Path> result = new LinkedHashSet<>();
    result.addAll(javaFiles(mainJavaRoot()));
    result.addAll(javaFiles(testJavaRoot()));
    return result;
  }

  private Set<Path> javaFiles(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(path -> path.toString().endsWith(".java"))
          .collect(Collectors.toCollection(LinkedHashSet::new));
    }
  }

  private Path mainJavaRoot() {
    Path local = Paths.get("src/main/java/io/yak/ops/business/lineage");
    if (Files.isDirectory(local)) return local;
    return Paths.get("yak-ops-business", "yak-ops-business-lineage",
        "src/main/java/io/yak/ops/business/lineage");
  }

  private Path testJavaRoot() {
    Path local = Paths.get("src/test/java/io/yak/ops/business/lineage");
    if (Files.isDirectory(local)) return local;
    return Paths.get("yak-ops-business", "yak-ops-business-lineage",
        "src/test/java/io/yak/ops/business/lineage");
  }

  private String normalize(Path path) {
    return path.toString().replace('\\', '/');
  }
}
