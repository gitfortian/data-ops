package io.yak.ops.plugin.task.sql;

import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionRequest;
import io.yak.ops.core.execution.sql.SqlExecutionResult;
import io.yak.ops.core.execution.sql.SqlExecutionResultType;
import io.yak.ops.core.execution.sql.SqlExecutionRuntime;
import io.yak.ops.core.execution.sql.SqlExecutionSnapshot;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlScriptSplitter;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementSnapshot;
import io.yak.ops.plugin.task.api.TaskExecutionResult;
import io.yak.ops.plugin.task.api.TaskExecutor;
import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.spi.task.model.TaskExecutionStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One SQL task attempt delegated to the shared SQL execution lifecycle. */
final class SqlTaskExecutor implements TaskExecutor {

  private final TaskDefinition definition;
  private final SqlTaskConfig config;
  private final SqlExecutionRuntime runtime;
  private final AtomicBoolean cancelled = new AtomicBoolean(false);
  private final AtomicReference<String> executionId = new AtomicReference<>();

  SqlTaskExecutor(
      TaskDefinition definition,
      SqlTaskConfig config,
      SqlExecutionRuntime runtime) {
    this.definition = definition;
    this.config = config;
    this.runtime = runtime;
  }

  @Override
  public TaskExecutionResult execute() {
    if (cancelled.get()) {
      return cancelledResult("SQL execution was cancelled before start");
    }

    try {
      List<String> sqls = SqlScriptSplitter.split(definition.content());
      if (sqls.isEmpty()) {
        return new TaskExecutionResult(
            TaskExecutionStatus.FAILED,
            "SQL script contains no valid statements",
            failureOutput());
      }

      SqlExecutionContext context =
          SqlExecutionContext.of(SqlExecutionCaller.SQL_TASK, null);
      List<SqlStatementRequest> statementRequests = sqls.stream()
          .map(sql -> new SqlStatementRequest(sql, config.maxRows(), config.timeoutSeconds()))
          .toList();
      SqlExecutionPlan plan = new SqlExecutionPlan(
          config.dataSourceId(), statementRequests, context);

      SqlExecutionSnapshot started = runtime.start(plan);
      executionId.set(started.executionId());

      if (cancelled.get()) {
        runtime.cancel(started.executionId());
      }

      SqlExecutionSnapshot completed = runtime.await(started.executionId());
      return toTaskResult(completed);
    } catch (RuntimeException exception) {
      if (cancelled.get()) {
        return cancelledResult(safeMessage(exception));
      }
      return new TaskExecutionResult(
          TaskExecutionStatus.FAILED,
          safeMessage(exception),
          failureOutput());
    }
  }

  @Override
  public void cancel() {
    cancelled.set(true);
    String currentExecutionId = executionId.get();
    if (currentExecutionId != null) {
      runtime.cancel(currentExecutionId);
    }
  }

  private TaskExecutionResult toTaskResult(SqlExecutionSnapshot execution) {
    if (execution.status() == SqlExecutionStatus.CANCELLED) {
      return new TaskExecutionResult(
          TaskExecutionStatus.CANCELLED,
          defaultMessage(execution.errorMessage(), "SQL execution cancelled"),
          failureOutput(execution.executionId()));
    }
    if (execution.status() == SqlExecutionStatus.TIMED_OUT) {
      return new TaskExecutionResult(
          TaskExecutionStatus.TIMEOUT,
          defaultMessage(execution.errorMessage(), "SQL execution timed out"),
          failureOutput(execution.executionId()));
    }
    if (execution.status() != SqlExecutionStatus.SUCCEEDED) {
      return new TaskExecutionResult(
          TaskExecutionStatus.FAILED,
          defaultMessage(execution.errorMessage(), "SQL execution failed"),
          failureOutput(execution.executionId()));
    }

    // For multi-statement scripts, aggregate all statement results.
    // The last result set (if any) is surfaced as the primary output;
    // all affected rows are summed across statements.
    long totalAffectedRows = 0L;
    long totalReturnedRows = 0L;
    SqlStatementSnapshot lastResultSetStatement = null;
    for (SqlStatementSnapshot stmt : execution.statements()) {
      SqlExecutionResult result = stmt.result();
      if (result == null) continue;
      totalAffectedRows += result.affectedRows();
      totalReturnedRows += result.returnedRows();
      if (result.type() == SqlExecutionResultType.RESULT_SET) {
        lastResultSetStatement = stmt;
      }
    }

    Map<String, Object> output = new LinkedHashMap<>();
    output.put("dataSourceId", config.dataSourceId());
    output.put("sqlExecutionId", execution.executionId());
    output.put("returnedRows", totalReturnedRows);
    output.put("affectedRows", totalAffectedRows);

    if (lastResultSetStatement != null) {
      SqlExecutionResult result = lastResultSetStatement.result();
      output.put("kind", result.type().name());
      output.put("columns", result.columns());
      output.put("rows", result.rows());
      output.put("truncated", result.truncated());
      output.put("statementId", lastResultSetStatement.statementId());
    } else {
      // All statements were UPDATE_COUNT – surface the last one as the primary result
      SqlStatementSnapshot lastStatement = execution.statements().get(execution.statements().size() - 1);
      SqlExecutionResult result = lastStatement.result();
      output.put("kind", result != null ? result.type().name() : SqlExecutionResultType.UPDATE_COUNT.name());
      output.put("affectedRows", totalAffectedRows);
      output.put("statementId", lastStatement.statementId());
    }

    return TaskExecutionResult.success(output);
  }

  private TaskExecutionResult cancelledResult(String message) {
    return new TaskExecutionResult(
        TaskExecutionStatus.CANCELLED,
        defaultMessage(message, "SQL execution cancelled"),
        failureOutput());
  }

  private Map<String, Object> failureOutput() {
    return failureOutput(executionId.get());
  }

  private Map<String, Object> failureOutput(String currentExecutionId) {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("dataSourceId", config.dataSourceId());
    if (currentExecutionId != null) output.put("sqlExecutionId", currentExecutionId);
    return output;
  }

  private String defaultMessage(String message, String fallback) {
    return message == null || message.isBlank() ? fallback : message;
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable == null ? null : throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable == null ? "SQL execution failed" : throwable.getClass().getSimpleName();
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
