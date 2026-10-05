package io.yak.ops.business.development.repository;

import io.yak.framework.common.jdbc.JdbcDatabase;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC adapter for {@link DevelopmentEditorSettingRepository}. */
@Repository
public class DevelopmentEditorSettingRepositoryAdapter
    implements DevelopmentEditorSettingRepository {

  private final JdbcTemplate jdbcTemplate;

  public DevelopmentEditorSettingRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<String> findJson(String userKey) {
    return jdbcTemplate.query(
            "SELECT setting_json FROM yak_dev_editor_setting WHERE user_key = ? LIMIT 1",
            (rs, rowNum) -> rs.getString(1),
            userKey)
        .stream()
        .findFirst();
  }

  @Override
  public void upsertJson(String userKey, String settingJson) {
    jdbcTemplate.update(
        (jdbcTemplate.getDataSource() != null && JdbcDatabase.isPostgresql(jdbcTemplate.getDataSource())) ? """
        INSERT INTO yak_dev_editor_setting (user_key, setting_json, create_time, update_time) VALUES (?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) ON CONFLICT (user_key) DO UPDATE SET
          setting_json = excluded.setting_json,
          update_time = CURRENT_TIMESTAMP
        """ : "INSERT INTO yak_dev_editor_setting (user_key, setting_json, create_time, update_time) "
            + "VALUES (?, ?, NOW(6), NOW(6)) "
            + "ON DUPLICATE KEY UPDATE setting_json = VALUES(setting_json), update_time = NOW(6)",
        userKey,
        settingJson);
  }
}
