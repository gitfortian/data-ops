package io.yak.ops.business.datasource.execution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.core.execution.sql.LexicalSqlStatementClassifier;
import io.yak.ops.core.execution.sql.SqlExecutionCaller;
import io.yak.ops.core.execution.sql.SqlExecutionContext;
import io.yak.ops.core.execution.sql.SqlExecutionPlan;
import io.yak.ops.core.execution.sql.SqlExecutionStatus;
import io.yak.ops.core.execution.sql.SqlStatementClassification;
import io.yak.ops.core.execution.sql.SqlStatementRequest;
import io.yak.ops.core.execution.sql.SqlStatementStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqlExecutionAggregateTest {

  private final LexicalSqlStatementClassifier classifier = new LexicalSqlStatementClassifier();

  @Test
  void lifecycleStartsPendingAndCompletesSucceeded() {
    SqlExecutionAggregate aggregate = aggregate();

    assertThat(aggregate.snapshot().status()).isEqualTo(SqlExecutionStatus.PENDING);
    aggregate.markRunning();
    aggregate.markStatementRunning(0);
    aggregate.markStatementSucceeded(0, result());
    aggregate.markStatementRunning(1);
    aggregate.markStatementSucceeded(1, result());
    aggregate.finishSucceeded();

    assertThat(aggregate.snapshot().status()).isEqualTo(SqlExecutionStatus.SUCCEEDED);
    assertThat(aggregate.snapshot().statements())
        .allMatch(statement -> statement.status() == SqlStatementStatus.SUCCEEDED);
    assertThat(aggregate.snapshot().startedAt()).isNotNull();
    assertThat(aggregate.snapshot().finishedAt()).isNotNull();
  }

  @Test
  void cancellationIsDomainStateAndSkipsRemainingStatements() {
    SqlExecutionAggregate aggregate = aggregate();
    aggregate.markRunning();
    aggregate.markStatementRunning(0);

    assertThat(aggregate.requestCancel()).isTrue();
    assertThat(aggregate.snapshot().status()).isEqualTo(SqlExecutionStatus.CANCELLING);
    aggregate.markStatementCancelled(0, "cancelled");
    aggregate.finishCancelled(1, "cancelled");

    assertThat(aggregate.snapshot().status()).isEqualTo(SqlExecutionStatus.CANCELLED);
    assertThat(aggregate.snapshot().statements().get(0).status())
        .isEqualTo(SqlStatementStatus.CANCELLED);
    assertThat(aggregate.snapshot().statements().get(1).status())
        .isEqualTo(SqlStatementStatus.SKIPPED);
    assertThat(aggregate.requestCancel()).isFalse();
  }

  @Test
  void statementFailureOwnsExecutionFailureAndRemainingSkipSemantics() {
    SqlExecutionAggregate aggregate = aggregate();
    aggregate.markRunning();
    aggregate.markStatementRunning(0);
    aggregate.markStatementFailed(0, "boom");
    aggregate.finishFailed(1, "boom");

    assertThat(aggregate.snapshot().status()).isEqualTo(SqlExecutionStatus.FAILED);
    assertThat(aggregate.snapshot().statements().get(0).status())
        .isEqualTo(SqlStatementStatus.FAILED);
    assertThat(aggregate.snapshot().statements().get(1).status())
        .isEqualTo(SqlStatementStatus.SKIPPED);
    assertThat(aggregate.snapshot().errorMessage()).isEqualTo("boom");
  }

  @Test
  void classificationsMustMatchPlanStatements() {
    SqlExecutionPlan plan = plan();
    assertThatThrownBy(() -> new SqlExecutionAggregate("sql-1", plan, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("classifications");
  }

  private SqlExecutionAggregate aggregate() {
    SqlExecutionPlan plan = plan();
    List<SqlStatementClassification> classifications =
        plan.statements().stream().map(statement -> classifier.classify(statement.sql())).toList();
    return new SqlExecutionAggregate("sql-test", plan, classifications);
  }

  private SqlExecutionPlan plan() {
    return new SqlExecutionPlan(
        "42",
        List.of(
            new SqlStatementRequest("select 1", 10, 5),
            new SqlStatementRequest("update demo set enabled = 1", 10, 5)),
        SqlExecutionContext.of(SqlExecutionCaller.CONSOLE, "console-1"));
  }

  private io.yak.ops.core.execution.sql.SqlExecutionResult result() {
    return new io.yak.ops.core.execution.sql.SqlExecutionResult(
        io.yak.ops.core.execution.sql.SqlExecutionResultType.UPDATE_COUNT,
        List.of(),
        List.of(),
        1L,
        false,
        new io.yak.ops.core.execution.sql.SqlExecutionTiming(0L, 1L, 1L));
  }
}
