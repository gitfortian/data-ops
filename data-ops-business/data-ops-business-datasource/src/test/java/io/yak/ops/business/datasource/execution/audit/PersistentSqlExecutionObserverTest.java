package io.yak.ops.business.datasource.execution.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlExecutionTiming;
import io.yak.ops.core.execution.sql.SqlStatementSnapshot;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistentSqlExecutionObserverTest {

  @Test
  void mapsOnlyObservabilityMetadataAndRedactsSqlLiterals() {
    Instant started = Instant.parse("2026-08-18T10:00:00Z");
    Instant finished = started.plusMillis(25);
    SqlExecutionResult result = new SqlExecutionResult(
        SqlExecutionResultType.RESULT_SET,
        List.of(),
        List.of(List.of("sensitive-result-value")),
        0L,
        false,
        new SqlExecutionTiming(2L, 20L, 25L));
    SqlExecutionSnapshot snapshot = new SqlExecutionSnapshot(
        "sql-1",
        SqlExecutionStatus.SUCCEEDED,
        "42",
        SqlExecutionContext.of(SqlExecutionCaller.CONSOLE, "console-1", "bruce"),
        SqlTransactionMode.AUTO_COMMIT,
        List.of(new SqlStatementSnapshot(
            "sql-1:stmt:1",
            0,
            "select * from patient where name='Alice' and id=123",
            SqlStatementType.SELECT,
            SqlStatementStatus.SUCCEEDED,
            result,
            null,
            started,
            finished)),
        started,
        finished,
        null);

    PersistentSqlExecutionObserver.AuditBatch batch =
        PersistentSqlExecutionObserver.map(snapshot, 7L);

    assertEquals(7L, batch.execution().getProjectId());
    assertEquals("bruce", batch.execution().getOperatorName());
    assertEquals(1L, batch.execution().getReturnedRows());
    assertEquals(1, batch.execution().getSucceededStatementCount());
    assertEquals(1, batch.statements().size());
    String preview = batch.statements().get(0).getSqlPreview();
    assertFalse(preview.contains("Alice"));
    assertFalse(preview.contains("123"));
    assertFalse(preview.contains("sensitive-result-value"));
    assertTrue(preview.contains("?"));
    assertEquals(64, batch.statements().get(0).getSqlFingerprint().length());
  }
}
