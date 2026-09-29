package io.yak.ops.business.datasource.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.gateway.adapter.SpiSqlExecutionGateway;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionPolicyViolationException;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlColumn;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.sql.SQLTimeoutException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultSqlExecutionRuntimeTest {

  @Test
  void mapsResultSetAndPreservesNullableSqlValues() {
    CapturingExecutor executor = new CapturingExecutor(new DataSourceSqlResult(
        true,
        List.of(new DataSourceSqlColumn("name", "name", "VARCHAR", Types.VARCHAR, true)),
        List.of(Arrays.asList("Yak", null)),
        0L,
        false));
    SqlExecutionRequest request = new SqlExecutionRequest(
        " 12 ",
        " select name from demo where id = ? and deleted_at is ? ",
        Arrays.asList(7L, null),
        100,
        30,
        SqlExecutionContext.of(SqlExecutionCaller.DATASET, "42"));

    DefaultSqlExecutionRuntime runtime = runtime(executor);
    SqlExecutionResult result = runtime.execute(request);
    runtime.shutdown();

    assertEquals(SqlExecutionResultType.RESULT_SET, result.type());
    assertTrue(result.resultSet());
    assertEquals(1, result.returnedRows());
    assertEquals("name", result.columns().get(0).label());
    assertNull(result.rows().get(0).get(1));
    assertEquals("12", executor.request.dataSourceId());
    assertNull(executor.request.parameters().get(1));
    assertTrue(executor.closed);
  }

  @Test
  void mapsUpdateCountWithoutPretendingItIsAQueryResult() {
    CapturingExecutor executor = new CapturingExecutor(DataSourceSqlResult.updateCount(3L));
    DefaultSqlExecutionRuntime runtime = runtime(executor);
    SqlExecutionResult result = runtime.execute(new SqlExecutionRequest(
        "9",
        "update demo set enabled = 1",
        10,
        30,
        SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-1")));
    runtime.shutdown();

    assertEquals(SqlExecutionResultType.UPDATE_COUNT, result.type());
    assertFalse(result.resultSet());
    assertEquals(3L, result.affectedRows());
    assertEquals(0, result.returnedRows());
  }

  @Test
  void propagatesRuntimeDatasourceFailures() {
    CapturingExecutor executor = new CapturingExecutor(new IllegalStateException("driver failed"));
    DefaultSqlExecutionRuntime runtime = runtime(executor);
    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> runtime.execute(new SqlExecutionRequest(
            "9",
            "select 1",
            10,
            30,
            SqlExecutionContext.of(SqlExecutionCaller.DATA_SERVICE, "service-2"))));
    runtime.shutdown();

    assertEquals("driver failed", exception.getMessage());
  }

  @Test
  void rejectsDatasetWriteBeforeOpeningDatasource() {
    AtomicInteger opened = new AtomicInteger();
    DataSourceExecutionProvider provider = dataSourceId -> {
      opened.incrementAndGet();
      return request -> DataSourceSqlResult.updateCount(1L);
    };
    DefaultSqlExecutionRuntime runtime = runtime(provider);

    SqlExecutionPolicyViolationException exception = assertThrows(
        SqlExecutionPolicyViolationException.class,
        () -> runtime.execute(new SqlExecutionRequest(
            "42",
            "update orders set status = 'DONE'",
            10,
            30,
            SqlExecutionContext.of(SqlExecutionCaller.DATASET, "dataset-1"))));
    runtime.shutdown();

    assertEquals(SqlExecutionCaller.DATASET, exception.caller());
    assertEquals(SqlStatementType.UPDATE, exception.classification().primaryType());
    assertEquals(0, opened.get());
  }

  @Test
  void rejectsMutatingCteForReadOnlyDataService() {
    AtomicInteger opened = new AtomicInteger();
    DataSourceExecutionProvider provider = dataSourceId -> {
      opened.incrementAndGet();
      return request -> DataSourceSqlResult.resultSet(List.of(), List.of(), false);
    };
    DefaultSqlExecutionRuntime runtime = runtime(provider);
    SqlExecutionPlan plan = new SqlExecutionPlan(
        "42",
        List.of(new SqlStatementRequest("""
            WITH deleted AS (
              DELETE FROM orders WHERE expired = true RETURNING *
            )
            SELECT * FROM deleted
            """, 10, 30)),
        SqlExecutionContext.of(SqlExecutionCaller.DATA_SERVICE, "service-1"));

    SqlExecutionPolicyViolationException exception = assertThrows(
        SqlExecutionPolicyViolationException.class,
        () -> runtime.start(plan));
    runtime.shutdown();

    assertEquals(SqlStatementType.SELECT, exception.classification().primaryType());
    assertTrue(exception.classification().potentiallyMutating());
    assertEquals(0, opened.get());
  }

  @Test
  void rejectsEmbeddedTransactionControlForTask() {
    AtomicInteger opened = new AtomicInteger();
    DataSourceExecutionProvider provider = dataSourceId -> {
      opened.incrementAndGet();
      return request -> DataSourceSqlResult.updateCount(0L);
    };
    DefaultSqlExecutionRuntime runtime = runtime(provider);

    SqlExecutionPolicyViolationException exception = assertThrows(
        SqlExecutionPolicyViolationException.class,
        () -> runtime.start(new SqlExecutionPlan(
            "42",
            List.of(new SqlStatementRequest("begin", 10, 30)),
            SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-1"))));
    runtime.shutdown();

    assertEquals(SqlStatementType.BEGIN, exception.classification().primaryType());
    assertEquals(0, opened.get());
  }

  @Test
  void tracksExplicitMultiStatementLifecycleAndResults() {
    AtomicInteger opened = new AtomicInteger();
    DataSourceExecutionProvider provider = dataSourceId -> {
      int index = opened.getAndIncrement();
      return request -> index == 0
          ? DataSourceSqlResult.resultSet(
              List.of(new DataSourceSqlColumn("VALUE", "value", "INTEGER", Types.INTEGER, true)),
              List.of(List.of(1)),
              false)
          : DataSourceSqlResult.updateCount(2L);
    };
    DefaultSqlExecutionRuntime runtime = runtime(provider);
    SqlExecutionPlan plan = new SqlExecutionPlan(
        "42",
        List.of(
            new SqlStatementRequest("select 1 as value", 10, 5),
            new SqlStatementRequest("update demo set enabled = 1", 10, 5)),
        SqlExecutionContext.of(SqlExecutionCaller.CONSOLE, "console-1"));

    SqlExecutionSnapshot started = runtime.start(plan);
    assertTrue(runtime.find(started.executionId()).isPresent());

    SqlExecutionSnapshot completed = runtime.await(started.executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.SUCCEEDED, completed.status());
    assertEquals(SqlTransactionMode.AUTO_COMMIT, completed.transactionMode());
    assertEquals(2, completed.statements().size());
    assertEquals(SqlStatementType.SELECT, completed.statements().get(0).statementType());
    assertEquals(SqlStatementType.UPDATE, completed.statements().get(1).statementType());
    assertEquals(SqlStatementStatus.SUCCEEDED, completed.statements().get(0).status());
    assertEquals(SqlExecutionResultType.RESULT_SET, completed.statements().get(0).result().type());
    assertEquals(SqlExecutionResultType.UPDATE_COUNT, completed.statements().get(1).result().type());
    assertEquals(2L, completed.statements().get(1).result().affectedRows());
    assertTrue(completed.statements().get(0).statementId().contains(completed.executionId()));
  }

  @Test
  void commitsSingleTransactionAfterAllStatementsSucceed() {
    TransactionExecutor executor = new TransactionExecutor(-1);
    AtomicInteger opened = new AtomicInteger();
    DataSourceExecutionProvider provider = dataSourceId -> {
      opened.incrementAndGet();
      return executor;
    };
    DefaultSqlExecutionRuntime runtime = runtime(provider);
    SqlExecutionSnapshot completed = runtime.await(runtime.start(new SqlExecutionPlan(
        "42",
        List.of(
            new SqlStatementRequest("update orders set status = 'DONE' where id = 1", 10, 30),
            new SqlStatementRequest("insert into audit_log(id) values (1)", 10, 30)),
        SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-1"),
        SqlTransactionMode.SINGLE_TRANSACTION)).executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.SUCCEEDED, completed.status());
    assertEquals(SqlTransactionMode.SINGLE_TRANSACTION, completed.transactionMode());
    assertEquals(1, opened.get());
    assertTrue(executor.begun.get());
    assertTrue(executor.committed.get());
    assertFalse(executor.rolledBack.get());
    assertEquals(2, executor.sql.size());
    assertTrue(executor.closed.get());
  }

  @Test
  void rollsBackSingleTransactionAndSkipsRemainingStatementOnFailure() {
    TransactionExecutor executor = new TransactionExecutor(2);
    DefaultSqlExecutionRuntime runtime = runtime(dataSourceId -> executor);
    SqlExecutionSnapshot completed = runtime.await(runtime.start(new SqlExecutionPlan(
        "42",
        List.of(
            new SqlStatementRequest("update orders set status = 'DONE' where id = 1", 10, 30),
            new SqlStatementRequest("delete from audit_log where id = 1", 10, 30),
            new SqlStatementRequest("insert into audit_log(id) values (2)", 10, 30)),
        SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-2"),
        SqlTransactionMode.SINGLE_TRANSACTION)).executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.FAILED, completed.status());
    assertTrue(executor.begun.get());
    assertFalse(executor.committed.get());
    assertTrue(executor.rolledBack.get());
    assertEquals(SqlStatementStatus.SUCCEEDED, completed.statements().get(0).status());
    assertEquals(SqlStatementStatus.FAILED, completed.statements().get(1).status());
    assertEquals(SqlStatementStatus.SKIPPED, completed.statements().get(2).status());
    assertEquals(SqlStatementType.DELETE, completed.statements().get(1).statementType());
  }

  @Test
  void failsFastWhenDatasourceDoesNotSupportSingleTransaction() {
    AtomicInteger executions = new AtomicInteger();
    DataSourceSqlExecutor executor = request -> {
      executions.incrementAndGet();
      return DataSourceSqlResult.updateCount(1L);
    };
    DefaultSqlExecutionRuntime runtime = runtime(dataSourceId -> executor);
    SqlExecutionSnapshot completed = runtime.await(runtime.start(new SqlExecutionPlan(
        "42",
        List.of(new SqlStatementRequest("update demo set enabled = 1", 10, 30)),
        SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-3"),
        SqlTransactionMode.SINGLE_TRANSACTION)).executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.FAILED, completed.status());
    assertEquals(0, executions.get());
    assertEquals(SqlStatementStatus.SKIPPED, completed.statements().get(0).status());
    assertTrue(completed.errorMessage().contains("does not support SINGLE_TRANSACTION"));
  }

  @Test
  void cancelsActiveStatementAndSkipsRemainingStatements() throws Exception {
    BlockingExecutor blocking = new BlockingExecutor();
    AtomicInteger opened = new AtomicInteger();
    DataSourceExecutionProvider provider = dataSourceId ->
        opened.getAndIncrement() == 0 ? blocking : request -> DataSourceSqlResult.updateCount(1L);
    DefaultSqlExecutionRuntime runtime = runtime(provider);
    SqlExecutionSnapshot started = runtime.start(new SqlExecutionPlan(
        "42",
        List.of(
            new SqlStatementRequest("select sleep", 10, 30),
            new SqlStatementRequest("update should_not_run set value = 1", 10, 30)),
        SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-1")));

    assertTrue(blocking.started.await(2, TimeUnit.SECONDS));
    assertTrue(runtime.cancel(started.executionId()));
    SqlExecutionSnapshot cancelled = runtime.await(started.executionId());
    assertFalse(runtime.cancel(started.executionId()));
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.CANCELLED, cancelled.status());
    assertEquals(SqlStatementStatus.CANCELLED, cancelled.statements().get(0).status());
    assertEquals(SqlStatementStatus.SKIPPED, cancelled.statements().get(1).status());
    assertTrue(blocking.cancelled.get());
    assertEquals(1, opened.get());
  }

  @Test
  void promotesStatementTimeoutToExecutionTimeout() {
    DataSourceExecutionProvider provider = dataSourceId -> request -> {
      throw new IllegalStateException(new SQLTimeoutException("query timeout"));
    };
    DefaultSqlExecutionRuntime runtime = runtime(provider);
    SqlExecutionSnapshot completed = runtime.await(runtime.start(new SqlExecutionRequest(
        "42",
        "select slow_query",
        10,
        1,
        SqlExecutionContext.of(SqlExecutionCaller.CONSOLE, "console-2"))).executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.TIMED_OUT, completed.status());
    assertEquals(SqlStatementStatus.TIMED_OUT, completed.statements().get(0).status());
    assertTrue(completed.errorMessage().contains("SQLTimeoutException")
        || completed.errorMessage().contains("query timeout"));
  }

  private static DefaultSqlExecutionRuntime runtime(CapturingExecutor executor) {
    DataSourceExecutionProvider provider = dataSourceId -> {
      executor.request = new CapturedRequest(dataSourceId, List.of());
      return executor;
    };
    return runtime(provider);
  }

  private static DefaultSqlExecutionRuntime runtime(DataSourceExecutionProvider provider) {
    return new DefaultSqlExecutionRuntime(
        new SpiSqlExecutionGateway(provider),
        new DefaultSqlExecutionPolicy());
  }

  private record CapturedRequest(String dataSourceId, List<Object> parameters) {}

  private static final class CapturingExecutor implements DataSourceSqlExecutor {
    private final DataSourceSqlResult result;
    private final RuntimeException failure;
    private CapturedRequest request;
    private boolean closed;

    private CapturingExecutor(DataSourceSqlResult result) {
      this.result = result;
      this.failure = null;
    }

    private CapturingExecutor(RuntimeException failure) {
      this.result = null;
      this.failure = failure;
    }

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) {
      this.request = new CapturedRequest(this.request.dataSourceId(), request.parameters());
      if (failure != null) throw failure;
      return result;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class TransactionExecutor implements DataSourceSqlExecutor {
    private final int failOnCall;
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicBoolean begun = new AtomicBoolean(false);
    private final AtomicBoolean committed = new AtomicBoolean(false);
    private final AtomicBoolean rolledBack = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final List<String> sql = new ArrayList<>();

    private TransactionExecutor(int failOnCall) {
      this.failOnCall = failOnCall;
    }

    @Override
    public boolean supportsTransactions() {
      return true;
    }

    @Override
    public void beginTransaction() {
      begun.set(true);
    }

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) {
      sql.add(request.sql());
      int call = calls.incrementAndGet();
      if (call == failOnCall) throw new IllegalStateException("statement failed");
      return DataSourceSqlResult.updateCount(1L);
    }

    @Override
    public void commitTransaction() {
      committed.set(true);
    }

    @Override
    public void rollbackTransaction() {
      rolledBack.set(true);
    }

    @Override
    public void close() {
      closed.set(true);
    }
  }

  private static final class BlockingExecutor implements DataSourceSqlExecutor {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) {
      started.countDown();
      try {
        released.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      if (cancelled.get()) throw new IllegalStateException("SQL execution cancelled");
      return DataSourceSqlResult.resultSet(List.of(), List.of(), false);
    }

    @Override
    public void cancel() {
      cancelled.set(true);
      released.countDown();
    }
  }
}
