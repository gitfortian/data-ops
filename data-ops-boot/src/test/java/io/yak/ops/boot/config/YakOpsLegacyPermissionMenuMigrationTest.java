package io.yak.ops.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class YakOpsLegacyPermissionMenuMigrationTest {

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
  void reconciliationPreservesLegacyLeafPermissionMenuBindings() throws Exception {
    ClassPathResource resource =
        new ClassPathResource("yak-security/db/migration/V2__boot_security_baseline.sql");
    String sql =
        section(resource.getContentAsString(StandardCharsets.UTF_8),
                "V2006__reconcile_menu_permission_catalog.sql");

    assertThat(sql)
        .contains("'task:batch:read'")
        .contains("'datasource:create'")
        .contains("'datasource:update'")
        .contains("'datasource:delete'")
        .contains("'datasource:test'")
        .contains("'job:view'")
        .contains("'job:execute'")
        .contains("'resource:data-source:read'")
        .contains("'resource:view'")
        .contains("'resource:upload'")
        .contains("WHEN 'datasource:create' THEN 'data-source'")
        .contains("WHEN 'job:view' THEN 'batch-link-up'")
        .contains("WHEN 'resource:view' THEN 'resource-management'")
        .contains("permission_row.menu_code IS NOT NULL")
        .doesNotContain("DELETE FROM yak_security_permission")
        .doesNotContain("DELETE FROM yak_security_menu");
  }
}
