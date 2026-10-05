package io.yak.ops.business.quality.dao.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import io.yak.ops.business.quality.dao.model.QualityExecutionPO;
import io.yak.ops.business.quality.dao.model.QualityMonitorPO;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.AlertEventRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.ColumnReportRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.DimensionReportRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.FolderRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.MonitorRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.OperationLogRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.ReportOverviewRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.RuleExecutionWorkspaceRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.TableAssetRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.TableMonitorSummaryRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.TemplateRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.TrendPointRow;
import io.yak.ops.business.quality.dao.model.QualityQueryPO.WorkspaceStatsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 质量模块复杂只读查询 Mapper；普通单表 CRUD 仍由 BaseMapper 承担。
 *
 * <p>原 XML 已移除：这批语句含多表 {@code JOIN}、相关子查询、聚合与 {@code UNION ALL}
 * 合成（操作日志），Wrapper 无法表达，SQL 逐字保留在注解里（{@code <script>} 承载原有动态条件）。
 */
@Mapper
public interface QualityQueryMapper {

  @Select(
      """
      <script>
          SELECT 
          t.id, t.template_code, t.template_name, t.description,
          t.rule_type, t.rule_scope, t.quality_dimension, t.parameter_schema_json,
          t.builtin, t.enabled, t.sort_order, t.folder_id, f.folder_name,
          t.template_sql, t.set_flag, t.check_type, t.check_method, t.created_by,
          t.created_at, t.updated_at, COUNT(r.id) AS rule_count


          FROM yak_quality_rule_template t
          LEFT JOIN yak_quality_template_folder f ON f.id = t.folder_id AND f.deleted = 0
          LEFT JOIN yak_quality_rule r ON r.template_id = t.id AND r.deleted = 0

          WHERE t.enabled = 1 AND COALESCE(t.deleted, 0) = 0
          <if test="customOnly != null and customOnly">
            AND t.builtin = 0
          </if>
          <if test="keyword != null and keyword != ''">
            AND (LOWER(t.template_name) LIKE #{keyword}
              OR LOWER(t.template_code) LIKE #{keyword}
              OR LOWER(COALESCE(t.description, '')) LIKE #{keyword})
          </if>
          <if test="dimension != null and dimension != ''">
            AND t.quality_dimension = #{dimension}
          </if>
          <if test="scope != null and scope != ''">
            AND t.rule_scope = #{scope}
          </if>
          <if test="folderFilter != null and folderFilter">
            <choose>
              <when test="folderId != null and folderId != 0">AND t.folder_id = #{folderId}</when>
              <otherwise>AND t.folder_id IS NULL</otherwise>
            </choose>
          </if>

          GROUP BY t.id, t.template_code, t.template_name, t.description,
            t.rule_type, t.rule_scope, t.quality_dimension, t.parameter_schema_json,
            t.builtin, t.enabled, t.sort_order, t.folder_id, f.folder_name,
            t.template_sql, t.set_flag, t.check_type, t.check_method, t.created_by,
            t.created_at, t.updated_at

          ORDER BY t.sort_order ASC, t.updated_at DESC, t.id ASC

      </script>
      """)
  List<TemplateRow> selectTemplates(Map<String, Object> params);

  @Select(
      """
      <script>
          SELECT 
          t.id, t.template_code, t.template_name, t.description,
          t.rule_type, t.rule_scope, t.quality_dimension, t.parameter_schema_json,
          t.builtin, t.enabled, t.sort_order, t.folder_id, f.folder_name,
          t.template_sql, t.set_flag, t.check_type, t.check_method, t.created_by,
          t.created_at, t.updated_at, COUNT(r.id) AS rule_count


          FROM yak_quality_rule_template t
          LEFT JOIN yak_quality_template_folder f ON f.id = t.folder_id AND f.deleted = 0
          LEFT JOIN yak_quality_rule r ON r.template_id = t.id AND r.deleted = 0

          WHERE t.id = #{id} AND COALESCE(t.deleted, 0) = 0
          <if test="customOnly">AND t.builtin = 0</if>

          GROUP BY t.id, t.template_code, t.template_name, t.description,
            t.rule_type, t.rule_scope, t.quality_dimension, t.parameter_schema_json,
            t.builtin, t.enabled, t.sort_order, t.folder_id, f.folder_name,
            t.template_sql, t.set_flag, t.check_type, t.check_method, t.created_by,
            t.created_at, t.updated_at


      </script>
      """)
  TemplateRow selectTemplateById(@Param("id") long id, @Param("customOnly") boolean customOnly);

  @Select(
      """
          SELECT COUNT(*) FROM yak_quality_rule_template
          WHERE builtin = 1 AND enabled = 1 AND COALESCE(deleted, 0) = 0

      """)
  long countSystemTemplates();

  @Select(
      """
          SELECT f.id, f.parent_id, f.folder_name, f.sort_order,
            COUNT(DISTINCT t.id) AS template_count,
            COUNT(DISTINCT child.id) AS child_count,
            f.created_at, f.updated_at
          FROM yak_quality_template_folder f
          LEFT JOIN yak_quality_rule_template t
            ON t.folder_id = f.id AND t.builtin = 0 AND t.deleted = 0 AND t.enabled = 1
          LEFT JOIN yak_quality_template_folder child
            ON child.parent_id = f.id AND child.deleted = 0
          WHERE f.deleted = 0
          GROUP BY f.id, f.parent_id, f.folder_name, f.sort_order, f.created_at, f.updated_at
          ORDER BY f.sort_order ASC, f.folder_name ASC, f.id ASC

      """)
  List<FolderRow> selectFolders();

  @Select(
      """
      <script>
          SELECT COUNT(*) FROM yak_quality_monitor m 
          WHERE m.project_id = #{projectId} AND m.deleted = 0
          <if test="keyword != null and keyword != ''">
            AND (LOWER(m.monitor_name) LIKE #{keyword}
              OR LOWER(COALESCE(m.description, '')) LIKE #{keyword}
              OR LOWER(m.data_source_name) LIKE #{keyword}
              OR LOWER(m.table_name) LIKE #{keyword}
              OR LOWER(m.owner) LIKE #{keyword})
          </if>
          <if test="dataSourceId != null">AND m.data_source_id = #{dataSourceId}</if>
          <if test="databaseFilter != null and databaseFilter">
            AND COALESCE(m.database_name, '') = #{databaseName}
          </if>
          <if test="schemaFilter != null and schemaFilter">
            AND COALESCE(m.schema_name, '') = #{schemaName}
          </if>
          <if test="tableName != null and tableName != ''">AND m.table_name = #{tableName}</if>
          <if test="enabled != null">AND m.enabled = #{enabled}</if>
          <if test="lastResult != null and lastResult != ''">AND m.last_result = #{lastResult}</if>


      </script>
      """)
  long countMonitors(Map<String, Object> params);

  @Select(
      """
      <script>
          SELECT m.id, m.monitor_name, m.description, m.data_source_id, m.data_source_name,
            m.database_name, m.schema_name, m.table_name, m.where_clause, m.owner,
            m.enabled, m.last_result, m.last_execution_no, m.last_run_time,
            m.created_at, m.updated_at,
            (SELECT COUNT(*) FROM yak_quality_rule r WHERE r.monitor_id = m.id AND r.deleted = 0) AS rule_count
          FROM yak_quality_monitor m

          WHERE m.project_id = #{projectId} AND m.deleted = 0
          <if test="keyword != null and keyword != ''">
            AND (LOWER(m.monitor_name) LIKE #{keyword}
              OR LOWER(COALESCE(m.description, '')) LIKE #{keyword}
              OR LOWER(m.data_source_name) LIKE #{keyword}
              OR LOWER(m.table_name) LIKE #{keyword}
              OR LOWER(m.owner) LIKE #{keyword})
          </if>
          <if test="dataSourceId != null">AND m.data_source_id = #{dataSourceId}</if>
          <if test="databaseFilter != null and databaseFilter">
            AND COALESCE(m.database_name, '') = #{databaseName}
          </if>
          <if test="schemaFilter != null and schemaFilter">
            AND COALESCE(m.schema_name, '') = #{schemaName}
          </if>
          <if test="tableName != null and tableName != ''">AND m.table_name = #{tableName}</if>
          <if test="enabled != null">AND m.enabled = #{enabled}</if>
          <if test="lastResult != null and lastResult != ''">AND m.last_result = #{lastResult}</if>

          ORDER BY m.updated_at DESC, m.id DESC
          LIMIT #{limit} OFFSET #{offset}

      </script>
      """)
  List<MonitorRow> selectMonitors(Map<String, Object> params);

  @Select(
      """
          SELECT m.id, m.monitor_name, m.description, m.data_source_id, m.data_source_name,
            m.database_name, m.schema_name, m.table_name, m.where_clause, m.owner,
            m.enabled, m.last_result, m.last_execution_no, m.last_run_time,
            m.created_at, m.updated_at,
            (SELECT COUNT(*) FROM yak_quality_rule r WHERE r.monitor_id = m.id AND r.deleted = 0) AS rule_count
          FROM yak_quality_monitor m
          WHERE m.project_id = #{projectId} AND m.id = #{id} AND m.deleted = 0

      """)
  MonitorRow selectMonitor(@Param("projectId") long projectId, @Param("id") long id);

  @Select(
      """
          SELECT m.id, m.project_id
          FROM yak_quality_monitor m
          INNER JOIN yak_quality_monitor_setting setting ON setting.monitor_id = m.id
          WHERE m.project_id IS NOT NULL
            AND m.deleted = 0
            AND m.enabled = 1
            AND setting.run_mode = 'SCHEDULE'
          ORDER BY m.project_id ASC, m.id ASC

      """)
  List<QualityMonitorPO> selectScheduledMonitorsForRecovery();

  @Select(
      """
      <script>
          SELECT m.table_name,
            SUBSTRING_INDEX(GROUP_CONCAT(m.id ORDER BY m.last_run_time DESC, m.id DESC), ',', 1) AS monitor_id,
            SUBSTRING_INDEX(GROUP_CONCAT(m.monitor_name ORDER BY m.last_run_time DESC, m.id DESC), ',', 1) AS monitor_name,
            COUNT(DISTINCT m.id) AS monitor_count,
            COUNT(DISTINCT CASE WHEN m.enabled = 1 THEN m.id END) AS enabled_monitor_count,
            COUNT(r.id) AS rule_count,
            SUBSTRING_INDEX(GROUP_CONCAT(m.last_execution_no ORDER BY m.last_run_time DESC, m.id DESC), ',', 1) AS last_execution_no,
            SUBSTRING_INDEX(GROUP_CONCAT(m.last_result ORDER BY m.last_run_time DESC, m.id DESC), ',', 1) AS last_result,
            MAX(m.last_run_time) AS last_run_time
          FROM yak_quality_monitor m
          LEFT JOIN yak_quality_rule r ON r.monitor_id = m.id AND r.deleted = 0
          WHERE m.project_id = #{projectId} AND m.deleted = 0 AND m.data_source_id = #{dataSourceId}
          <if test="databaseFilter != null and databaseFilter">AND COALESCE(m.database_name, '') = #{databaseName}</if>
          <if test="schemaFilter != null and schemaFilter">AND COALESCE(m.schema_name, '') = #{schemaName}</if>
          <if test="tableName != null">AND m.table_name = #{tableName}</if>
          GROUP BY m.table_name ORDER BY m.table_name ASC

      </script>
      """)
  @Select(databaseId = "postgresql", value = """
      <script>
          SELECT m.table_name,
            (array_agg(m.id ORDER BY m.last_run_time DESC NULLS LAST, m.id DESC) FILTER (WHERE m.id IS NOT NULL))[1] AS monitor_id,
            (array_agg(m.monitor_name ORDER BY m.last_run_time DESC NULLS LAST, m.id DESC) FILTER (WHERE m.monitor_name IS NOT NULL))[1] AS monitor_name,
            COUNT(DISTINCT m.id) AS monitor_count,
            COUNT(DISTINCT CASE WHEN m.enabled = 1 THEN m.id END) AS enabled_monitor_count,
            COUNT(r.id) AS rule_count,
            (array_agg(m.last_execution_no ORDER BY m.last_run_time DESC NULLS LAST, m.id DESC) FILTER (WHERE m.last_execution_no IS NOT NULL))[1] AS last_execution_no,
            (array_agg(m.last_result ORDER BY m.last_run_time DESC NULLS LAST, m.id DESC) FILTER (WHERE m.last_result IS NOT NULL))[1] AS last_result,
            MAX(m.last_run_time) AS last_run_time
          FROM yak_quality_monitor m
          LEFT JOIN yak_quality_rule r ON r.monitor_id = m.id AND r.deleted = 0
          WHERE m.project_id = #{projectId} AND m.deleted = 0 AND m.data_source_id = #{dataSourceId}
          <if test="databaseFilter != null and databaseFilter">AND COALESCE(m.database_name, '') = #{databaseName}</if>
          <if test="schemaFilter != null and schemaFilter">AND COALESCE(m.schema_name, '') = #{schemaName}</if>
          <if test="tableName != null">AND m.table_name = #{tableName}</if>
          GROUP BY m.table_name ORDER BY m.table_name ASC

      </script>
      """)
  List<TableMonitorSummaryRow> selectTableSummaries(Map<String, Object> params);

  @Select(
      """
          SELECT e.id, e.monitor_id, m.monitor_name, e.execution_no, e.check_result,
            e.alert_level, e.notify_channel, e.delivery_status, e.alert_message, e.created_at
          FROM yak_quality_alert_event e
          INNER JOIN yak_quality_monitor m ON m.id = e.monitor_id AND m.project_id = #{projectId}
          ORDER BY e.created_at DESC, e.id DESC
          LIMIT #{limit}

      """)
  List<AlertEventRow> selectRecentAlertEvents( @Param("projectId") long projectId, @Param("limit") int limit);

  @Select(
      """
          SELECT COUNT(*)
          FROM yak_quality_alert_event e
          INNER JOIN yak_quality_monitor m ON m.id = e.monitor_id AND m.project_id = #{projectId}
          WHERE e.created_at >= #{since}

      """)
  long countAlertEventsSince( @Param("projectId") long projectId, @Param("since") LocalDateTime since);

  @Select(
      """
      <script>
          SELECT COUNT(*) FROM yak_quality_table_asset asset 
          WHERE asset.project_id = #{projectId}
            AND asset.deleted = 0 AND asset.data_source_id = #{dataSourceId}
          <if test="databaseFilter != null and databaseFilter">AND asset.database_name = #{databaseName}</if>
          <if test="schemaFilter != null and schemaFilter">AND asset.schema_name = #{schemaName}</if>
          <if test="keyword != null and keyword != ''">
            AND (LOWER(asset.table_name) LIKE #{keyword} OR LOWER(COALESCE(asset.remarks, '')) LIKE #{keyword})
          </if>


      </script>
      """)
  long countTableAssets(Map<String, Object> params);

  @Select(
      """
      <script>
          SELECT asset.id, asset.data_source_id, asset.data_source_name,
            asset.database_name, asset.schema_name, asset.table_name, asset.table_type,
            asset.remarks, asset.registered_by, asset.registered_at,
            MIN(monitor.id) AS monitor_id, MIN(monitor.monitor_name) AS monitor_name,
            COUNT(DISTINCT monitor.id) AS monitor_count,
            COUNT(DISTINCT rule_row.id) AS rule_count,
            SUBSTRING_INDEX(GROUP_CONCAT(monitor.last_result ORDER BY monitor.last_run_time DESC, monitor.id DESC), ',', 1) AS last_result,
            MAX(monitor.last_run_time) AS last_run_time
          FROM yak_quality_table_asset asset
          LEFT JOIN yak_quality_monitor monitor
            ON monitor.project_id = asset.project_id
            AND monitor.data_source_id = asset.data_source_id
            AND COALESCE(monitor.database_name, '') = asset.database_name
            AND COALESCE(monitor.schema_name, '') = asset.schema_name
            AND monitor.table_name = asset.table_name AND monitor.deleted = 0
          LEFT JOIN yak_quality_rule rule_row ON rule_row.monitor_id = monitor.id AND rule_row.deleted = 0

          WHERE asset.project_id = #{projectId}
            AND asset.deleted = 0 AND asset.data_source_id = #{dataSourceId}
          <if test="databaseFilter != null and databaseFilter">AND asset.database_name = #{databaseName}</if>
          <if test="schemaFilter != null and schemaFilter">AND asset.schema_name = #{schemaName}</if>
          <if test="keyword != null and keyword != ''">
            AND (LOWER(asset.table_name) LIKE #{keyword} OR LOWER(COALESCE(asset.remarks, '')) LIKE #{keyword})
          </if>

          GROUP BY asset.id, asset.data_source_id, asset.data_source_name, asset.database_name,
            asset.schema_name, asset.table_name, asset.table_type, asset.remarks,
            asset.registered_by, asset.registered_at
          ORDER BY asset.table_name ASC, asset.id ASC
          LIMIT #{limit} OFFSET #{offset}

      </script>
      """)
  @Select(databaseId = "postgresql", value = """
      <script>
          SELECT asset.id, asset.data_source_id, asset.data_source_name,
            asset.database_name, asset.schema_name, asset.table_name, asset.table_type,
            asset.remarks, asset.registered_by, asset.registered_at,
            MIN(monitor.id) AS monitor_id, MIN(monitor.monitor_name) AS monitor_name,
            COUNT(DISTINCT monitor.id) AS monitor_count,
            COUNT(DISTINCT rule_row.id) AS rule_count,
            (array_agg(monitor.last_result ORDER BY monitor.last_run_time DESC NULLS LAST, monitor.id DESC) FILTER (WHERE monitor.last_result IS NOT NULL))[1] AS last_result,
            MAX(monitor.last_run_time) AS last_run_time
          FROM yak_quality_table_asset asset
          LEFT JOIN yak_quality_monitor monitor
            ON monitor.project_id = asset.project_id
            AND monitor.data_source_id = asset.data_source_id
            AND COALESCE(monitor.database_name, '') = asset.database_name
            AND COALESCE(monitor.schema_name, '') = asset.schema_name
            AND monitor.table_name = asset.table_name AND monitor.deleted = 0
          LEFT JOIN yak_quality_rule rule_row ON rule_row.monitor_id = monitor.id AND rule_row.deleted = 0

          WHERE asset.project_id = #{projectId}
            AND asset.deleted = 0 AND asset.data_source_id = #{dataSourceId}
          <if test="databaseFilter != null and databaseFilter">AND asset.database_name = #{databaseName}</if>
          <if test="schemaFilter != null and schemaFilter">AND asset.schema_name = #{schemaName}</if>
          <if test="keyword != null and keyword != ''">
            AND (LOWER(asset.table_name) LIKE #{keyword} OR LOWER(COALESCE(asset.remarks, '')) LIKE #{keyword})
          </if>

          GROUP BY asset.id, asset.data_source_id, asset.data_source_name, asset.database_name,
            asset.schema_name, asset.table_name, asset.table_type, asset.remarks,
            asset.registered_by, asset.registered_at
          ORDER BY asset.table_name ASC, asset.id ASC
          LIMIT #{limit} OFFSET #{offset}

      </script>
      """)
  List<TableAssetRow> selectTableAssets(Map<String, Object> params);

  @Select(
      """
          SELECT COUNT(*)
          FROM yak_quality_table_asset asset
          JOIN yak_quality_monitor monitor
            ON monitor.project_id = asset.project_id
            AND monitor.data_source_id = asset.data_source_id
            AND COALESCE(monitor.database_name, '') = asset.database_name
            AND COALESCE(monitor.schema_name, '') = asset.schema_name
            AND monitor.table_name = asset.table_name AND monitor.deleted = 0
          WHERE asset.project_id = #{projectId}
            AND asset.id = #{assetId} AND asset.deleted = 0

      """)
  int countMonitorsForAsset( @Param("projectId") long projectId, @Param("assetId") long assetId);

  @Select(
      """
      <script>
          SELECT COUNT(*) FROM yak_quality_execution e 
          WHERE e.project_id = #{projectId}
          <if test="keyword != null and keyword != ''">
            AND (LOWER(e.execution_no) LIKE #{keyword} OR LOWER(e.monitor_name) LIKE #{keyword}
              OR LOWER(e.data_source_name) LIKE #{keyword} OR LOWER(e.object_name) LIKE #{keyword})
          </if>
          <if test="monitorId != null">AND e.monitor_id = #{monitorId}</if>
          <if test="executionStatus != null and executionStatus != ''">AND e.execution_status = #{executionStatus}</if>
          <if test="checkResult != null and checkResult != ''">AND e.check_result = #{checkResult}</if>


      </script>
      """)
  long countExecutions(Map<String, Object> params);

  @Select(
      """
      <script>
          SELECT 
          e.id, e.project_id, e.execution_no, e.monitor_id, e.monitor_name, e.data_source_id,
          e.data_source_name, e.database_name, e.schema_name, e.table_name, e.object_name,
          e.trigger_type, e.execution_status, e.check_result, e.total_rules, e.passed_rules,
          e.failed_rules, e.error_rules, e.operator_name, e.queued_at, e.started_at,
          e.finished_at, e.duration_ms, e.error_message, e.created_at, e.updated_at
         FROM yak_quality_execution e

          WHERE e.project_id = #{projectId}
          <if test="keyword != null and keyword != ''">
            AND (LOWER(e.execution_no) LIKE #{keyword} OR LOWER(e.monitor_name) LIKE #{keyword}
              OR LOWER(e.data_source_name) LIKE #{keyword} OR LOWER(e.object_name) LIKE #{keyword})
          </if>
          <if test="monitorId != null">AND e.monitor_id = #{monitorId}</if>
          <if test="executionStatus != null and executionStatus != ''">AND e.execution_status = #{executionStatus}</if>
          <if test="checkResult != null and checkResult != ''">AND e.check_result = #{checkResult}</if>

          ORDER BY e.queued_at DESC, e.id DESC LIMIT #{limit} OFFSET #{offset}

      </script>
      """)
  List<QualityExecutionPO> selectExecutions(Map<String, Object> params);

  @Select(
      """
      <script>
          SELECT COUNT(*) FROM yak_quality_execution e 
          WHERE e.project_id = #{projectId}
          <if test="keyword != null and keyword != ''">
            AND (LOWER(e.execution_no) LIKE #{keyword} OR LOWER(e.monitor_name) LIKE #{keyword})
          </if>
          <if test="objectKeyword != null and objectKeyword != ''">
            AND (LOWER(e.object_name) LIKE #{objectKeyword} OR LOWER(e.table_name) LIKE #{objectKeyword}
              OR LOWER(e.data_source_name) LIKE #{objectKeyword})
          </if>
          <if test="dataSourceId != null">AND e.data_source_id = #{dataSourceId}</if>
          <if test="monitorId != null">AND e.monitor_id = #{monitorId}</if>
          <if test="executionStatus != null and executionStatus != ''">AND e.execution_status = #{executionStatus}</if>
          <if test="checkResult != null and checkResult != ''">AND e.check_result = #{checkResult}</if>
          <if test="triggerType != null and triggerType != ''">AND e.trigger_type = #{triggerType}</if>
          <if test="hasIssues != null and hasIssues">AND (e.failed_rules + e.error_rules) &gt; 0</if>
          <if test="hasIssues != null and !hasIssues">AND (e.failed_rules + e.error_rules) = 0</if>
          <if test="queuedAfter != null">AND e.queued_at &gt;= #{queuedAfter}</if>
          <if test="queuedBefore != null">AND e.queued_at &lt;= #{queuedBefore}</if>
          <if test="ruleTypeFilter != null and ruleTypeFilter">
            <choose>
              <when test="ruleTypes != null and ruleTypes.size() > 0">
                AND EXISTS (SELECT 1 FROM yak_quality_rule_execution rf
                  WHERE rf.execution_id = e.id AND rf.rule_type IN
                  <foreach collection="ruleTypes" item="type" open="(" separator="," close=")">#{type}</foreach>)
              </when>
              <otherwise>AND 1 = 0</otherwise>
            </choose>
          </if>


      </script>
      """)
  long countExecutionWorkspace(Map<String, Object> params);

  @Select(
      """
      <script>
          SELECT 
          e.id, e.project_id, e.execution_no, e.monitor_id, e.monitor_name, e.data_source_id,
          e.data_source_name, e.database_name, e.schema_name, e.table_name, e.object_name,
          e.trigger_type, e.execution_status, e.check_result, e.total_rules, e.passed_rules,
          e.failed_rules, e.error_rules, e.operator_name, e.queued_at, e.started_at,
          e.finished_at, e.duration_ms, e.error_message, e.created_at, e.updated_at
         FROM yak_quality_execution e

          WHERE e.project_id = #{projectId}
          <if test="keyword != null and keyword != ''">
            AND (LOWER(e.execution_no) LIKE #{keyword} OR LOWER(e.monitor_name) LIKE #{keyword})
          </if>
          <if test="objectKeyword != null and objectKeyword != ''">
            AND (LOWER(e.object_name) LIKE #{objectKeyword} OR LOWER(e.table_name) LIKE #{objectKeyword}
              OR LOWER(e.data_source_name) LIKE #{objectKeyword})
          </if>
          <if test="dataSourceId != null">AND e.data_source_id = #{dataSourceId}</if>
          <if test="monitorId != null">AND e.monitor_id = #{monitorId}</if>
          <if test="executionStatus != null and executionStatus != ''">AND e.execution_status = #{executionStatus}</if>
          <if test="checkResult != null and checkResult != ''">AND e.check_result = #{checkResult}</if>
          <if test="triggerType != null and triggerType != ''">AND e.trigger_type = #{triggerType}</if>
          <if test="hasIssues != null and hasIssues">AND (e.failed_rules + e.error_rules) &gt; 0</if>
          <if test="hasIssues != null and !hasIssues">AND (e.failed_rules + e.error_rules) = 0</if>
          <if test="queuedAfter != null">AND e.queued_at &gt;= #{queuedAfter}</if>
          <if test="queuedBefore != null">AND e.queued_at &lt;= #{queuedBefore}</if>
          <if test="ruleTypeFilter != null and ruleTypeFilter">
            <choose>
              <when test="ruleTypes != null and ruleTypes.size() > 0">
                AND EXISTS (SELECT 1 FROM yak_quality_rule_execution rf
                  WHERE rf.execution_id = e.id AND rf.rule_type IN
                  <foreach collection="ruleTypes" item="type" open="(" separator="," close=")">#{type}</foreach>)
              </when>
              <otherwise>AND 1 = 0</otherwise>
            </choose>
          </if>

          ORDER BY e.queued_at DESC, e.id DESC LIMIT #{limit} OFFSET #{offset}

      </script>
      """)
  List<QualityExecutionPO> selectExecutionWorkspace(Map<String, Object> params);

  @Select(
      """
      <script>
          SELECT COUNT(*) FROM yak_quality_rule_execution r
          INNER JOIN yak_quality_execution e ON e.id = r.execution_id

          WHERE e.project_id = #{projectId}
          <if test="keyword != null and keyword != ''">
            AND (LOWER(e.execution_no) LIKE #{keyword} OR LOWER(e.monitor_name) LIKE #{keyword}
              OR LOWER(r.rule_name) LIKE #{keyword} OR LOWER(r.template_code) LIKE #{keyword})
          </if>
          <if test="objectKeyword != null and objectKeyword != ''">
            AND (LOWER(e.object_name) LIKE #{objectKeyword} OR LOWER(e.table_name) LIKE #{objectKeyword}
              OR LOWER(e.data_source_name) LIKE #{objectKeyword})
          </if>
          <if test="dataSourceId != null">AND e.data_source_id = #{dataSourceId}</if>
          <if test="monitorId != null">AND e.monitor_id = #{monitorId}</if>
          <if test="executionStatus != null and executionStatus != ''">AND e.execution_status = #{executionStatus}</if>
          <if test="checkResult != null and checkResult != ''">AND r.check_result = #{checkResult}</if>
          <if test="triggerType != null and triggerType != ''">AND e.trigger_type = #{triggerType}</if>
          <if test="hasIssues != null and hasIssues">AND r.check_result IN ('NOT_PASSED', 'ERROR')</if>
          <if test="hasIssues != null and !hasIssues">AND r.check_result NOT IN ('NOT_PASSED', 'ERROR')</if>
          <if test="queuedAfter != null">AND e.queued_at &gt;= #{queuedAfter}</if>
          <if test="queuedBefore != null">AND e.queued_at &lt;= #{queuedBefore}</if>
          <if test="ruleTypeFilter != null and ruleTypeFilter">
            <choose>
              <when test="ruleTypes != null and ruleTypes.size() > 0">
                AND r.rule_type IN
                <foreach collection="ruleTypes" item="type" open="(" separator="," close=")">#{type}</foreach>
              </when>
              <otherwise>AND 1 = 0</otherwise>
            </choose>
          </if>


      </script>
      """)
  long countRuleExecutionWorkspace(Map<String, Object> params);

  @Select(
      """
      <script>
          SELECT r.id AS rule_execution_id, r.rule_id, r.rule_name, r.template_code,
            r.rule_type, r.column_name, r.check_result AS rule_check_result,
            r.metric_value, r.expected_value, r.duration_ms AS rule_duration_ms,
            r.error_message AS rule_error_message,
            e.execution_no, e.monitor_id, e.monitor_name, e.data_source_id,
            e.data_source_name, e.database_name, e.schema_name, e.table_name,
            e.object_name, e.trigger_type, e.execution_status, e.operator_name,
            e.queued_at, e.started_at, e.finished_at
          FROM yak_quality_rule_execution r
          INNER JOIN yak_quality_execution e ON e.id = r.execution_id

          WHERE e.project_id = #{projectId}
          <if test="keyword != null and keyword != ''">
            AND (LOWER(e.execution_no) LIKE #{keyword} OR LOWER(e.monitor_name) LIKE #{keyword}
              OR LOWER(r.rule_name) LIKE #{keyword} OR LOWER(r.template_code) LIKE #{keyword})
          </if>
          <if test="objectKeyword != null and objectKeyword != ''">
            AND (LOWER(e.object_name) LIKE #{objectKeyword} OR LOWER(e.table_name) LIKE #{objectKeyword}
              OR LOWER(e.data_source_name) LIKE #{objectKeyword})
          </if>
          <if test="dataSourceId != null">AND e.data_source_id = #{dataSourceId}</if>
          <if test="monitorId != null">AND e.monitor_id = #{monitorId}</if>
          <if test="executionStatus != null and executionStatus != ''">AND e.execution_status = #{executionStatus}</if>
          <if test="checkResult != null and checkResult != ''">AND r.check_result = #{checkResult}</if>
          <if test="triggerType != null and triggerType != ''">AND e.trigger_type = #{triggerType}</if>
          <if test="hasIssues != null and hasIssues">AND r.check_result IN ('NOT_PASSED', 'ERROR')</if>
          <if test="hasIssues != null and !hasIssues">AND r.check_result NOT IN ('NOT_PASSED', 'ERROR')</if>
          <if test="queuedAfter != null">AND e.queued_at &gt;= #{queuedAfter}</if>
          <if test="queuedBefore != null">AND e.queued_at &lt;= #{queuedBefore}</if>
          <if test="ruleTypeFilter != null and ruleTypeFilter">
            <choose>
              <when test="ruleTypes != null and ruleTypes.size() > 0">
                AND r.rule_type IN
                <foreach collection="ruleTypes" item="type" open="(" separator="," close=")">#{type}</foreach>
              </when>
              <otherwise>AND 1 = 0</otherwise>
            </choose>
          </if>

          ORDER BY e.queued_at DESC, e.id DESC, r.id ASC
          LIMIT #{limit} OFFSET #{offset}

      </script>
      """)
  List<RuleExecutionWorkspaceRow> selectRuleExecutionWorkspace(Map<String, Object> params);

  @Select(
      """
          SELECT
            (SELECT COUNT(*) FROM yak_quality_rule r
              INNER JOIN yak_quality_monitor m ON m.id = r.monitor_id
              WHERE r.monitor_id = #{monitorId} AND r.deleted = 0
                AND m.project_id = #{projectId}) AS rule_count,
            (SELECT COUNT(*) FROM yak_quality_rule r
              INNER JOIN yak_quality_monitor m ON m.id = r.monitor_id
              WHERE r.monitor_id = #{monitorId} AND r.deleted = 0 AND r.enabled = 1
                AND m.project_id = #{projectId}) AS enabled_rule_count,
            (SELECT COUNT(*) FROM yak_quality_execution e
              WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}) AS execution_count,
            (SELECT COUNT(*) FROM yak_quality_execution e
              WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}
                AND e.check_result IN ('NOT_PASSED', 'ERROR')) AS issue_execution_count,
            (SELECT MAX(e.queued_at) FROM yak_quality_execution e
              WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}) AS latest_execution_time

      """)
  WorkspaceStatsRow selectWorkspaceStats( @Param("projectId") long projectId, @Param("monitorId") long monitorId);

  @Select(
      """
          SELECT
            (SELECT COUNT(*) FROM yak_quality_rule r
              INNER JOIN yak_quality_monitor m ON m.id = r.monitor_id
              WHERE r.monitor_id = #{monitorId} AND r.deleted = 0
                AND m.project_id = #{projectId}) AS total_rules,
            (SELECT COUNT(*) FROM yak_quality_rule r
              INNER JOIN yak_quality_monitor m ON m.id = r.monitor_id
              WHERE r.monitor_id = #{monitorId} AND r.deleted = 0 AND r.enabled = 1
                AND m.project_id = #{projectId}) AS enabled_rules,
            COALESCE(SUM(CASE WHEN re.check_result <> 'NOT_RUN' THEN 1 ELSE 0 END), 0) AS executed_rules,
            COALESCE(SUM(CASE WHEN re.check_result = 'NOT_PASSED' THEN 1 ELSE 0 END), 0) AS issue_rules,
            COALESCE(SUM(CASE WHEN re.check_result = 'ERROR' THEN 1 ELSE 0 END), 0) AS error_rules,
            COALESCE(SUM(CASE WHEN re.check_result = 'PASSED' THEN 1 ELSE 0 END), 0) AS passed_rules
          FROM yak_quality_execution e
          LEFT JOIN yak_quality_rule_execution re ON re.execution_id = e.id
          WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}
            AND e.queued_at >= #{reportStart} AND e.queued_at < #{reportEnd}

      """)
  ReportOverviewRow selectReportOverview(Map<String, Object> params);

  @Select(
      """
          SELECT d.dimension, COALESCE(a.total_count, 0) AS total_count,
            COALESCE(a.passed_count, 0) AS passed_count,
            COALESCE(a.not_passed_count, 0) AS not_passed_count,
            COALESCE(a.error_count, 0) AS error_count
          FROM (SELECT DISTINCT COALESCE(NULLIF(r.quality_dimension, ''), '其他') AS dimension
            FROM yak_quality_rule r
            INNER JOIN yak_quality_monitor m ON m.id = r.monitor_id
            WHERE r.monitor_id = #{monitorId} AND r.deleted = 0
              AND m.project_id = #{projectId}) d
          LEFT JOIN (
            SELECT COALESCE(NULLIF(r.quality_dimension, ''), '其他') AS dimension,
              SUM(CASE WHEN re.check_result <> 'NOT_RUN' THEN 1 ELSE 0 END) AS total_count,
              SUM(CASE WHEN re.check_result = 'PASSED' THEN 1 ELSE 0 END) AS passed_count,
              SUM(CASE WHEN re.check_result = 'NOT_PASSED' THEN 1 ELSE 0 END) AS not_passed_count,
              SUM(CASE WHEN re.check_result = 'ERROR' THEN 1 ELSE 0 END) AS error_count
            FROM yak_quality_rule_execution re
            JOIN yak_quality_execution e ON e.id = re.execution_id
            LEFT JOIN yak_quality_rule r ON r.id = re.rule_id
            WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}
            AND e.queued_at >= #{reportStart} AND e.queued_at < #{reportEnd}
            GROUP BY COALESCE(NULLIF(r.quality_dimension, ''), '其他')) a ON a.dimension = d.dimension
          ORDER BY FIELD(d.dimension, '完整性', '准确性', '一致性', '唯一性', '时效性', '有效性'), d.dimension

      """)
  List<DimensionReportRow> selectDimensionReport(Map<String, Object> params);

  @Select(
      """
          SELECT DATE(e.queued_at) AS report_date,
            COALESCE(NULLIF(r.quality_dimension, ''), '其他') AS dimension,
            SUM(CASE WHEN re.check_result <> 'NOT_RUN' THEN 1 ELSE 0 END) AS total_count,
            SUM(CASE WHEN re.check_result = 'PASSED' THEN 1 ELSE 0 END) AS passed_count,
            SUM(CASE WHEN re.check_result IN ('NOT_PASSED', 'ERROR') THEN 1 ELSE 0 END) AS issue_count
          FROM yak_quality_rule_execution re
          JOIN yak_quality_execution e ON e.id = re.execution_id
          LEFT JOIN yak_quality_rule r ON r.id = re.rule_id
          WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}
            AND e.queued_at >= #{trendStart} AND e.queued_at < #{reportEnd}
          GROUP BY DATE(e.queued_at), COALESCE(NULLIF(r.quality_dimension, ''), '其他')
          ORDER BY report_date ASC, dimension ASC

      """)
  List<TrendPointRow> selectTrend(Map<String, Object> params);

  @Select(
      """
          SELECT COALESCE(NULLIF(re.column_name, ''), '表级') AS column_name,
            COALESCE(NULLIF(r.quality_dimension, ''), '其他') AS dimension,
            SUM(CASE WHEN re.check_result <> 'NOT_RUN' THEN 1 ELSE 0 END) AS total_count,
            SUM(CASE WHEN re.check_result = 'PASSED' THEN 1 ELSE 0 END) AS passed_count,
            SUM(CASE WHEN re.check_result IN ('NOT_PASSED', 'ERROR') THEN 1 ELSE 0 END) AS issue_count
          FROM yak_quality_rule_execution re
          JOIN yak_quality_execution e ON e.id = re.execution_id
          LEFT JOIN yak_quality_rule r ON r.id = re.rule_id
          WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}
            AND e.queued_at >= #{reportStart} AND e.queued_at < #{reportEnd}
          GROUP BY COALESCE(NULLIF(re.column_name, ''), '表级'), COALESCE(NULLIF(r.quality_dimension, ''), '其他')
          ORDER BY issue_count DESC, column_name ASC

      """)
  List<ColumnReportRow> selectColumnReport(Map<String, Object> params);

  @Select(
      """
          SELECT 1
            + CASE WHEN m.updated_at > DATE_ADD(m.created_at, INTERVAL 1 SECOND) THEN 1 ELSE 0 END
            + (SELECT COUNT(*) FROM yak_quality_rule r WHERE r.monitor_id = m.id)
            + (SELECT COUNT(*) FROM yak_quality_execution e
                WHERE e.project_id = #{projectId} AND e.monitor_id = m.id)
          FROM yak_quality_monitor m
          WHERE m.project_id = #{projectId} AND m.id = #{monitorId} AND m.deleted = 0

      """)
  @Select(databaseId = "postgresql", value = """
      SELECT 1
        + CASE WHEN m.updated_at > (m.created_at + INTERVAL '1 second') THEN 1 ELSE 0 END
        + (SELECT COUNT(*) FROM yak_quality_rule r WHERE r.monitor_id = m.id)
        + (SELECT COUNT(*) FROM yak_quality_execution e
            WHERE e.project_id = #{projectId} AND e.monitor_id = m.id)
      FROM yak_quality_monitor m
      WHERE m.project_id = #{projectId} AND m.id = #{monitorId} AND m.deleted = 0
      """)
  long countOperationLogs( @Param("projectId") long projectId, @Param("monitorId") long monitorId);

  @Select(
      """
          SELECT log_id, operator_name, operation_time, action_type, action_content FROM (
            SELECT CONCAT('monitor-create-', m.id) AS log_id, m.owner AS operator_name,
              m.created_at AS operation_time, 'CREATE_MONITOR' AS action_type,
              CONCAT('创建质量监控「', m.monitor_name, '」，监控对象：',
                COALESCE(NULLIF(m.database_name, ''), ''),
                CASE WHEN m.database_name IS NULL OR m.database_name = '' THEN '' ELSE '.' END,
                COALESCE(NULLIF(m.schema_name, ''), ''),
                CASE WHEN m.schema_name IS NULL OR m.schema_name = '' THEN '' ELSE '.' END,
                m.table_name) AS action_content
            FROM yak_quality_monitor m
            WHERE m.project_id = #{projectId} AND m.id = #{monitorId} AND m.deleted = 0
            UNION ALL
            SELECT CONCAT('monitor-update-', m.id), m.owner, m.updated_at, 'UPDATE_MONITOR',
              CONCAT('更新质量监控「', m.monitor_name, '」的基础配置、运行设置或问题处理策略')
            FROM yak_quality_monitor m
            WHERE m.project_id = #{projectId} AND m.id = #{monitorId} AND m.deleted = 0
              AND m.updated_at > DATE_ADD(m.created_at, INTERVAL 1 SECOND)
            UNION ALL
            SELECT CONCAT('rule-', r.id), m.owner, r.created_at, 'SAVE_RULE',
              CONCAT('保存质量规则「', r.rule_name, '」，规则模板：', r.template_code,
                '，关联范围：', CASE WHEN r.rule_scope = 'TABLE' THEN '表级' ELSE '字段级' END,
                CASE WHEN r.column_name IS NULL OR r.column_name = '' THEN '' ELSE CONCAT('，字段：', r.column_name) END)
            FROM yak_quality_rule r JOIN yak_quality_monitor m ON m.id = r.monitor_id
            WHERE m.project_id = #{projectId} AND r.monitor_id = #{monitorId}
            UNION ALL
            SELECT CONCAT('execution-', e.id), e.operator_name, e.queued_at, 'RUN_MONITOR',
              CONCAT('触发质量检查，执行编号：', e.execution_no,
                '，触发方式：', e.trigger_type, '，检查结果：', e.check_result)
            FROM yak_quality_execution e
            WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}
          ) logs
          ORDER BY operation_time DESC, log_id DESC LIMIT #{limit} OFFSET #{offset}

      """)
  @Select(databaseId = "postgresql", value = """
      SELECT log_id, operator_name, operation_time, action_type, action_content FROM (
        SELECT CONCAT('monitor-create-', m.id) AS log_id, m.owner AS operator_name,
          m.created_at AS operation_time, 'CREATE_MONITOR' AS action_type,
          CONCAT('创建质量监控「', m.monitor_name, '」，监控对象：',
            COALESCE(NULLIF(m.database_name, ''), ''),
            CASE WHEN m.database_name IS NULL OR m.database_name = '' THEN '' ELSE '.' END,
            COALESCE(NULLIF(m.schema_name, ''), ''),
            CASE WHEN m.schema_name IS NULL OR m.schema_name = '' THEN '' ELSE '.' END,
            m.table_name) AS action_content
        FROM yak_quality_monitor m
        WHERE m.project_id = #{projectId} AND m.id = #{monitorId} AND m.deleted = 0
        UNION ALL
        SELECT CONCAT('monitor-update-', m.id), m.owner, m.updated_at, 'UPDATE_MONITOR',
          CONCAT('更新质量监控「', m.monitor_name, '」的基础配置、运行设置或问题处理策略')
        FROM yak_quality_monitor m
        WHERE m.project_id = #{projectId} AND m.id = #{monitorId} AND m.deleted = 0
          AND m.updated_at > (m.created_at + INTERVAL '1 second')
        UNION ALL
        SELECT CONCAT('rule-', r.id), m.owner, r.created_at, 'SAVE_RULE',
          CONCAT('保存质量规则「', r.rule_name, '」，规则模板：', r.template_code,
            '，关联范围：', CASE WHEN r.rule_scope = 'TABLE' THEN '表级' ELSE '字段级' END,
            CASE WHEN r.column_name IS NULL OR r.column_name = '' THEN '' ELSE CONCAT('，字段：', r.column_name) END)
        FROM yak_quality_rule r JOIN yak_quality_monitor m ON m.id = r.monitor_id
        WHERE m.project_id = #{projectId} AND r.monitor_id = #{monitorId}
        UNION ALL
        SELECT CONCAT('execution-', e.id), e.operator_name, e.queued_at, 'RUN_MONITOR',
          CONCAT('触发质量检查，执行编号：', e.execution_no,
            '，触发方式：', e.trigger_type, '，检查结果：', e.check_result)
        FROM yak_quality_execution e
        WHERE e.project_id = #{projectId} AND e.monitor_id = #{monitorId}
      ) logs
      ORDER BY operation_time DESC, log_id DESC LIMIT #{limit} OFFSET #{offset}
      """)
  List<OperationLogRow> selectOperationLogs(Map<String, Object> params);
}
