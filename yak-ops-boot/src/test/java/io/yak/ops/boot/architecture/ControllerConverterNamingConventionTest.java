package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ControllerConverterNamingConventionTest {

  @Test
  void controllerAdaptersDoNotUsePersistenceMapperNaming() throws IOException {
    Path businessRoot = businessRoot();
    try (Stream<Path> paths = Files.walk(businessRoot)) {
      List<String> violations = paths
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> normalized(path).contains("/src/main/java/"))
          .filter(path -> normalized(path).contains("/controller/"))
          .filter(path -> normalized(path).contains("/mapper/")
              || path.getFileName().toString().endsWith("Mapper.java"))
          .map(path -> businessRoot.relativize(path).toString().replace('\\', '/'))
          .sorted()
          .toList();

      assertThat(violations)
          .as("Mapper is reserved for persistence/MyBatis; controller transformations belong in converter")
          .isEmpty();
    }
  }

  private static String normalized(Path path) {
    return path.toString().replace('\\', '/');
  }

  private static Path businessRoot() {
    Path root = Path.of("yak-ops-business");
    if (Files.isDirectory(root)) return root;
    Path parent = Path.of("..", "yak-ops-business");
    if (Files.isDirectory(parent)) return parent;
    throw new IllegalStateException("Cannot locate yak-ops-business from " + Path.of("").toAbsolutePath());
  }
}
