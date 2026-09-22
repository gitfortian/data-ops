package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataServiceApiCallMenuMigrationTest {

  @Test
  void accessPageKeepsStableIdentityWhileUsingCallerCentricName() throws IOException {
    String migration = Files.readString(migrationSource());

    assertThat(migration)
        .contains("menu_name = 'API 调用'")
        .contains("menu_code = 'data-service-access'")
        .contains("permission_name = '管理数据服务 API 调用'")
        .contains("permission_code = 'data-service:access'")
        .contains("调用方")
        .doesNotContain("INSERT INTO yak_security_menu");
  }

  private Path migrationSource() {
    Path local = Path.of(
        "src/main/resources/yak-security/db/migration/"
            + "V2008__rename_data_service_access_to_api_call.sql");
    if (Files.isRegularFile(local)) return local;
    return Path.of(
        "yak-ops-boot", "src", "main", "resources", "yak-security", "db", "migration",
        "V2008__rename_data_service_access_to_api_call.sql");
  }
}
