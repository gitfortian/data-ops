package io.yak.ops.business.datasource.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasource.gateway.adapter.SpiSqlExecutionGateway;
import io.yak.ops.core.execution.sql.*;
import io.yak.ops.spi.datasource.execution.DataSourceExecutionProvider;
import io.yak.ops.spi.datasource.execution.DataSourceSqlResult;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ObservableSqlExecutionRuntimeTest {

    @Test
    void springUsesExplicitInjectionConstructorWhenTestConstructorAlsoExists() {
        DataSourceExecutionProvider provider = dataSourceId -> request ->
                DataSourceSqlResult.resultSet(List.of(), List.of(), false);
        DefaultSqlExecutionRuntime delegate =
                new DefaultSqlExecutionRuntime(
                        new SpiSqlExecutionGateway(provider),
                        new DefaultSqlExecutionPolicy());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("defaultSqlExecutionRuntime", delegate);
            context.getBeanFactory().registerSingleton(
                    "currentProject",
                    (io.yak.ops.core.project.CurrentProject) () -> java.util.Optional.empty());
            context.getBeanFactory().registerSingleton(
                    "projectContextScope",
                    new io.yak.ops.core.project.ProjectContextScope() {
                      @Override
                      public <T> T call(
                          io.yak.ops.core.project.ProjectContext projectContext,
                          java.util.function.Supplier<T> action) {
                        return action.get();
                      }
                    });
            context.registerBean(ObservableSqlExecutionRuntime.class);
            context.refresh();

            assertTrue(context.getBean(ObservableSqlExecutionRuntime.class) instanceof SqlExecutionRuntime);
        } finally {
            delegate.shutdown();
        }
    }

    @Test
    void observesSynchronousDatasetExecution() {
        AtomicReference<SqlExecutionSnapshot> observed = new AtomicReference<>();
        RuntimePair pair = runtime(observed::set);
        try {
            SqlExecutionResult result = pair.observable().execute(new SqlExecutionRequest(
                    "42",
                    "select * from demo where id = 7",
                    10,
                    5,
                    SqlExecutionContext.of(SqlExecutionCaller.DATASET, "dataset-1")));

            assertTrue(result.resultSet());
            SqlExecutionSnapshot snapshot = observed.get();
            assertEquals(SqlExecutionStatus.SUCCEEDED, snapshot.status());
            assertTrue(snapshot.executionId().startsWith("sql-sync-"));
            assertEquals(SqlStatementType.SELECT, snapshot.statements().get(0).statementType());
            assertEquals(SqlStatementStatus.SUCCEEDED, snapshot.statements().get(0).status());
        } finally {
            pair.close();
        }
    }

    @Test
    void observesTrackedExecutionOnceAtTerminalState() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<SqlExecutionSnapshot> observed = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        RuntimePair pair = runtime(snapshot -> {
            calls.incrementAndGet();
            observed.set(snapshot);
            latch.countDown();
        });
        try {
            SqlExecutionSnapshot started = pair.observable().start(new SqlExecutionPlan(
                    "42",
                    List.of(new SqlStatementRequest("update demo set enabled = 1", 10, 5)),
                    SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, "task-1")));

            SqlExecutionSnapshot completed = pair.observable().await(started.executionId());
            assertEquals(SqlExecutionStatus.SUCCEEDED, completed.status());
            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals(started.executionId(), observed.get().executionId());
        } finally {
            pair.close();
        }
    }

    @Test
    void observerFailureNeverChangesSqlOutcome() {
        RuntimePair pair = runtime(snapshot -> {
            throw new IllegalStateException("audit unavailable");
        });
        try {
            SqlExecutionResult result = pair.observable().execute(new SqlExecutionRequest(
                    "42",
                    "select 1",
                    10,
                    5,
                    SqlExecutionContext.of(SqlExecutionCaller.CONSOLE, "console-1")));

            assertTrue(result.resultSet());
        } finally {
            pair.close();
        }
    }

    @Test
    void policyRejectedSqlIsNotRecordedAsPhysicalExecution() {
        AtomicInteger calls = new AtomicInteger();
        RuntimePair pair = runtime(snapshot -> calls.incrementAndGet());
        try {
            assertThrows(
                    SqlExecutionPolicyViolationException.class,
                    () -> pair.observable().execute(new SqlExecutionRequest(
                            "42",
                            "update demo set enabled = 1",
                            10,
                            5,
                            SqlExecutionContext.of(SqlExecutionCaller.DATASET, "dataset-1"))));
            assertEquals(0, calls.get());
        } finally {
            pair.close();
        }
    }

    private static RuntimePair runtime(SqlExecutionObserver observer) {
        DataSourceExecutionProvider provider = dataSourceId -> request ->
                request.sql().trim().toLowerCase().startsWith("select")
                        ? DataSourceSqlResult.resultSet(List.of(), List.of(), false)
                        : DataSourceSqlResult.updateCount(1L);
        DefaultSqlExecutionRuntime delegate =
                new DefaultSqlExecutionRuntime(
                        new SpiSqlExecutionGateway(provider),
                        new DefaultSqlExecutionPolicy());
        return new RuntimePair(delegate, new ObservableSqlExecutionRuntime(delegate, List.of(observer)));
    }

    private record RuntimePair(
            DefaultSqlExecutionRuntime delegate,
            ObservableSqlExecutionRuntime observable) {
        void close() {
            observable.shutdown();
            delegate.shutdown();
        }
    }
}
