package io.yak.ops.boot.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class YakOpsSecurityMigrationVersionCompatibilityTest {

  @Test
  void baselineRetainsSecurityCatalogSourceSections() throws Exception {
    Path migrations = Path.of("src/main/resources/yak-security/db/migration");
    if (!Files.isDirectory(migrations)) {
      migrations =
          Path.of(
              "data-ops-boot", "src", "main", "resources", "yak-security", "db", "migration");
    }

    // 模块迁移已合并为单文件;历史迁移以 -- Source: 段保留,这里断言各版本段仍在。
    String baseline = Files.readString(migrations.resolve("V2__boot_security_baseline.sql"));
    for (String script : List.of(
        "V2040__reconcile_datasource_permission_codes.sql",
        "V2041__rename_metric_service_menu.sql",
        "V2042__register_metric_publish_permission.sql",
        "V2043__register_dataset_query_permission.sql",
        "V2044__register_data_service_invoke_permission.sql")) {
      assertThat(baseline).contains(script);
    }
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
