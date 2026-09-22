package io.yak.ops.plugin.database.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards the control-plane/runtime boundary: vendor Elasticsearch SDKs belong to Link-Up plugins. */
class ElasticsearchSdkIsolationTest {

  @Test
  void productionSourcesDoNotImportElasticsearchVendorSdks() throws IOException {
    Path root = sourceRoot();
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = Files.readString(file);
        assertThat(source)
            .as("%s must remain SDK-free", file.getFileName())
            .doesNotContain("import org.elasticsearch.")
            .doesNotContain("import co.elastic.clients.")
            .doesNotContain("org.elasticsearch.client")
            .doesNotContain("co.elastic.clients");
      }
    }
  }

  @Test
  void modulePomDoesNotDeclareElasticsearchVendorArtifacts() throws IOException {
    String pom = Files.readString(moduleRoot().resolve("pom.xml"));
    assertThat(pom)
        .doesNotContain("org.elasticsearch")
        .doesNotContain("co.elastic.clients")
        .doesNotContain("elasticsearch-java")
        .doesNotContain("elasticsearch-rest-high-level-client");
  }

  private Path moduleRoot() {
    Path local = Path.of("").toAbsolutePath().normalize();
    if (Files.isDirectory(local.resolve("src/main/java"))) return local;

    Path repository = Path.of(
        "yak-ops-plugins",
        "yak-ops-plugin-datasource",
        "yak-ops-plugin-datasource-elasticsearch");
    if (Files.isDirectory(repository.resolve("src/main/java"))) return repository;

    throw new IllegalStateException("Elasticsearch datasource module root is unavailable");
  }

  private Path sourceRoot() {
    return moduleRoot().resolve("src/main/java");
  }
}
