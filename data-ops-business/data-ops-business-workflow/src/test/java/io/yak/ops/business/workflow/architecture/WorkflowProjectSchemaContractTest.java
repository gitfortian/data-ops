package io.yak.ops.business.workflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WorkflowProjectSchemaContractTest {

  @Test
  void locksWorkflowProjectOwnershipIntoFirstReleaseBaseline() throws IOException {
    String sql = resource("db/migration/yak-workflow/V1__baseline_workflow.sql");

    assertThat(table(sql, "yak_workflow_definition"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(table(sql, "yak_workflow_version"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(table(sql, "yak_workflow_execution"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(table(sql, "yak_workflow_schedule"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(table(sql, "yak_workflow_schedule_trigger"))
        .contains("project_id BIGINT NOT NULL");
    assertThat(table(sql, "yak_workflow_backfill"))
        .contains("project_id BIGINT NOT NULL");

    assertThat(table(sql, "yak_workflow_node_execution"))
        .doesNotContain("project_id");
    assertThat(table(sql, "yak_workflow_node_attempt"))
        .doesNotContain("project_id");
  }

  private String resource(String path) throws IOException {
    try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
      if (input == null) throw new IllegalStateException("Missing test resource: " + path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String table(String sql, String tableName) {
    String marker = "CREATE TABLE IF NOT EXISTS " + tableName + " (";
    int start = sql.indexOf(marker);
    assertThat(start).as("table %s exists", tableName).isGreaterThanOrEqualTo(0);
    int end = sql.indexOf(") ENGINE=", start);
    assertThat(end).as("table %s has engine terminator", tableName).isGreaterThan(start);
    return sql.substring(start, end);
  }
}
