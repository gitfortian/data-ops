package io.yak.ops.business.job.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskExecutionGatewayTest {

  @Test
  void shouldRouteByTaskTypeAndDefaultWorkflowTrigger() {
    FakeExecutor executor = new FakeExecutor("SQL");
    TaskExecutionGateway gateway = new TaskExecutionGateway(List.of(executor));
    TaskVersionSnapshot snapshot =
        new TaskVersionSnapshot("SQL:1", "demo", "sql", 1L, "digest", "{}", "{}");

    TaskExecution execution = gateway.start(snapshot, "attempt-1", Map.of("biz_date", "2026-08-10"));

    assertThat(execution.executionId()).isEqualTo("SQL-1");
    assertThat(executor.lastTrigger).isEqualTo(TaskExecutionTrigger.WORKFLOW);
    assertThat(gateway.supports("sql")).isTrue();
    assertThat(gateway.status("SQL", execution.executionId()).successful()).isTrue();
  }

  @Test
  void shouldPropagateManualTrigger() {
    FakeExecutor executor = new FakeExecutor("SQL");
    TaskExecutionGateway gateway = new TaskExecutionGateway(List.of(executor));
    TaskVersionSnapshot snapshot =
        new TaskVersionSnapshot("SQL:1", "demo", "sql", 0L, null, "{}", "{}");

    gateway.start(snapshot, TaskExecutionTrigger.MANUAL, null, Map.of());

    assertThat(executor.lastTrigger).isEqualTo(TaskExecutionTrigger.MANUAL);
  }

  @Test
  void shouldRejectDuplicateTaskTypes() {
    assertThatThrownBy(() -> new TaskExecutionGateway(
            List.of(new FakeExecutor("SQL"), new FakeExecutor("sql"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("重复的任务执行器");
  }

  private static final class FakeExecutor implements TaskExecutor {
    private final String type;
    private TaskExecutionTrigger lastTrigger;

    private FakeExecutor(String type) {
      this.type = type;
    }

    @Override
    public String taskType() {
      return type;
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot snapshot,
        String idempotencyKey,
        Map<String, Object> input) {
      return start(snapshot, TaskExecutionTrigger.WORKFLOW, idempotencyKey, input);
    }

    @Override
    public TaskExecution start(
        TaskVersionSnapshot snapshot,
        TaskExecutionTrigger trigger,
        String idempotencyKey,
        Map<String, Object> input) {
      this.lastTrigger = trigger;
      return new TaskExecution("SQL-1", "SUCCEEDED", null, input);
    }

    @Override
    public TaskExecution status(String executionId) {
      return new TaskExecution(executionId, "SUCCEEDED", null, Map.of());
    }

    @Override
    public void cancel(String executionId) {}
  }
}
