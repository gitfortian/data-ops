package io.yak.ops.business.job.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class JobRoleConventionTest {

  private static final Set<String> SERVICE_ALLOWLIST = Set.of(
      "task/TaskExecutionGateway.java",
      "environment/SystemEnvVarService.java");

  @Test
  void serviceAnnotationIsReservedForStableApplicationFacades() throws IOException {
    Set<String> actual = new LinkedHashSet<>();
    for (Path source : productionJavaFiles()) {
      String text = Files.readString(source);
      if (text.contains("\n@Service\n")) actual.add(relative(source));
    }
    assertThat(actual).containsExactlyInAnyOrderElementsOf(SERVICE_ALLOWLIST);
  }

  @Test
  void professionalRolesUseComponentOrPlainObjects() throws IOException {
    assertComponent("discovery/InMemoryTaskRegistry.java");
    assertComponent("runtime/TaskExecutionContextFactory.java");
    assertComponent("adapter/plugin/SqlTaskExecutorAdapter.java");
    assertComponent("adapter/plugin/PythonTaskExecutorAdapter.java");
    assertComponent("adapter/plugin/JavaTaskExecutorAdapter.java");
    assertComponent("adapter/plugin/ShellTaskExecutorAdapter.java");
  }

  @Test
  void legacySyncCorridorCannotBecomeProductionSpringBeans() throws IOException {
    for (String path : Set.of(
        "task/SyncTaskRunner.java",
        "task/SyncTaskExecution.java",
        "task/SyncTaskExecutorAdapter.java")) {
      String text = Files.readString(productionRoot().resolve(path));
      assertThat(text)
          .as(path)
          .contains("@Deprecated")
          .doesNotContain("@Service")
          .doesNotContain("@Component");
    }
  }

  private void assertComponent(String path) throws IOException {
    String text = Files.readString(productionRoot().resolve(path));
    assertThat(text).as(path).contains("@Component").doesNotContain("@Service");
  }

  private java.util.List<Path> productionJavaFiles() throws IOException {
    try (Stream<Path> paths = Files.walk(productionRoot())) {
      return paths.filter(path -> path.toString().endsWith(".java")).toList();
    }
  }

  private String relative(Path source) {
    return productionRoot().relativize(source).toString().replace('\\', '/');
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
}
