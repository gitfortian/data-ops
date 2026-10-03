package io.yak.framework.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PermissionMenuMigrationTest {

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
  @Test
  void migrationLinksSystemActionsToMenusBeforeBusinessCatalogs() throws Exception {
    ClassPathResource resource =
        new ClassPathResource("yak-security/db/migration/V1__security_framework_baseline.sql");
    String sql =
        section(resource.getContentAsString(StandardCharsets.UTF_8),
                "V2__link_permissions_to_menus.sql");

    assertThat(sql)
        .contains("ADD COLUMN menu_code")
        .contains("SET menu_code='system-users'")
        .contains("permission_code LIKE 'security:user:%'")
        .contains("SET menu_code='system-roles'")
        .contains("SET menu_code='system-permissions'")
        .contains("INSERT IGNORE INTO yak_security_role_menu")
        .contains("permission_row.menu_code")
        .doesNotContain("task:batch")
        .doesNotContain("workflow:definition")
        .doesNotContain("resource:data-source")
        .doesNotContain("quality:rule");
  }
}
