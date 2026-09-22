package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL-backed offline execution overview projection. */
@Repository
@ConditionalOnOfflineSyncEnabled
public class OfflineExecutionOverviewRepositoryAdapter
    implements OfflineExecutionOverviewRepository {

  private static final String SUCCESS_STATUSES =
      "'SUCCEEDED','SUCCESS','FINISHED','COMPLETED'";
  private static final String FAILED_STATUSES = "'FAILED','LOST'";
  private static final String RUNNING_STATUSES =
      "'CREATED','SUBMITTED','QUEUED','RUNNING'";

  private final JdbcTemplate jdbc;
  private final CurrentProject currentProject;

  public OfflineExecutionOverviewRepositoryAdapter(
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
        SELECT e.job_definition_id AS task_id,
               d.job_name AS task_name,
               e.status,
               COALESCE(e.start_time, e.create_time, e.update_time) AS occurred_at,
               COALESCE(e.duration_millis, 0) AS duration_ms,
               CAST(e.id AS CHAR) AS execution_id
          FROM yak_offline_job_execution e
          LEFT JOIN yak_offline_job_definition d ON d.id = e.job_definition_id
         WHERE e.create_time >= ? AND e.create_time < ?
        """ + scope.sql()
        + " ORDER BY COALESCE(e.start_time, e.create_time, e.update_time) DESC, e.id DESC LIMIT 1";
    List<Execution> rows = jdbc.query(sql, this::execution, args(scope, start, end));
    return rows.isEmpty() ? null : rows.get(0);
  }

  @Override
  public TaskSummary taskSummary(String taskId, LocalDateTime start, LocalDateTime end) {
    if (taskId == null || taskId.isBlank()) return null;
    long id;
    try {
      id = Long.parseLong(taskId);
    } catch (NumberFormatException ignored) {
      return null;
    }

    Scope scope = scope("e");
    String sql = """
        SELECT e.job_definition_id AS task_id,
               MAX(d.job_name) AS task_name,
               COUNT(*) AS run_count,
               SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END) AS success_count,
               SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END) AS failed_count,
               MAX(COALESCE(e.start_time, e.create_time, e.update_time)) AS last_run_time
          FROM yak_offline_job_execution e
          LEFT JOIN yak_offline_job_definition d ON d.id = e.job_definition_id
         WHERE e.create_time >= ? AND e.create_time < ?
           AND e.job_definition_id = ?
        """.formatted(SUCCESS_STATUSES, FAILED_STATUSES)
        + scope.sql()
        + " GROUP BY e.job_definition_id";
    List<TaskAggregate> aggregates =
        jdbc.query(sql, this::taskAggregate, args(scope, start, end, id));
    if (aggregates.isEmpty()) return null;
    return summary(aggregates.get(0), latestForTask(id, start, end));
  }

  @Override
  public List<TaskSummary> recentTasks(LocalDateTime start, LocalDateTime end, int limit) {
    int boundedLimit = Math.max(1, Math.min(limit, 20));
    Scope scope = scope("e");
    String sql = """
        SELECT e.job_definition_id AS task_id,
               MAX(d.job_name) AS task_name,
               COUNT(*) AS run_count,
               SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END) AS success_count,
               SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END) AS failed_count,
               MAX(COALESCE(e.start_time, e.create_time, e.update_time)) AS last_run_time
          FROM yak_offline_job_execution e
          LEFT JOIN yak_offline_job_definition d ON d.id = e.job_definition_id
         WHERE e.create_time >= ? AND e.create_time < ?
        """.formatted(SUCCESS_STATUSES, FAILED_STATUSES)
        + scope.sql()
        + " GROUP BY e.job_definition_id ORDER BY last_run_time DESC LIMIT " + boundedLimit;
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
    Scope scope = scope("d");
    String sql = """
        SELECT CAST(d.id AS CHAR) AS task_id,
               d.job_name AS task_name,
               d.cron_expression,
               d.schedule_last_fire_time,
               d.schedule_next_fire_time
          FROM yak_offline_job_definition d
         WHERE d.schedule_enabled = 1
        """ + scope.sql()
        + " ORDER BY d.schedule_next_fire_time DESC LIMIT " + boundedLimit;
    return jdbc.query(
        sql,
        (rs, rowNum) -> new ScheduleSummary(
            rs.getString("task_id"),
            text(rs.getString("task_name"), "离线同步任务"),
            rs.getString("cron_expression"),
            "ENABLED",
            localDateTime(rs, "schedule_last_fire_time"),
            localDateTime(rs, "schedule_next_fire_time")),
        scope.params().toArray());
  }

  @Override
  public Metrics metrics(LocalDateTime start, LocalDateTime end) {
    Scope scope = scope("e");
    String sql = """
        SELECT COALESCE(SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END), 0) AS success_count,
               COALESCE(SUM(CASE WHEN UPPER(e.status) IN (%s) THEN 1 ELSE 0 END), 0) AS failed_count,
               COALESCE(SUM(CASE WHEN UPPER(e.trigger_type) = 'SCHEDULE' THEN 1 ELSE 0 END), 0) AS schedule_count,
               COALESCE(SUM(COALESCE(e.sink_success_record_count, 0)), 0) AS processed_records,
               COALESCE(SUM(CASE WHEN UPPER(e.status) NOT IN (%s) AND COALESCE(e.duration_millis, 0) > 0 THEN e.duration_millis ELSE 0 END), 0) AS duration_total_ms,
               COALESCE(SUM(CASE WHEN UPPER(e.status) NOT IN (%s) AND COALESCE(e.duration_millis, 0) > 0 THEN 1 ELSE 0 END), 0) AS duration_sample_count
          FROM yak_offline_job_execution e
         WHERE e.create_time >= ? AND e.create_time < ?
        """.formatted(
            SUCCESS_STATUSES, FAILED_STATUSES, RUNNING_STATUSES, RUNNING_STATUSES)
        + scope.sql();
    Metrics aggregate = jdbc.queryForObject(
        sql,
        (rs, rowNum) -> new Metrics(
            rs.getLong("success_count"),
            0L,
            rs.getLong("failed_count"),
            rs.getLong("schedule_count"),
            rs.getLong("processed_records"),
            rs.getLong("duration_total_ms"),
            rs.getLong("duration_sample_count")),
        args(scope, start, end));
    return (aggregate == null ? Metrics.empty() : aggregate).withRunning(runningAt(end));
  }

  private long runningAt(LocalDateTime point) {
    Scope scope = scope("e");
    String sql = """
        SELECT COUNT(*)
          FROM yak_offline_job_execution e
         WHERE e.create_time <= ?
           AND (e.end_time IS NULL OR e.end_time > ?)
        """ + scope.sql();
    Long value = jdbc.queryForObject(sql, Long.class, args(scope, point, point));
    return value == null ? 0L : value;
  }

  private List<TrendPoint> trend(LocalDateTime start, LocalDateTime end, boolean hourly) {
    Scope scope = scope("e");
    String bucketHour = hourly
        ? "FLOOR(HOUR(COALESCE(e.start_time, e.create_time, e.update_time)) / 4) * 4"
        : "0";
    String sql = "SELECT DATE(COALESCE(e.start_time, e.create_time, e.update_time)) AS bucket_date, "
        + bucketHour + " AS bucket_hour, COUNT(*) AS total "
        + "FROM yak_offline_job_execution e "
        + "WHERE e.create_time >= ? AND e.create_time < ? "
        + scope.sql()
        + " GROUP BY bucket_date, bucket_hour ORDER BY bucket_date, bucket_hour";
    return jdbc.query(
        sql,
        (rs, rowNum) -> new TrendPoint(
            date(rs, "bucket_date").atTime(rs.getInt("bucket_hour"), 0),
            rs.getLong("total")),
        args(scope, start, end));
  }

  private Execution latestForTask(long taskId, LocalDateTime start, LocalDateTime end) {
    Scope scope = scope("e");
    String sql = """
        SELECT e.job_definition_id AS task_id,
               d.job_name AS task_name,
               e.status,
               COALESCE(e.start_time, e.create_time, e.update_time) AS occurred_at,
               COALESCE(e.duration_millis, 0) AS duration_ms,
               CAST(e.id AS CHAR) AS execution_id
          FROM yak_offline_job_execution e
          LEFT JOIN yak_offline_job_definition d ON d.id = e.job_definition_id
         WHERE e.create_time >= ? AND e.create_time < ?
           AND e.job_definition_id = ?
        """ + scope.sql()
        + " ORDER BY COALESCE(e.start_time, e.create_time, e.update_time) DESC, e.id DESC LIMIT 1";
    List<Execution> rows = jdbc.query(sql, this::execution, args(scope, start, end, taskId));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private Execution execution(ResultSet rs, int rowNum) throws SQLException {
    String taskId = rs.getString("task_id");
    return new Execution(
        taskId,
        text(rs.getString("task_name"), "离线同步任务 #" + taskId),
        normalize(rs.getString("status")),
        localDateTime(rs, "occurred_at"),
        rs.getLong("duration_ms"),
        rs.getString("execution_id"));
  }

  private TaskAggregate taskAggregate(ResultSet rs, int rowNum) throws SQLException {
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
        text(aggregate.taskName(), "离线同步任务 #" + taskId),
        aggregate.lastRunTime(),
        aggregate.runCount(),
        aggregate.successCount(),
        aggregate.failedCount(),
        last == null ? 0L : last.durationMs(),
        last == null ? "UNKNOWN" : last.status(),
        last == null ? null : last.executionId());
  }

  private Scope scope(String alias) {
    Long projectId = currentProject.requireProjectId();
    return new Scope(" AND " + alias + ".project_id = ?", List.of(projectId));
  }

  private Object[] args(Scope scope, Object... values) {
    List<Object> args = new ArrayList<>(values.length + scope.params().size());
    for (Object value : values) args.add(value);
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
      long taskId,
      String taskName,
      long runCount,
      long successCount,
      long failedCount,
      LocalDateTime lastRunTime) {}

  private record Scope(String sql, List<Object> params) {}
}
