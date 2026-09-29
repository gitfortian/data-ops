package io.yak.ops.business.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AuditMigrationContractTest {

  @Test
  void baselineDefinesOperationAndAppendOnlyEventStore() throws IOException {
    try (var stream = getClass().getResourceAsStream("/db/migration/yak-audit/V1__baseline_audit.sql")) {
      assertThat(stream).isNotNull();
      String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      assertThat(sql).contains("yak_audit_operation", "yak_audit_event", "operation_id", "schema_version");
      assertThat(sql).contains("root_trace_id", "trace_id");
      assertThat(sql).doesNotContain("password", "access_token", "secret_value");
    }
  }
}
