package io.yak.ops.business.resource.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceFlywayContractTest {

  @Test
  void resourceNamespaceContainsExpandAndContractProjectScopeMigrations() throws IOException {
    assertThat(sqlFiles(migrationRoot()))
        .containsExactly(
            "V1__init_resource_management.sql",
            "V2__expand_project_scope.sql",
            "V3__contract_project_scope.sql");

    String expand = Files.readString(migrationRoot().resolve("V2__expand_project_scope.sql"));
    assertThat(expand)
        .contains("ADD COLUMN project_id BIGINT NULL")
        .contains("uk_yak_resource_project_parent_name")
        .contains("idx_yak_resource_project_path")
        .contains("idx_yak_resource_project_parent_type");

    String contract = Files.readString(migrationRoot().resolve("V3__contract_project_scope.sql"));
    assertThat(contract)
        .contains("ALTER TABLE yak_ops_resource")
        .contains("MODIFY COLUMN project_id BIGINT NOT NULL")
        .contains("performs no implicit backfill")
        .doesNotContain("UPDATE yak_ops_resource")
        .doesNotContain("project_id = 1");
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
        "yak-ops-business", "yak-ops-business-resource", "src", "main", "resources", "db",
        "migration", "yak-resource");
  }
}
