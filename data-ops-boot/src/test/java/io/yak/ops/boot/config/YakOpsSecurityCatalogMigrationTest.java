package io.yak.ops.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class YakOpsSecurityCatalogMigrationTest {

  @Test
  void baselineOwnsCurrentBusinessPermissionsMenusAndBackfills()
      throws Exception {

    ClassPathResource resource =
        new ClassPathResource("yak-security/db/migration/V2__boot_security_baseline.sql");
    String sql =
        section(resource.getContentAsString(StandardCharsets.UTF_8),
                "V1000__init_yak_ops_security_catalog.sql");

    assertThat(sql)
        .contains("INSERT INTO yak_security_permission")
        .contains("INSERT INTO yak_security_menu")
        .contains("'task:batch:read'")
        .contains("'task:batch:create'")
        .contains("'datasource:create'")
        .contains("'datasource:update'")
        .contains("'datasource:delete'")
        .contains("'resource:data-source:read'")
        .contains("'resource:view'")
        .contains("('data-source', '数据源管理', 'resources'")
        .contains("('resource-management', '文件资源', 'resources'")
        .contains("permission_row.menu_code IS NOT NULL")
        .contains("ON DUPLICATE KEY UPDATE")
        .doesNotContain("'system-users'")
        .doesNotContain("INSERT IGNORE")
        .doesNotContain("INSERT INTO yak_security_role_permission");
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
