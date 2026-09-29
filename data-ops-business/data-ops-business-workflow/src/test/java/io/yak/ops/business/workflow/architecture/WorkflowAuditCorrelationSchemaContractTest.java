package io.yak.ops.business.workflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class WorkflowAuditCorrelationSchemaContractTest {

  @Test
  void v2AddsOnlyNullableExecutionAuditCarrierWithoutBackfill() throws IOException {
    String baseline = resource("db/migration/yak-workflow/V1__baseline_workflow.sql");
    String migration = resource("db/migration/yak-workflow/V2__add_execution_audit_carrier.sql");
    String upper = migration.toUpperCase(Locale.ROOT);

    assertThat(baseline).doesNotContain("audit_carrier_json");
    assertThat(migration)
        .contains("ALTER TABLE yak_workflow_execution")
        .contains("ADD COLUMN audit_carrier_json LONGTEXT NULL");
    assertThat(upper)
        .doesNotContain("UPDATE YAK_WORKFLOW_EXECUTION")
        .doesNotContain("INSERT INTO YAK_WORKFLOW_EXECUTION")
        .doesNotContain("DELETE FROM YAK_WORKFLOW_EXECUTION");
  }

  @Test
  void correlationMapperStaysProjectScopedAndDoesNotRewriteRuntimeTruth() throws IOException {
    String mapper = resource("mapper/workflow/WorkflowExecutionMapper.xml");
    String update = between(mapper, "<update id=\"updateAuditCarrier\">", "</update>");
    String select = between(mapper, "<select id=\"selectAuditCarrierJson\"", "</select>");

    assertThat(update)
        .contains("audit_carrier_json = #{carrierJson}")
        .contains("id = #{executionId}")
        .contains("project_id = #{projectId}")
        .doesNotContain("status =")
        .doesNotContain("updated_at =")
        .doesNotContain("runtime_metadata_json =");
    assertThat(select)
        .contains("audit_carrier_json")
        .contains("project_id = #{projectId}");
  }

  private String resource(String path) throws IOException {
    try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
      if (input == null) throw new IllegalStateException("Missing test resource: " + path);
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String between(String value, String startMarker, String endMarker) {
    int start = value.indexOf(startMarker);
    assertThat(start).as("start marker %s", startMarker).isGreaterThanOrEqualTo(0);
    int end = value.indexOf(endMarker, start);
    assertThat(end).as("end marker %s", endMarker).isGreaterThan(start);
    return value.substring(start, end + endMarker.length());
  }
}
