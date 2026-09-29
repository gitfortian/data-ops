package io.yak.ops.business.datasource.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.gateway.adapter.SpiSqlExecutionGateway;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlTransactionMode;
import io.yak.ops.spi.datasource.execution.DataSourceSqlExecutor;
import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class DefaultSqlExecutionTransactionCancellationTest {

  @Test
  void rollsBackSingleTransactionWhenActiveStatementIsCancelled() throws Exception {
    BlockingTransactionExecutor executor = new BlockingTransactionExecutor();
    DefaultSqlExecutionRuntime runtime =
        new DefaultSqlExecutionRuntime(
            new SpiSqlExecutionGateway(dataSourceId -> executor),
            new DefaultSqlExecutionPolicy());
    SqlExecutionSnapshot started =
        runtime.start(
            new SqlExecutionPlan(
                "42",
                List.of(
                    new SqlStatementRequest("update orders set status = 'RUNNING'", 10, 30),
                    new SqlStatementRequest("insert into audit_log(id) values (1)", 10, 30)),
                SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-cancel"),
                SqlTransactionMode.SINGLE_TRANSACTION));

    assertTrue(executor.started.await(2, TimeUnit.SECONDS));
    assertTrue(runtime.cancel(started.executionId()));
    SqlExecutionSnapshot completed = runtime.await(started.executionId());
    runtime.shutdown();

    assertEquals(SqlExecutionStatus.CANCELLED, completed.status());
    assertEquals(SqlStatementStatus.CANCELLED, completed.statements().get(0).status());
    assertEquals(SqlStatementStatus.SKIPPED, completed.statements().get(1).status());
    assertTrue(executor.rolledBack.get());
    assertFalse(executor.committed.get());
  }

  private static final class BlockingTransactionExecutor implements DataSourceSqlExecutor {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean committed = new AtomicBoolean(false);
    private final AtomicBoolean rolledBack = new AtomicBoolean(false);

    @Override
    public boolean supportsTransactions() {
      return true;
    }

    @Override
    public void beginTransaction() {
      // No-op test transaction.
    }

    @Override
    public DataSourceSqlResult execute(DataSourceSqlRequest request) {
      started.countDown();
      try {
        released.await(2, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      }
      if (cancelled.get()) throw new IllegalStateException("cancelled");
      return DataSourceSqlResult.updateCount(1L);
    }

    @Override
    public void cancel() {
      cancelled.set(true);
      released.countDown();
    }

    @Override
    public void commitTransaction() {
      committed.set(true);
    }

    @Override
    public void rollbackTransaction() {
      rolledBack.set(true);
    }
  }
}
