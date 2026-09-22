package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataServiceAccessMenuMigrationTest {

  @Test
  void accessPermissionOwnsDedicatedVisiblePage() throws IOException {
    String migration = Files.readString(migrationSource());

    assertThat(migration)
        .contains("'data-service-access', '访问控制'")
        .contains("'/data-service/access'")
        .contains("'data-service:access'")
        .contains("menu_code = 'data-service-access'")
        .contains("yak_security_role_menu")
        .contains("WHEN 'data-service-debug' THEN 30")
        .contains("WHEN 'data-service-overview' THEN 40")
        .contains("WHEN 'data-service-logs' THEN 50");
  }

  private Path migrationSource() {
    Path local = Path.of(
        "src/main/resources/yak-security/db/migration/V2007__register_data_service_access_page.sql");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-boot", "src", "main", "resources", "yak-security", "db", "migration",
        "V2007__register_data_service_access_page.sql");
  }
}
