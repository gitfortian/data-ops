package io.yak.ops.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class YakOpsLegacyPermissionMenuMigrationTest {

  @Test
  void reconciliationPreservesLegacyLeafPermissionMenuBindings() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "yak-security/db/migration/V2006__reconcile_menu_permission_catalog.sql");
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);

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
