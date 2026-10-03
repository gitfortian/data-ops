package io.yak.framework.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class MenuMigrationIsolationTest {

  @Test
  void consolidatedBaselineMustContainFinalYakSecuritySchemaAndCatalog()
      throws Exception {

    String sql = section(readMigration(
        "yak-security/db/migration/V1__security_framework_baseline.sql"), "V1__init_yak_security.sql");

    assertThat(sql)
        .contains("CREATE TABLE IF NOT EXISTS yak_security_permission")
        .contains("active TINYINT(1) NOT NULL DEFAULT 1")
        .contains("declared TINYINT(1) NOT NULL DEFAULT 0")
        .contains("CREATE TABLE IF NOT EXISTS yak_security_user_project")
        .contains("user_type TINYINT NOT NULL DEFAULT 0")
        .contains("CREATE TABLE IF NOT EXISTS yak_security_menu")
        .contains("CREATE TABLE IF NOT EXISTS yak_security_role_menu")
        .contains("INSERT INTO yak_security_permission")
        .contains("INSERT INTO yak_security_menu")
        .contains("'security:root'")
        .contains("'security:user:create'")
        .contains("'security:user:reset-password'")
        .contains("'security:role:assign'")
        .contains("'security:permission:import'")
        .contains("'system-users'")
        .contains("'system-operation-logs'")
        .contains("INSERT IGNORE INTO yak_security_role_permission")
        .contains("INSERT IGNORE INTO yak_security_role_menu")
        .doesNotContain("ALTER TABLE yak_security_permission")
        .doesNotContain("ALTER TABLE yak_security_user_project")
        .doesNotContain("'task:batch:read'")
        .doesNotContain("'workflow:definition:read'")
        .doesNotContain("'resource:data-source:read'")
        .doesNotContain("'knowledge-management'");
  }

  private static String readMigration(String path) throws Exception {
    ClassPathResource resource = new ClassPathResource(path);
    return resource.getContentAsString(StandardCharsets.UTF_8);
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
