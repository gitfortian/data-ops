package io.yak.ops.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class YakOpsSecurityCatalogMigrationTest {

  @Test
  void baselineOwnsCurrentBusinessPermissionsMenusAndBackfills()
      throws Exception {

    ClassPathResource resource = new ClassPathResource(
        "yak-security/db/migration/V1000__init_yak_ops_security_catalog.sql");
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);

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
}
