package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.offline.execution.OfflineFanOutExecutionCoordinator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class OfflineFanOutArchitectureTest {

  @Test
  void fanOutCoordinatorRemainsAnInternalComponent() {
    assertThat(OfflineFanOutExecutionCoordinator.class.getAnnotation(Component.class)).isNotNull();
    assertThat(OfflineFanOutExecutionCoordinator.class.getAnnotation(Service.class)).isNull();
  }

  @Test
  void nonExecutionProductionCodeDoesNotImportFanOutCoordinator() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (relative.startsWith("execution/")) {
          continue;
        }
        assertThat(Files.readString(file))
            .as("%s must enter FAN_OUT through OfflineJobExecutionService", relative)
            .doesNotContain(
                "import io.yak.ops.business.sync.offline.execution.OfflineFanOutExecutionCoordinator");
      }
    }
  }

  private Path productionRoot() {
    Path moduleRoot = Path.of("src/main/java/io/yak/ops/business/sync/offline");
    if (Files.isDirectory(moduleRoot)) {
      return moduleRoot;
    }
    return Path.of(
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
  }
}
