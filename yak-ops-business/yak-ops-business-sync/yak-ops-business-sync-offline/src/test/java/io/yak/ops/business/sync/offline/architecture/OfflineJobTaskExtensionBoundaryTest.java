package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OfflineJobTaskExtensionBoundaryTest {

  private static final Map<String, Set<String>> ALLOWED = Map.of(
      "definition/OfflineSyncTaskProvider.java", Set.of(
          "io.yak.ops.business.job.task.TaskDefinition",
          "io.yak.ops.business.job.task.TaskProvider",
          "io.yak.ops.business.job.task.TaskRegistration",
          "io.yak.ops.business.job.task.TaskVersionSnapshot"),
      "execution/adapter/OfflineSyncTaskExecutor.java", Set.of(
          "io.yak.ops.business.job.task.TaskExecution",
          "io.yak.ops.business.job.task.TaskExecutor",
          "io.yak.ops.business.job.task.TaskVersionSnapshot"));

  @Test
  void offlineEntersJobOnlyThroughDeclaredTaskExtensionContracts() throws IOException {
    try (Stream<Path> paths = Files.walk(productionRoot())) {
      for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = productionRoot().relativize(source).toString().replace('\\', '/');
        for (String line : Files.readAllLines(source)) {
          String trimmed = line.trim();
          if (!trimmed.startsWith("import io.yak.ops.business.job.")) continue;
          String imported = trimmed.substring("import ".length(), trimmed.length() - 1);
          assertThat(ALLOWED.getOrDefault(relative, Set.of()))
              .as("%s must not depend on Job implementation via %s", relative, imported)
              .contains(imported);
        }
      }
    }
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/sync/offline");
    if (Files.isDirectory(local)) return local;
    Path repository = Path.of(
        "yak-ops-business", "yak-ops-business-sync", "yak-ops-business-sync-offline",
        "src", "main", "java", "io", "yak", "ops", "business", "sync", "offline");
    assertThat(Files.isDirectory(repository)).isTrue();
    return repository;
  }
}
