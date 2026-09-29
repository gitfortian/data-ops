package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.repository.WorkflowExecutionOverviewRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Workflow execution statistics Reader; persistence stays behind Repository. */
@Component
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowExecutionOverviewReader {

  private final WorkflowExecutionOverviewRepository repository;

  public WorkflowExecutionOverviewReader(WorkflowExecutionOverviewRepository repository) {
    this.repository = repository;
  }

  public Overview overview(LocalDateTime start, LocalDateTime end, boolean hourlyTrend) {
    return overview(repository.overview(start, end, hourlyTrend));
  }

  public Metrics metrics(LocalDateTime start, LocalDateTime end) {
    return metrics(repository.metrics(start, end));
  }

  public Execution latest(LocalDateTime start, LocalDateTime end) {
    return execution(repository.latest(start, end));
  }

  public TaskSummary taskSummary(String taskId, LocalDateTime start, LocalDateTime end) {
    return task(repository.taskSummary(taskId, start, end));
  }

  public List<TaskSummary> recentTasks(LocalDateTime start, LocalDateTime end, int limit) {
    return repository.recentTasks(start, end, limit).stream().map(this::task).toList();
  }

  public List<ScheduleSummary> schedules(int limit) {
    return repository.schedules(limit).stream().map(this::schedule).toList();
  }

  private Overview overview(WorkflowExecutionOverviewRepository.Overview value) {
    return value == null
        ? new Overview(Metrics.empty(), List.of(), null)
        : new Overview(
            metrics(value.metrics()),
            value.trend().stream()
                .map(item -> new TrendPoint(item.bucket(), item.count()))
                .toList(),
            execution(value.latest()));
  }

  private Metrics metrics(WorkflowExecutionOverviewRepository.Metrics value) {
    if (value == null) return Metrics.empty();
    return new Metrics(
        value.successCount(), value.runningCount(), value.failedCount(), value.scheduleCount(),
        value.processedRecords(), value.durationTotalMs(), value.durationSampleCount());
  }

  private Execution execution(WorkflowExecutionOverviewRepository.Execution value) {
    return value == null ? null : new Execution(
        value.taskId(), value.taskName(), value.status(), value.occurredAt(),
        value.durationMs(), value.executionId());
  }

  private TaskSummary task(WorkflowExecutionOverviewRepository.TaskSummary value) {
    return value == null ? null : new TaskSummary(
        value.taskId(), value.taskName(), value.lastRunTime(), value.runCount(),
        value.successCount(), value.failedCount(), value.lastDurationMs(), value.lastStatus(),
        value.executionId());
  }

  private ScheduleSummary schedule(WorkflowExecutionOverviewRepository.ScheduleSummary value) {
    return new ScheduleSummary(
        value.taskId(), value.taskName(), value.cronExpression(), value.status(),
        value.lastScheduleTime(), value.nextScheduleTime());
  }

  public record Overview(Metrics metrics, List<TrendPoint> trend, Execution latest) {
    public Overview {
      trend = trend == null ? List.of() : List.copyOf(trend);
    }
  }

  public record Metrics(
      long successCount,
      long runningCount,
      long failedCount,
      long scheduleCount,
      long processedRecords,
      long durationTotalMs,
      long durationSampleCount) {
    static Metrics empty() {
      return new Metrics(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }
  }

  public record TrendPoint(LocalDateTime bucket, long count) {}

  public record Execution(
      String taskId,
      String taskName,
      String status,
      LocalDateTime occurredAt,
      long durationMs,
      String executionId) {}

  public record TaskSummary(
      String taskId,
      String taskName,
      LocalDateTime lastRunTime,
      long runCount,
      long successCount,
      long failedCount,
      long lastDurationMs,
      String lastStatus,
      String executionId) {}

  public record ScheduleSummary(
      String taskId,
      String taskName,
      String cronExpression,
      String status,
      LocalDateTime lastScheduleTime,
      LocalDateTime nextScheduleTime) {}
}
