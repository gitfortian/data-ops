package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** MySQL maintenance adapter; each hour is rolled up and deleted in one transaction. */
@Repository
@ConditionalOnDataSourceEnabled
public class DataServiceObservabilityMaintenanceRepositoryAdapter
    implements DataServiceObservabilityMaintenanceRepository {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  public DataServiceObservabilityMaintenanceRepositoryAdapter(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Qualifier("yakBusinessTransactionManager") PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public Optional<LocalDateTime> oldestRawHourBefore(LocalDateTime cutoff) {
    LocalDateTime oldest = jdbcTemplate.query(
        "SELECT MIN(create_time) FROM yak_ops_data_service_call_log WHERE create_time < ?",
        resultSet -> resultSet.next() ? resultSet.getObject(1, LocalDateTime.class) : null,
        cutoff);
    return Optional.ofNullable(oldest).map(value -> value.truncatedTo(ChronoUnit.HOURS));
  }

  @Override
  public int rollupAndDeleteHour(LocalDateTime bucketStart) {
    if (bucketStart == null) return 0;
    LocalDateTime start = bucketStart.truncatedTo(ChronoUnit.HOURS);
    LocalDateTime end = start.plusHours(1);
    Integer deleted = transactionTemplate.execute(status -> {
      jdbcTemplate.update(
          """
          INSERT INTO yak_ops_data_service_call_log_hourly
          (project_id, api_id, bucket_hour, service_name, service_path,
           total_calls, success_calls, failure_calls, total_duration_ms, total_rows,
           first_call_at, last_call_at, last_success_at, last_failure_at, update_time)
          SELECT project_id,
                 api_id,
                 ?,
                 MAX(service_name),
                 MAX(service_path),
                 COUNT(*),
                 COALESCE(SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END), 0),
                 COALESCE(SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END), 0),
                 COALESCE(SUM(GREATEST(duration_ms, 0)), 0),
                 COALESCE(SUM(GREATEST(row_count, 0)), 0),
                 MIN(create_time),
                 MAX(create_time),
                 MAX(CASE WHEN success = 1 THEN create_time END),
                 MAX(CASE WHEN success = 0 THEN create_time END),
                 CURRENT_TIMESTAMP(3)
          FROM yak_ops_data_service_call_log
          WHERE create_time >= ? AND create_time < ?
          GROUP BY project_id, api_id
          ON DUPLICATE KEY UPDATE
            service_name = VALUES(service_name),
            service_path = VALUES(service_path),
            total_calls = VALUES(total_calls),
            success_calls = VALUES(success_calls),
            failure_calls = VALUES(failure_calls),
            total_duration_ms = VALUES(total_duration_ms),
            total_rows = VALUES(total_rows),
            first_call_at = VALUES(first_call_at),
            last_call_at = VALUES(last_call_at),
            last_success_at = VALUES(last_success_at),
            last_failure_at = VALUES(last_failure_at),
            update_time = CURRENT_TIMESTAMP(3)
          """,
          start,
          start,
          end);
      return jdbcTemplate.update(
          "DELETE FROM yak_ops_data_service_call_log WHERE create_time >= ? AND create_time < ?",
          start,
          end);
    });
    return deleted == null ? 0 : deleted;
  }

  @Override
  public int deleteRollupsBefore(LocalDateTime cutoff) {
    return jdbcTemplate.update(
        "DELETE FROM yak_ops_data_service_call_log_hourly WHERE bucket_hour < ?", cutoff);
  }
}
