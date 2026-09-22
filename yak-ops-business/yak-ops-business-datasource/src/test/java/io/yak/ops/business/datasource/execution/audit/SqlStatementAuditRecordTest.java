package io.yak.ops.business.datasource.execution.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.core.execution.sql.SqlStatementStatus;
import org.junit.jupiter.api.Test;

class SqlStatementAuditRecordTest {

  @Test
  void preservesStatementLevelStatus() {
    SqlStatementAuditRecord record =
        new SqlStatementAuditRecord(
            "statement-1",
            0,
            null,
            null,
            null,
            SqlStatementStatus.SUCCEEDED,
            null,
            0L,
            0L,
            false,
            null,
            null,
            0L,
            null);

    assertThat(record.status()).isEqualTo(SqlStatementStatus.SUCCEEDED);
  }
}
