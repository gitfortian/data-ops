package io.yak.ops.business.dataservice.repository;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL CAS adapter that enforces one shared fixed-window counter across all Yak Ops instances. */
@Slf4j
@Repository
@ConditionalOnDataSourceEnabled
public class DataServiceRateLimitRepositoryAdapter implements DataServiceRateLimitRepository {

  private static final int MAX_CAS_ATTEMPTS = 32;
  private final JdbcTemplate jdbcTemplate;

  public DataServiceRateLimitRepositoryAdapter(
      @Qualifier("yakBusinessDataSource") DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Override
  public boolean tryAcquire(Long apiKeyId, long windowMinute, int limitPerMinute) {
    if (apiKeyId == null || apiKeyId <= 0L) {
      throw new IllegalArgumentException("API Key ID 必须大于 0");
    }
    if (limitPerMinute <= 0) {
      throw new IllegalArgumentException("API Key 每分钟限流必须大于 0");
    }

    for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
      Integer current = currentCount(apiKeyId, windowMinute);
      if (current == null) {
        try {
          jdbcTemplate.update(
              "INSERT INTO yak_ops_data_service_rate_window "
                  + "(api_key_id, window_minute, request_count, update_time) "
                  + "VALUES (?, ?, 1, CURRENT_TIMESTAMP(3))",
              apiKeyId,
              windowMinute);
          return true;
        } catch (DuplicateKeyException concurrentInsert) {
          continue;
        }
      }

      if (current >= limitPerMinute) return false;
      int updated = jdbcTemplate.update(
          "UPDATE yak_ops_data_service_rate_window "
              + "SET request_count = request_count + 1, update_time = CURRENT_TIMESTAMP(3) "
              + "WHERE api_key_id = ? AND window_minute = ? AND request_count = ?",
          apiKeyId,
          windowMinute,
          current);
      if (updated == 1) return true;
    }

    log.warn(
        "Data Service shared rate-limit CAS contention exceeded: apiKeyId={}, windowMinute={}",
        apiKeyId,
        windowMinute);
    throw new IllegalStateException("数据服务全局限流状态竞争过高，请稍后重试");
  }

  @Override
  public void deleteForKey(Long apiKeyId) {
    if (apiKeyId == null) return;
    jdbcTemplate.update(
        "DELETE FROM yak_ops_data_service_rate_window WHERE api_key_id = ?", apiKeyId);
  }

  @Override
  public int deleteBefore(long windowMinute) {
    return jdbcTemplate.update(
        "DELETE FROM yak_ops_data_service_rate_window WHERE window_minute < ?", windowMinute);
  }

  private Integer currentCount(Long apiKeyId, long windowMinute) {
    return jdbcTemplate.query(
        "SELECT request_count FROM yak_ops_data_service_rate_window "
            + "WHERE api_key_id = ? AND window_minute = ?",
        resultSet -> resultSet.next() ? resultSet.getInt(1) : null,
        apiKeyId,
        windowMinute);
  }
}
