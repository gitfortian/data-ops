package io.yak.ops.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class YakOpsPermissionMenuMigrationTest {

  @Test
  void migrationReconcilesVisibleMenusAndQualityActions() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "yak-security/db/migration/V2006__reconcile_menu_permission_catalog.sql");
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);

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
