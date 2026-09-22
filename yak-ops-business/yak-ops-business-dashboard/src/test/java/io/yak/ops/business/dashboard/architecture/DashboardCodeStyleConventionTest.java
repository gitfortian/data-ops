package io.yak.ops.business.dashboard.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DashboardCodeStyleConventionTest {

  @Test
  void productionJavaDoesNotUseWildcardImportsOrConsoleOutput() throws IOException {
    for (Path source : productionJavaFiles()) {
      String text = Files.readString(source);
      assertThat(text).as(source.toString())
          .doesNotContain("import java.*;")
          .doesNotContain("import org.springframework.*;")
          .doesNotContain("System.out.")
          .doesNotContain("System.err.")
          .doesNotContain("BeanUtils.copyProperties");
      assertThat(text.lines().filter(line -> line.startsWith("import ") && line.contains(".*;")).count())
          .as("wildcard imports in " + source)
          .isZero();
    }
  }

  @Test
  void productionUsesConstructorInjectionInsteadOfAutowiredFields() throws IOException {
    for (Path source : productionJavaFiles()) {
      String text = Files.readString(source);
      List<String> lines = text.lines().toList();
      for (int index = 0; index < lines.size(); index++) {
        if (!lines.get(index).trim().equals("@Autowired")) continue;
        String next = nextNonBlank(lines, index + 1);
        assertThat(next)
            .as("@Autowired must not annotate a field in " + source)
            .contains("(");
      }
    }
  }

  @Test
  void domainRemainsFrameworkFree() throws IOException {
    Path domain = productionRoot().resolve("domain");
    try (Stream<Path> paths = Files.walk(domain)) {
      for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String text = Files.readString(source);
        assertThat(text).as(source.toString())
            .doesNotContain("org.springframework")
            .doesNotContain("com.baomidou.mybatisplus")
            .doesNotContain("jakarta.validation")
            .doesNotContain("com.fasterxml.jackson")
            .doesNotContain("io.yak.ops.business.dashboard.dao")
            .doesNotContain("io.yak.ops.business.dashboard.controller")
            .doesNotContain("io.yak.ops.business.analysis")
            .doesNotContain("io.yak.ops.business.lineage");
      }
    }
  }

  @Test
  void repositoryCodeStyleHasSingleRepositoryWideSource() {
    Path root = repositoryRoot();
    assertThat(root.resolve("CODE_STYLE.md")).exists();
    assertThat(moduleRoot().resolve("CODE_STYLE.md")).doesNotExist();
  }

  private List<Path> productionJavaFiles() throws IOException {
    try (Stream<Path> paths = Files.walk(productionRoot())) {
      return paths.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }

  private String nextNonBlank(List<String> lines, int start) {
    for (int index = start; index < lines.size(); index++) {
      if (!lines.get(index).isBlank()) return lines.get(index).trim();
    }
    return "";
  }

  private Path productionRoot() {
    return moduleRoot().resolve("src/main/java/io/yak/ops/business/dashboard");
  }

  private Path moduleRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/dashboard");
    if (Files.isDirectory(local)) return Path.of(".");
    return Path.of("yak-ops-business", "yak-ops-business-dashboard");
  }

  private Path repositoryRoot() {
    Path module = moduleRoot().toAbsolutePath().normalize();
    if (module.getFileName() != null && module.getFileName().toString().equals("yak-ops-business-dashboard")) {
      Path business = module.getParent();
      if (business != null && business.getParent() != null) return business.getParent();
    }
    return Path.of(".").toAbsolutePath().normalize();
  }
}
