package io.yak.ops.business.development.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Prevents Data Development from coupling back to removed datasource transport/service APIs. */
class DataDevelopmentDatasourceBoundaryTest {

  @Test
  void datasourceIntegrationUsesCurrentBusinessBoundary() throws IOException {
    try (Stream<Path> paths = Files.walk(productionRoot())) {
      for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String text = Files.readString(source);
        assertThat(text)
            .as(relative(source))
            .doesNotContain("io.yak.ops.business.datasource.service.")
            .doesNotContain("io.yak.ops.common.bean.vo.datasource.DataSourceCatalog");
      }
    }
  }

  private String relative(Path source) {
    return productionRoot().relativize(source).toString().replace('\\', '/');
  }

  private Path productionRoot() {
    Path local = Path.of("src/main/java/io/yak/ops/business/development");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "yak-ops-business", "yak-ops-business-data-development", "src", "main", "java",
        "io", "yak", "ops", "business", "development");
  }
}
