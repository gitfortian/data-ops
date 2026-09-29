package io.yak.ops.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class YakOpsSecurityMigrationVersionCompatibilityTest {

  @Test
  void preservesPreviouslyAppliedSecurityMigrationVersions() {
    Path migrations = Path.of("src/main/resources/yak-security/db/migration");
    if (!Files.isDirectory(migrations)) {
      migrations =
          Path.of(
              "data-ops-boot", "src", "main", "resources", "yak-security", "db", "migration");
    }

    assertThat(migrations.resolve("V2040__reconcile_datasource_permission_codes.sql")).isRegularFile();
    assertThat(migrations.resolve("V2041__rename_metric_service_menu.sql")).isRegularFile();
    assertThat(migrations.resolve("V2042__register_metric_publish_permission.sql")).isRegularFile();
    assertThat(migrations.resolve("V2043__register_dataset_query_permission.sql")).isRegularFile();
    assertThat(migrations.resolve("V2044__register_data_service_invoke_permission.sql")).isRegularFile();
  }
}
