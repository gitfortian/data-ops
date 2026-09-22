package io.yak.ops.business.workflow.repository;

import io.yak.ops.core.project.CurrentProject;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL-backed Workflow execution overview projection. */
@Repository
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowExecutionOverviewRepositoryAdapter
    implements WorkflowExecutionOverviewRepository {

  private static final String SUCCESS_STATUSES =
      "'SUCCESS','SUCCESS_WITH_WARNINGS','WARNING'";
  private static final String FAILED_STATUSES = "'FAILED','TIMED_OUT'";
  private static final String RUNNING_STATUSES =
      "'CREATED','RUNNING','PAUSING','PAUSED','RESUMING'";

  private final JdbcTemplate jdbc;
  private final CurrentProject currentProject;

  public WorkflowExecutionOverviewRepositoryAdapter(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      CurrentProject currentProject) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.currentProject = currentProject;
  }

  @Override
  public Overview overview(LocalDateTime start, LocalDateTime end, boolean hourlyTrend) {
    return new Overview(metrics(start, end), trend(start, end, hourlyTrend), latest(start, end));
  }

  @Override
  public Execution latest(LocalDateTime start, LocalDateTime end) {
    Scope scope = scope("e");
    String sql = """
        SELECT COALESCE(NULLIF(e.definition_id, ''), e.id) AS task_id,
               e.workflow_name AS task_name,
               e.status,
               COALESCE(e.run_started_at, e.created_at, e.updated_at) AS occurred_at,
               CASE
                 WHEN e.ended_at IS NULL THEN 0
                 ELSE GREATEST(0, TIMESTAMPDIFF(MICROSECOND,
                      COALESCE(e.run_started_at, e.created_at, e.updated_at), e.ended_at) / 1000)
               END AS duration_ms,
               e.id AS execution_id
          FROM yak_workflow_execution e
         WHERE e.created_at >= ? AND e.created_at < ?
        """ + scope.sql()
        + " ORDER BY COALESCE(e.run_started_at, e.created_at, e.updated_at) DESC, e.id DESC LIMIT 1";
    List<Execution> rows = jdbc.query(sql, this::execution, args(scope, start, end));
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public TaskSummary taskSummary(String taskId, LocalDateTime start, LocalDateTime end) {
    if (taskId == null || taskId.isBlank()) return null;
    Scope scope = scope("e");
    String sql = """
        SELECT COALESCE(NULLIF(e.definition_id, ''), e.id) AS task_id,
               MAX(e.workflow_name) AS task_name,
               COUNT(*) AS run_count,
               SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END) AS success_count,
               SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END) AS failed_count,
               MAX(COALESCE(e.run_started_at, e.created_at, e.updated_at)) AS last_run_time
          FROM yak_workflow_execution e
         WHERE e.created_at >= ? AND e.created_at < ?
           AND COALESCE(NULLIF(e.definition_id, ''), e.id) = ?
        """.formatted(SUCCESS_STATUSES, FAILED_STATUSES)
        + scope.sql()
        + " GROUP BY COALESCE(NULLIF(e.definition_id, ''), e.id)";
    List<TaskAggregate> aggregates =
        jdbc.query(sql, this::taskAggregate, args(scope, start, end, taskId));
    if (aggregates.isEmpty()) return null;
    return summary(aggregates.get(0), latestForTask(taskId, start, end));
  }

  @Override
  public List<TaskSummary> recentTasks(LocalDateTime start, LocalDateTime end, int limit) {
    int boundedLimit = Math.max(1, Math.min(limit, 20));
    Scope scope = scope("e");
    String sql = """
        SELECT COALESCE(NULLIF(e.definition_id, ''), e.id) AS task_id,
               MAX(e.workflow_name) AS task_name,
               COUNT(*) AS run_count,
               SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END) AS success_count,
               SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END) AS failed_count,
               MAX(COALESCE(e.run_started_at, e.created_at, e.updated_at)) AS last_run_time
          FROM yak_workflow_execution e
         WHERE e.created_at >= ? AND e.created_at < ?
        """.formatted(SUCCESS_STATUSES, FAILED_STATUSES)
        + scope.sql()
        + " GROUP BY COALESCE(NULLIF(e.definition_id, ''), e.id)"
        + " ORDER BY last_run_time DESC LIMIT " + boundedLimit;
    List<TaskAggregate> aggregates = jdbc.query(sql, this::taskAggregate, args(scope, start, end));
    List<TaskSummary> result = new ArrayList<>(aggregates.size());
    for (TaskAggregate aggregate : aggregates) {
      result.add(summary(aggregate, latestForTask(aggregate.taskId(), start, end)));
    }
    return List.copyOf(result);
  }

  @Override
  public List<ScheduleSummary> schedules(int limit) {
    int boundedLimit = Math.max(1, Math.min(limit, 50));
    Scope scope = scope("s");
    String sql = """
        SELECT s.id AS task_id,
               s.name AS task_name,
               s.cron_expression,
               s.status,
               s.last_fire_time,
               s.next_fire_time
          FROM yak_workflow_schedule s
         WHERE 1 = 1
        """ + scope.sql()
        + " ORDER BY s.next_fire_time DESC LIMIT " + boundedLimit;
    return jdbc.query(
        sql,
        (rs, rowNum) -> new ScheduleSummary(
            rs.getString("task_id"),
            text(rs.getString("task_name"), "工作流调度"),
            rs.getString("cron_expression"),
            normalize(rs.getString("status")),
            localDateTime(rs, "last_fire_time"),
            localDateTime(rs, "next_fire_time")),
        scope.params().toArray());
  }

  @Override
  public Metrics metrics(LocalDateTime start, LocalDateTime end) {
    Scope scope = scope("e");
    String duration = "GREATEST(0, TIMESTAMPDIFF(MICROSECOND, "
        + "COALESCE(e.run_started_at, e.created_at, e.updated_at), e.ended_at) / 1000)";
    String sql = ("""
        SELECT COALESCE(SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END), 0) AS success_count,
               COALESCE(SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END), 0) AS failed_count,
               COALESCE(SUM(CASE WHEN UPPER(e.status) NOT IN (%s)
                                  AND e.ended_at IS NOT NULL
                                  AND %s > 0 THEN %s ELSE 0 END), 0) AS duration_total_ms,
               COALESCE(SUM(CASE WHEN UPPER(e.status) NOT IN (%s)
                                  AND e.ended_at IS NOT NULL
                                  AND %s > 0 THEN 1 ELSE 0 END), 0) AS duration_sample_count
          FROM yak_workflow_execution e
         WHERE e.created_at >= ? AND e.created_at < ?
        """).formatted(
            SUCCESS_STATUSES,
            FAILED_STATUSES,
            RUNNING_STATUSES,
            duration,
            duration,
            RUNNING_STATUSES,
            duration)
        + scope.sql();
    Metrics aggregate = jdbc.queryForObject(
        sql,
        (rs, rowNum) -> new Metrics(
            rs.getLong("success_count"),
            0L,
            rs.getLong("failed_count"),
            0L,
            0L,
            rs.getLong("duration_total_ms"),
            rs.getLong("duration_sample_count")),
        args(scope, start, end));
    return (aggregate == null ? Metrics.empty() : aggregate).withRunning(runningAt(end));
  }

  private long runningAt(LocalDateTime point) {
    Scope scope = scope("e");
    String sql = """
        SELECT COUNT(*)
          FROM yak_workflow_execution e
         WHERE e.created_at <= ?
           AND (e.ended_at IS NULL OR e.ended_at > ?)
        """ + scope.sql();
    Long value = jdbc.queryForObject(sql, Long.class, args(scope, point, point));
    return value == null ? 0L : value;
  }

  private List<TrendPoint> trend(LocalDateTime start, LocalDateTime end, boolean hourly) {
    Scope scope = scope("e");
    String occurredAt = "COALESCE(e.run_started_at, e.created_at, e.updated_at)";
    String bucketHour = hourly ? "FLOOR(HOUR(" + occurredAt + ") / 4) * 4" : "0";
    String sql = "SELECT DATE(" + occurredAt + ") AS bucket_date, "
        + bucketHour + " AS bucket_hour, COUNT(*) AS total "
        + "FROM yak_workflow_execution e "
        + "WHERE e.created_at >= ? AND e.created_at < ? "
        + scope.sql()
        + " GROUP BY bucket_date, bucket_hour ORDER BY bucket_date, bucket_hour";
    return jdbc.query(
        sql,
        (rs, rowNum) -> new TrendPoint(
            date(rs, "bucket_date").atTime(rs.getInt("bucket_hour"), 0),
            rs.getLong("total")),
        args(scope, start, end));
  }

  private Execution latestForTask(String taskId, LocalDateTime start, LocalDateTime end) {
    Scope scope = scope("e");
    String sql = """
        SELECT COALESCE(NULLIF(e.definition_id, ''), e.id) AS task_id,
               e.workflow_name AS task_name,
               e.status,
               COALESCE(e.run_started_at, e.created_at, e.updated_at) AS occurred_at,
               CASE
                 WHEN e.ended_at IS NULL THEN 0
                 ELSE GREATEST(0, TIMESTAMPDIFF(MICROSECOND,
                      COALESCE(e.run_started_at, e.created_at, e.updated_at), e.ended_at) / 1000)
               END AS duration_ms,
               e.id AS execution_id
          FROM yak_workflow_execution e
         WHERE e.created_at >= ? AND e.created_at < ?
           AND COALESCE(NULLIF(e.definition_id, ''), e.id) = ?
        """ + scope.sql()
        + " ORDER BY COALESCE(e.run_started_at, e.created_at, e.updated_at) DESC, e.id DESC LIMIT 1";
    List<Execution> rows = jdbc.query(sql, this::execution, args(scope, start, end, taskId));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Execution execution(ResultSet rs, int rowNum) throws SQLException {
    String taskId = rs.getString("task_id");
    return new Execution(
        taskId,
        text(rs.getString("task_name"), "工作流 #" + taskId),
        normalize(rs.getString("status")),
        localDateTime(rs, "occurred_at"),
        rs.getLong("duration_ms"),
        rs.getString("execution_id"));
  }

  private TaskAggregate taskAggregate(ResultSet rs, int rowNum) throws SQLException {
    return new TaskAggregate(
        rs.getString("task_id"),
        rs.getString("task_name"),
        rs.getLong("run_count"),
        rs.getLong("success_count"),
        rs.getLong("failed_count"),
        localDateTime(rs, "last_run_time"));
  }

  private TaskSummary summary(TaskAggregate aggregate, Execution last) {
    return new TaskSummary(
        aggregate.taskId(),
        text(aggregate.taskName(), "工作流 #" + aggregate.taskId()),
        aggregate.lastRunTime(),
        aggregate.runCount(),
        aggregate.successCount(),
        aggregate.failedCount(),
        last == null ? 0L : last.durationMs(),
        last == null ? "UNKNOWN" : last.status(),
        last == null ? null : last.executionId());
  }

  private Scope scope(String alias) {
    long projectId = currentProject.requireProjectId();
    return new Scope(" AND " + alias + ".project_id = ?", List.of(projectId));
  }

  private Object[] args(Scope scope, Object... values) {
    List<Object> args = new ArrayList<>(values.length + scope.params().size());
    for (Object value : values) {
      if (value instanceof LocalDateTime localDateTime) {
        args.add(Timestamp.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()));
      } else {
        args.add(value);
      }
    }
    args.addAll(scope.params());
    return args.toArray();
  }

  private static LocalDate date(ResultSet rs, String column) throws SQLException {
    Date value = rs.getDate(column);
    return value == null ? LocalDate.of(1970, 1, 1) : value.toLocalDate();
  }

  private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
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
      String taskId,
      String taskName,
      long runCount,
      long successCount,
      long failedCount,
      LocalDateTime lastRunTime) {}

  private record Scope(String sql, List<Object> params) {}
}
