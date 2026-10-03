package io.yak.ops.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class YakOpsPermissionMenuMigrationTest {

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
  void migrationReconcilesVisibleMenusAndQualityActions() throws Exception {
    ClassPathResource resource =
        new ClassPathResource("yak-security/db/migration/V2__boot_security_baseline.sql");
    String sql =
        section(resource.getContentAsString(StandardCharsets.UTF_8),
                "V2006__reconcile_menu_permission_catalog.sql");

    assertThat(sql)
        .contains("WHEN 'quality:monitor:create' THEN 'data-quality-table-config'")
        .contains("WHEN 'quality:monitor:run' THEN 'data-quality-table-config'")
        .contains("WHEN 'quality:template:create' THEN 'data-quality-rule-template'")
        .contains("WHEN 'quality:template:delete' THEN 'data-quality-rule-template'")
        .contains("('data-source', '数据源管理', NULL, '/data-source'")
        .contains("('data-quality-overview', '质量总览', 'data-quality'")
        .contains("('data-quality-table-config', '数据表监控', 'data-quality'")
        .contains("('data-analysis', '数据消费', NULL, NULL")
        .contains("('dashboard', '仪表盘', 'data-analysis'")
        .contains("('dataset-management', '数据集', 'data-analysis'")
        .contains("('data-analysis-lineage', '数据血缘', 'data-analysis'")
        .contains("('digital-screen', '数字化大屏', 'data-analysis'")
        .contains("('workflow', '工作流', NULL, NULL")
        .contains("('workflow-definition', '工作流定义', 'workflow'")
        .contains("('workflow-instances', '工作流实例', 'workflow'")
        .contains("menu_row.required_permission_code = permission_row.permission_code")
        .contains("ON DUPLICATE KEY UPDATE is_delete = 0")
        .doesNotContain("'data-analysis-catalog'")
        .doesNotContain("'data-quality-monitor-detail'")
        .doesNotContain("'system-users'");
  }
}
