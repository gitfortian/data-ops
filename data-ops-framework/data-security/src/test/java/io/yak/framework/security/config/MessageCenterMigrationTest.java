package io.yak.framework.security.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定消息中心扩展字段，避免模型升级后 Flyway 漏列。 */
class MessageCenterMigrationTest {

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
  void migrationContainsMessageCenterColumnsAndIndexes() throws IOException {
    try (InputStream input = getClass().getResourceAsStream(
            "/yak-security/db/migration/V1__security_framework_baseline.sql")) {
      assertNotNull(input);
      String sql = section(
          new String(input.readAllBytes(), StandardCharsets.UTF_8),
          "V3__evolve_message_center.sql");
      assertTrue(sql.contains("message_type"));
      assertTrue(sql.contains("message_level"));
      assertTrue(sql.contains("message_scope"));
      assertTrue(sql.contains("project_id"));
      assertTrue(sql.contains("source_type"));
      assertTrue(sql.contains("action_path"));
      assertTrue(sql.contains("read_time"));
      assertTrue(sql.contains("idx_message_app_user_project"));
    }
  }
}
