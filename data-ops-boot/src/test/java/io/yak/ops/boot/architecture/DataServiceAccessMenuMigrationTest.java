package io.yak.ops.boot.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataServiceAccessMenuMigrationTest {

  @Test
  void accessPermissionOwnsDedicatedVisiblePage() throws IOException {
    String migration = migrationSource();

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

  private String migrationSource() throws IOException {
    return section(bootBaseline(), "V2007__register_data_service_access_page.sql");
  }

  private String bootBaseline() throws IOException {
    Path local =
        Path.of("src/main/resources/yak-security/db/migration/V2__boot_security_baseline.sql");
    if (Files.isRegularFile(local)) return Files.readString(local);
    return Files.readString(Path.of(
        "data-ops-boot", "src", "main", "resources", "yak-security", "db", "migration",
        "V2__boot_security_baseline.sql"));
  }
  /**
   * 合并后的单文件按 {@code -- Source: <原路径>} 分段;按文件名取回原迁移的正文,
   * 使原先针对单个文件的断言继续有效。
   */
  private static String section(String sql, String sourceFileName) {
    String[] lines = sql.split("\n");
    int start = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:") && lines[i].trim().endsWith(sourceFileName)) {
        start = i + 1;
        break;
      }
    }
    if (start < 0) throw new IllegalStateException("missing source section: " + sourceFileName);
    StringBuilder body = new StringBuilder();
    for (int i = start; i < lines.length; i++) {
      if (lines[i].startsWith("-- Source:")) break;
      body.append(lines[i]).append('\n');
    }
    return body.toString();
  }
}
