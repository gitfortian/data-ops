package io.yak.ops.plugin.task.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionColumn;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlExecutionTiming;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementSnapshot;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import io.yak.ops.core.execution.sql.SqlStatementType;
import io.yak.ops.plugin.task.api.TaskExecutionContext;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SqlTaskPluginTest {

  @Test
  void validatesDatasourceAndExecutesThroughSharedLifecycleCapability() throws Exception {
    SqlTaskPlugin plugin = new SqlTaskPlugin();
    TaskDefinition definition =
        new TaskDefinition(
            "SQL",
            1,
            "select 1 as value",
            "{\"dataSourceId\":\"42\",\"maxRows\":10,\"timeoutSeconds\":5}");
    AtomicReference<SqlExecutionRequest> captured = new AtomicReference<>();
    AtomicReference<SqlExecutionSnapshot> completed = new AtomicReference<>();
    SqlExecutionRuntime runtime =
        new SqlExecutionRuntime() {
          @Override
          public SqlExecutionResult execute(SqlExecutionRequest request) {
            throw new UnsupportedOperationException();
          }

          @Override
          public SqlExecutionSnapshot start(SqlExecutionPlan plan) {
            SqlStatementSnapshot statement = new SqlStatementSnapshot(
                "sql-test:stmt:1",
                0,
                plan.statements().get(0).sql(),
                SqlStatementType.SELECT,
                SqlStatementStatus.SUCCEEDED,
                new SqlExecutionResult(
                    SqlExecutionResultType.RESULT_SET,
                    List.of(new SqlExecutionColumn(
                        "VALUE", "value", "INTEGER", Types.INTEGER, true)),
                    List.of(List.of(1)),
                    0L,
                    false,
                    new SqlExecutionTiming(1L, 2L, 3L)),
                null,
                Instant.now(),
                Instant.now());
            SqlExecutionSnapshot snapshot = new SqlExecutionSnapshot(
                "sql-test",
                SqlExecutionStatus.SUCCEEDED,
                plan.dataSourceId(),
                plan.context(),
                plan.transactionMode(),
                List.of(statement),
                Instant.now(),
                Instant.now(),
                null);
            captured.set(new SqlExecutionRequest(
                plan.dataSourceId(),
                plan.statements().get(0).sql(),
                plan.statements().get(0).parameters(),
                plan.statements().get(0).maxRows(),
                plan.statements().get(0).timeoutSeconds(),
                plan.context()));
            completed.set(snapshot);
            return snapshot;
          }

          @Override
          public Optional<SqlExecutionSnapshot> find(String executionId) {
            SqlExecutionSnapshot snapshot = completed.get();
            return snapshot != null && snapshot.executionId().equals(executionId)
                ? Optional.of(snapshot)
                : Optional.empty();
          }

          @Override
          public SqlExecutionSnapshot await(String executionId) {
            return completed.get();
          }

          @Override
          public boolean cancel(String executionId) {
            return false;
          }
        };

    TaskExecutionContext context =
        new TaskExecutionContext() {
          @Override
          public TaskExecutionTrigger trigger() {
            return TaskExecutionTrigger.MANUAL;
          }

          @Override
          public Map<String, Object> parameters() {
            return Map.of();
          }

          @Override
          public <T> Optional<T> capability(Class<T> capabilityType) {
            return capabilityType.isInstance(runtime)
                ? Optional.of(capabilityType.cast(runtime))
                : Optional.empty();
          }
        };

    assertTrue(plugin.descriptor().executable());
    assertTrue(plugin.descriptor().cancellable());
    assertTrue(plugin.validate(definition).valid());

    TaskExecutor executor = plugin.createExecutor(definition, context);
    TaskExecutionResult result = executor.execute();

    assertEquals(TaskExecutionStatus.SUCCESS, result.status());
    assertEquals("RESULT_SET", result.output().get("kind"));
    assertEquals("sql-test", result.output().get("sqlExecutionId"));
    assertEquals(10, captured.get().maxRows());
    assertEquals(5, captured.get().timeoutSeconds());
    assertEquals(SqlExecutionCaller.SQL_TASK, captured.get().context().caller());
  }

  @Test
  void rejectsMissingDatasourceReference() {
    SqlTaskPlugin plugin = new SqlTaskPlugin();
    TaskDefinition definition = new TaskDefinition("SQL", 1, "select 1", "{}");

    assertFalse(plugin.validate(definition).valid());
    assertTrue(
        plugin.validate(definition).issues().stream()
            .anyMatch(issue -> "SQL_DATASOURCE_REQUIRED".equals(issue.code())));
  }

  @Test
  void multiStatementScriptIsSplitAndExecutedAsPlan() throws Exception {
    SqlTaskPlugin plugin = new SqlTaskPlugin();
    TaskDefinition definition =
        new TaskDefinition(
            "SQL",
            1,
            "CREATE TABLE t (id INT); INSERT INTO t VALUES (1); SELECT * FROM t",
            "{\"dataSourceId\":\"42\",\"maxRows\":10,\"timeoutSeconds\":5}");
    AtomicReference<SqlExecutionPlan> capturedPlan = new AtomicReference<>();
    AtomicReference<SqlExecutionSnapshot> completed = new AtomicReference<>();
    SqlExecutionRuntime runtime =
        new SqlExecutionRuntime() {
          @Override
          public SqlExecutionResult execute(SqlExecutionRequest request) {
            throw new UnsupportedOperationException();
          }

          @Override
          public SqlExecutionSnapshot start(SqlExecutionPlan plan) {
            capturedPlan.set(plan);
            List<SqlStatementSnapshot> statements = new ArrayList<>();
            for (int i = 0; i < plan.statements().size(); i++) {
              SqlStatementRequest req = plan.statements().get(i);
              SqlExecutionResult result;
              if (i == plan.statements().size() - 1) {
                result = new SqlExecutionResult(
                    SqlExecutionResultType.RESULT_SET,
                    List.of(new SqlExecutionColumn("ID", "id", "INTEGER", Types.INTEGER, true)),
                    List.of(List.of(1)),
                    0L,
                    false,
                    new SqlExecutionTiming(1L, 2L, 3L));
              } else {
                result = new SqlExecutionResult(
                    SqlExecutionResultType.UPDATE_COUNT,
                    List.of(),
                    List.of(),
                    i == 0 ? 0L : 1L,
                    false,
                    new SqlExecutionTiming(1L, 2L, 3L));
              }
              statements.add(new SqlStatementSnapshot(
                  "sql-test:stmt:" + (i + 1),
                  i,
                  req.sql(),
                  i == 0 ? SqlStatementType.CREATE : (i == 1 ? SqlStatementType.INSERT : SqlStatementType.SELECT),
                  SqlStatementStatus.SUCCEEDED,
                  result,
                  null,
                  Instant.now(),
                  Instant.now()));
            }
            SqlExecutionSnapshot snapshot = new SqlExecutionSnapshot(
                "sql-multi",
                SqlExecutionStatus.SUCCEEDED,
                plan.dataSourceId(),
                plan.context(),
                plan.transactionMode(),
                statements,
                Instant.now(),
                Instant.now(),
                null);
            completed.set(snapshot);
            return snapshot;
          }

          @Override
          public Optional<SqlExecutionSnapshot> find(String executionId) {
            SqlExecutionSnapshot snapshot = completed.get();
            return snapshot != null && snapshot.executionId().equals(executionId)
                ? Optional.of(snapshot)
                : Optional.empty();
          }

          @Override
          public SqlExecutionSnapshot await(String executionId) {
            return completed.get();
          }

          @Override
          public boolean cancel(String executionId) {
            return false;
          }
        };

    TaskExecutionContext context =
        new TaskExecutionContext() {
          @Override
          public TaskExecutionTrigger trigger() {
            return TaskExecutionTrigger.MANUAL;
          }

          @Override
          public Map<String, Object> parameters() {
            return Map.of();
          }

          @Override
          public <T> Optional<T> capability(Class<T> capabilityType) {
            return capabilityType.isInstance(runtime)
                ? Optional.of(capabilityType.cast(runtime))
                : Optional.empty();
          }
        };

    TaskExecutor executor = plugin.createExecutor(definition, context);
    TaskExecutionResult result = executor.execute();

    // Verify the script was split into 3 statements
    assertEquals(3, capturedPlan.get().statements().size());
    assertTrue(capturedPlan.get().statements().get(0).sql().startsWith("CREATE TABLE"));
    assertTrue(capturedPlan.get().statements().get(1).sql().startsWith("INSERT INTO"));
    assertTrue(capturedPlan.get().statements().get(2).sql().startsWith("SELECT"));

    // Verify result aggregates across statements
    assertEquals(TaskExecutionStatus.SUCCESS, result.status());
    assertEquals("RESULT_SET", result.output().get("kind"));
    assertEquals("sql-multi", result.output().get("sqlExecutionId"));
    assertEquals(1L, result.output().get("returnedRows"));
    assertEquals(1L, result.output().get("affectedRows"));
  }
}
