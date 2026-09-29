package io.yak.ops.business.resource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceFlywayContractTest {

  @Test
  void resourceNamespaceUsesItsProjectScopedBaseline() throws IOException {
    assertThat(sqlFiles(migrationRoot()))
        .containsExactly("V1__init_resource_management.sql");

    String baseline = Files.readString(
        migrationRoot().resolve("V1__init_resource_management.sql"));
    assertThat(baseline)
        .contains("project_id BIGINT NOT NULL")
        .contains("uk_yak_resource_project_parent_name")
        .contains("idx_yak_resource_project_path")
        .contains("idx_yak_resource_project_parent_type")
        .doesNotContain("project_id BIGINT NOT NULL DEFAULT");
  }

  private List<String> sqlFiles(Path root) throws IOException {
    try (var paths = Files.list(root)) {
      return paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.endsWith(".sql"))
          .sorted()
          .toList();
    }
  }

  private Path migrationRoot() {
    Path local = Path.of("src/main/resources/db/migration/yak-resource");
    if (Files.isDirectory(local)) return local;
    return Path.of(
        "data-ops-business", "data-ops-business-resource", "src", "main", "resources", "db",
        "migration", "yak-resource");
  }
}
