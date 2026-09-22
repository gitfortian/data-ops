package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL-backed, Project-scoped data-quality execution overview projection. */
@Repository
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityExecutionOverviewRepositoryAdapter
    implements QualityExecutionOverviewRepository {

  private static final String SUCCESS_STATUSES = "'SUCCESS','SUCCEEDED','COMPLETED'";
  private static final String FAILED_STATUSES = "'FAILED','ERROR','TIMED_OUT'";
  private static final String RUNNING_STATUSES = "'QUEUED','RUNNING'";

  private final JdbcTemplate jdbc;
  private final CurrentProject currentProject;

  public QualityExecutionOverviewRepositoryAdapter(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      CurrentProject currentProject) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.currentProject = currentProject;
  }

  @Override
  public Overview overview(
      LocalDateTime start,
      LocalDateTime end,
      boolean hourlyTrend) {
    long projectId = currentProjectId();
    return new Overview(
        metrics(projectId, start, end),
        trend(projectId, start, end, hourlyTrend),
        latest(projectId, start, end));
  }

  @Override
  public Execution latest(LocalDateTime start, LocalDateTime end) {
    return latest(currentProjectId(), start, end);
  }

  @Override
  public TaskSummary taskSummary(
      String taskId,
      LocalDateTime start,
      LocalDateTime end) {
    if (taskId == null || taskId.isBlank()) return null;
    long id;
    try {
      id = Long.parseLong(taskId);
    } catch (NumberFormatException ignored) {
      return null;
    }
    long projectId = currentProjectId();
    String sql = """
        SELECT e.monitor_id AS task_id,
               MAX(e.monitor_name) AS task_name,
               COUNT(*) AS run_count,
               SUM(CASE WHEN UPPER(e.execution_status) IN (%s) THEN 1 ELSE 0 END) AS success_count,
               SUM(CASE WHEN UPPER(e.execution_status) IN (%s) THEN 1 ELSE 0 END) AS failed_count,
               MAX(COALESCE(e.started_at, e.queued_at, e.created_at)) AS last_run_time
          FROM yak_quality_execution e
         WHERE e.project_id = ?
           AND e.queued_at >= ? AND e.queued_at < ?
           AND e.monitor_id = ?
         GROUP BY e.monitor_id
        """.formatted(SUCCESS_STATUSES, FAILED_STATUSES);
    List<TaskAggregate> aggregates =
        jdbc.query(sql, this::taskAggregate, projectId, start, end, id);
    if (aggregates.isEmpty()) return null;
    return summary(
        aggregates.get(0),
        latestForTask(projectId, id, start, end));
  }

  @Override
  public List<TaskSummary> recentTasks(
      LocalDateTime start,
      LocalDateTime end,
      int limit) {
    long projectId = currentProjectId();
    int boundedLimit = Math.max(1, Math.min(limit, 20));
    String sql = """
        SELECT e.monitor_id AS task_id,
               MAX(e.monitor_name) AS task_name,
               COUNT(*) AS run_count,
               SUM(CASE WHEN UPPER(e.execution_status) IN (%s) THEN 1 ELSE 0 END) AS success_count,
               SUM(CASE WHEN UPPER(e.execution_status) IN (%s) THEN 1 ELSE 0 END) AS failed_count,
               MAX(COALESCE(e.started_at, e.queued_at, e.created_at)) AS last_run_time
          FROM yak_quality_execution e
         WHERE e.project_id = ?
           AND e.queued_at >= ? AND e.queued_at < ?
         GROUP BY e.monitor_id
         ORDER BY last_run_time DESC
         LIMIT %d
        """.formatted(SUCCESS_STATUSES, FAILED_STATUSES, boundedLimit);
    List<TaskAggregate> aggregates =
        jdbc.query(sql, this::taskAggregate, projectId, start, end);
    List<TaskSummary> result = new ArrayList<>(aggregates.size());
    for (TaskAggregate aggregate : aggregates) {
      result.add(
          summary(
              aggregate,
              latestForTask(
                  projectId,
                  aggregate.taskId(),
                  start,
                  end)));
    }
    return List.copyOf(result);
  }

  @Override
  public Metrics metrics(LocalDateTime start, LocalDateTime end) {
    return metrics(currentProjectId(), start, end);
  }

  private Execution latest(
      long projectId,
      LocalDateTime start,
      LocalDateTime end) {
    String sql = """
        SELECT CAST(e.monitor_id AS CHAR) AS task_id,
               e.monitor_name AS task_name,
               e.execution_status AS status,
               COALESCE(e.started_at, e.queued_at, e.created_at) AS occurred_at,
               COALESCE(e.duration_ms, 0) AS duration_ms,
               e.execution_no AS execution_id
          FROM yak_quality_execution e
         WHERE e.project_id = ?
           AND e.queued_at >= ? AND e.queued_at < ?
         ORDER BY COALESCE(e.started_at, e.queued_at, e.created_at) DESC, e.id DESC
         LIMIT 1
        """;
    List<Execution> rows =
        jdbc.query(sql, this::execution, projectId, start, end);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Metrics metrics(
      long projectId,
      LocalDateTime start,
      LocalDateTime end) {
    String sql = """
        SELECT COALESCE(SUM(CASE WHEN UPPER(e.execution_status) IN (%s) THEN 1 ELSE 0 END), 0) AS success_count,
               COALESCE(SUM(CASE WHEN UPPER(e.execution_status) IN (%s) THEN 1 ELSE 0 END), 0) AS failed_count,
               COALESCE(SUM(CASE WHEN UPPER(e.trigger_type) = 'SCHEDULE' THEN 1 ELSE 0 END), 0) AS schedule_count,
               COALESCE(SUM(CASE WHEN UPPER(e.execution_status) NOT IN (%s)
                                  AND COALESCE(e.duration_ms, 0) > 0 THEN e.duration_ms ELSE 0 END), 0) AS duration_total_ms,
               COALESCE(SUM(CASE WHEN UPPER(e.execution_status) NOT IN (%s)
                                  AND COALESCE(e.duration_ms, 0) > 0 THEN 1 ELSE 0 END), 0) AS duration_sample_count
          FROM yak_quality_execution e
         WHERE e.project_id = ?
           AND e.queued_at >= ? AND e.queued_at < ?
        """.formatted(
            SUCCESS_STATUSES,
            FAILED_STATUSES,
            RUNNING_STATUSES,
            RUNNING_STATUSES);
    Metrics aggregate =
        jdbc.queryForObject(
            sql,
            (rs, rowNum) ->
                new Metrics(
                    rs.getLong("success_count"),
                    0L,
                    rs.getLong("failed_count"),
                    rs.getLong("schedule_count"),
                    0L,
                    rs.getLong("duration_total_ms"),
                    rs.getLong("duration_sample_count")),
            projectId,
            start,
            end);
    return (aggregate == null ? Metrics.empty() : aggregate)
        .withRunning(runningAt(projectId, end));
  }

  private long runningAt(long projectId, LocalDateTime point) {
    String sql = """
        SELECT COUNT(*)
          FROM yak_quality_execution e
         WHERE e.project_id = ?
           AND e.queued_at <= ?
           AND (e.finished_at IS NULL OR e.finished_at > ?)
        """;
    Long value = jdbc.queryForObject(sql, Long.class, projectId, point, point);
    return value == null ? 0L : value;
  }

  private List<TrendPoint> trend(
      long projectId,
      LocalDateTime start,
      LocalDateTime end,
      boolean hourly) {
    String occurredAt = "COALESCE(e.started_at, e.queued_at, e.created_at)";
    String bucketHour =
        hourly ? "FLOOR(HOUR(" + occurredAt + ") / 4) * 4" : "0";
    String sql =
        "SELECT DATE("
            + occurredAt
            + ") AS bucket_date, "
            + bucketHour
            + " AS bucket_hour, COUNT(*) AS total "
            + "FROM yak_quality_execution e "
            + "WHERE e.project_id = ? AND e.queued_at >= ? AND e.queued_at < ? "
            + "GROUP BY bucket_date, bucket_hour ORDER BY bucket_date, bucket_hour";
    return jdbc.query(
        sql,
        (rs, rowNum) ->
            new TrendPoint(
                date(rs, "bucket_date").atTime(rs.getInt("bucket_hour"), 0),
                rs.getLong("total")),
        projectId,
        start,
        end);
  }

  private Execution latestForTask(
      long projectId,
      long taskId,
      LocalDateTime start,
      LocalDateTime end) {
    String sql = """
        SELECT CAST(e.monitor_id AS CHAR) AS task_id,
               e.monitor_name AS task_name,
               e.execution_status AS status,
               COALESCE(e.started_at, e.queued_at, e.created_at) AS occurred_at,
               COALESCE(e.duration_ms, 0) AS duration_ms,
               e.execution_no AS execution_id
          FROM yak_quality_execution e
         WHERE e.project_id = ?
           AND e.queued_at >= ? AND e.queued_at < ?
           AND e.monitor_id = ?
         ORDER BY COALESCE(e.started_at, e.queued_at, e.created_at) DESC, e.id DESC
         LIMIT 1
        """;
    List<Execution> rows =
        jdbc.query(
            sql,
            this::execution,
            projectId,
            start,
            end,
            taskId);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Execution execution(ResultSet rs, int rowNum) throws SQLException {
    String taskId = rs.getString("task_id");
    return new Execution(
        taskId,
        text(rs.getString("task_name"), "数据质量任务 #" + taskId),
        normalize(rs.getString("status")),
        localDateTime(rs, "occurred_at"),
        rs.getLong("duration_ms"),
        rs.getString("execution_id"));
  }

  private TaskAggregate taskAggregate(ResultSet rs, int rowNum)
      throws SQLException {
    return new TaskAggregate(
        rs.getLong("task_id"),
        rs.getString("task_name"),
        rs.getLong("run_count"),
        rs.getLong("success_count"),
        rs.getLong("failed_count"),
        localDateTime(rs, "last_run_time"));
  }

  private TaskSummary summary(TaskAggregate aggregate, Execution last) {
    String taskId = String.valueOf(aggregate.taskId());
    return new TaskSummary(
        taskId,
        text(aggregate.taskName(), "数据质量任务 #" + taskId),
        aggregate.lastRunTime(),
        aggregate.runCount(),
        aggregate.successCount(),
        aggregate.failedCount(),
        last == null ? 0L : last.durationMs(),
        last == null ? "UNKNOWN" : last.status(),
        last == null ? null : last.executionId());
  }

  private long currentProjectId() {
    return currentProject.requireProjectId();
  }

  private static LocalDate date(ResultSet rs, String column)
      throws SQLException {
    Date value = rs.getDate(column);
    return value == null ? LocalDate.of(1970, 1, 1) : value.toLocalDate();
  }

  private static LocalDateTime localDateTime(ResultSet rs, String column)
      throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toLocalDateTime();
  }

  private static String normalize(String value) {
    return value == null ? "UNKNOWN" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String text(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record TaskAggregate(
      long taskId,
      String taskName,
      long runCount,
      long successCount,
      long failedCount,
      LocalDateTime lastRunTime) {}
}
