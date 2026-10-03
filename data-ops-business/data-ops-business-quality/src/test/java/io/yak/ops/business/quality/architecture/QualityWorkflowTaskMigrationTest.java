package io.yak.ops.business.quality.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualityWorkflowTaskMigrationTest {

  @Test
  void workflowTaskMigrationAddsImmutableRevisionAndIdempotencyContracts() throws IOException {
    String sql = section(Files.readString(migration()), "V2__add_quality_workflow_task_contract.sql");

    assertThat(sql)
        .contains("CREATE TABLE IF NOT EXISTS yak_quality_monitor_revision")
        .contains("UNIQUE KEY uk_yak_quality_monitor_revision_no")
        .contains("ADD COLUMN idempotency_key VARCHAR(255) NULL")
        .contains("UNIQUE KEY uk_yak_quality_execution_idempotency")
        .doesNotContain("UPDATE yak_quality_monitor");
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
  private Path migration() {
    Path local = Path.of(
        "src/main/resources/db/migration/yak-quality/V1__quality_baseline.sql");
    if (Files.isRegularFile(local)) return local;
    Path repository = Path.of(
        "data-ops-business",
        "data-ops-business-quality",
        "src",
        "main",
        "resources",
        "db",
        "migration",
        "yak-quality",
        "V1__quality_baseline.sql");
    assertThat(Files.isRegularFile(repository)).isTrue();
    return repository;
  }
}
