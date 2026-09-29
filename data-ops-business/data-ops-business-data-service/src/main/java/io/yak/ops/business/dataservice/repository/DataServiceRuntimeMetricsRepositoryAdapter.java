package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL projection over raw invocation evidence plus hourly rollups. */
@Repository
@ConditionalOnDataSourceEnabled
public class DataServiceRuntimeMetricsRepositoryAdapter
    implements DataServiceRuntimeMetricsRepository {

  private final JdbcTemplate jdbcTemplate;
  private final CurrentProject currentProject;

  public DataServiceRuntimeMetricsRepositoryAdapter(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      CurrentProject currentProject) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.currentProject = currentProject;
  }

  @Override
  public Metrics load(Long apiId, int durationSampleSize) {
    if (apiId == null || apiId <= 0L) throw new IllegalArgumentException("数据服务 ID 必须大于 0");
    Long projectId = currentProject.requireProjectId();
    Aggregate aggregate = jdbcTemplate.queryForObject(
        """
        SELECT
          COALESCE(SUM(metrics.total_calls), 0) AS total_calls,
          COALESCE(SUM(metrics.success_calls), 0) AS success_calls,
          COALESCE(SUM(metrics.failure_calls), 0) AS failure_calls,
          COALESCE(SUM(metrics.total_duration_ms), 0) AS total_duration_ms,
          MAX(metrics.last_success_at) AS last_success_at,
          MAX(metrics.last_failure_at) AS last_failure_at
        FROM (
          SELECT COUNT(*) AS total_calls,
                 COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0) AS success_calls,
                 COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0) AS failure_calls,
                 COALESCE(SUM(GREATEST(duration_ms, 0)), 0) AS total_duration_ms,
                 MAX(CASE WHEN success = 1 THEN create_time END) AS last_success_at,
                 MAX(CASE WHEN success = 0 THEN create_time END) AS last_failure_at
          FROM yak_ops_data_service_call_log
          WHERE project_id = ? AND api_id = ?
          UNION ALL
          SELECT COALESCE(SUM(total_calls), 0),
                 COALESCE(SUM(success_calls), 0),
                 COALESCE(SUM(failure_calls), 0),
                 COALESCE(SUM(total_duration_ms), 0),
                 MAX(last_success_at),
                 MAX(last_failure_at)
          FROM yak_ops_data_service_call_log_hourly
          WHERE project_id = ? AND api_id = ?
        ) metrics
        """,
        (resultSet, rowNum) -> new Aggregate(
            resultSet.getLong("total_calls"),
            resultSet.getLong("success_calls"),
            resultSet.getLong("failure_calls"),
            resultSet.getLong("total_duration_ms"),
            instant(resultSet.getTimestamp("last_success_at")),
            instant(resultSet.getTimestamp("last_failure_at"))),
        projectId,
        apiId,
        projectId,
        apiId);

    int sampleSize = Math.max(1, Math.min(1_024, durationSampleSize));
    List<Long> durations = jdbcTemplate.query(
        "SELECT duration_ms FROM yak_ops_data_service_call_log "
            + "WHERE project_id = ? AND api_id = ? "
            + "ORDER BY create_time DESC, id DESC LIMIT " + sampleSize,
        (resultSet, rowNum) -> Math.max(0L, resultSet.getLong(1)),
        projectId,
        apiId);

    Aggregate value = aggregate == null ? Aggregate.empty() : aggregate;
    return new Metrics(
        value.totalCalls(),
        value.successCalls(),
        value.failureCalls(),
        value.totalDurationMs(),
        List.copyOf(durations),
        value.lastSuccessAt(),
        value.lastFailureAt());
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }

  private record Aggregate(
      long totalCalls,
      long successCalls,
      long failureCalls,
      long totalDurationMs,
      Instant lastSuccessAt,
      Instant lastFailureAt) {
    static Aggregate empty() { return new Aggregate(0L, 0L, 0L, 0L, null, null); }
  }
}
