package io.yak.ops.business.quality.dao.mapper;

import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 需要数据库原子语义而不适合拆成多次 BaseMapper CRUD 的写操作。
 *
 * <p>原 XML 已移除：行锁（{@code FOR UPDATE}）与 MySQL {@code ON DUPLICATE KEY UPDATE}
 * upsert 都不是 Wrapper 能表达的，SQL 逐字保留在注解里。
 */
@Mapper
public interface QualityWriteMapper {

  @Select(
      """
          SELECT id FROM yak_quality_monitor
          WHERE project_id = #{projectId} AND id = #{monitorId} AND deleted = 0
          FOR UPDATE

      """)
  Long lockMonitor( @Param("projectId") long projectId, @Param("monitorId") long monitorId);

  @Insert(
      """
          INSERT INTO yak_quality_table_asset (
            project_id, data_source_id, data_source_name, database_name, schema_name, table_name,
            table_type, remarks, registered_by, registered_at, deleted
          ) VALUES (
            #{projectId}, #{dataSourceId}, #{dataSourceName}, #{databaseName}, #{schemaName}, #{tableName},
            #{tableType}, #{remarks}, #{registeredBy}, CURRENT_TIMESTAMP(3), 0
          )
          ON DUPLICATE KEY UPDATE
            data_source_name = VALUES(data_source_name),
            table_type = VALUES(table_type),
            remarks = VALUES(remarks),
            registered_by = VALUES(registered_by),
            registered_at = CURRENT_TIMESTAMP(3),
            deleted = 0,
            updated_at = CURRENT_TIMESTAMP(3)

      """)
  int upsertTableAsset(QualityTableAssetPO asset);

  @Insert(
      """
          INSERT INTO yak_quality_monitor_setting (
            monitor_id, run_mode, schedule_frequency, schedule_time, schedule_weekday,
            cron_expression, next_run_time, rule_failure_action, notify_enabled,
            notify_channel, notify_target, alert_level
          ) VALUES (
            #{monitorId}, #{runMode}, #{scheduleFrequency}, #{scheduleTime}, #{scheduleWeekday},
            #{cronExpression}, #{nextRunTime}, #{ruleFailureAction}, #{notifyEnabled},
            #{notifyChannel}, #{notifyTarget}, #{alertLevel}
          )
          ON DUPLICATE KEY UPDATE
            run_mode = VALUES(run_mode),
            schedule_frequency = VALUES(schedule_frequency),
            schedule_time = VALUES(schedule_time),
            schedule_weekday = VALUES(schedule_weekday),
            cron_expression = VALUES(cron_expression),
            next_run_time = VALUES(next_run_time),
            rule_failure_action = VALUES(rule_failure_action),
            notify_enabled = VALUES(notify_enabled),
            notify_channel = VALUES(notify_channel),
            notify_target = VALUES(notify_target),
            alert_level = VALUES(alert_level)

      """)
  int upsertMonitorSetting(QualityMonitorSettingPO setting);
}
