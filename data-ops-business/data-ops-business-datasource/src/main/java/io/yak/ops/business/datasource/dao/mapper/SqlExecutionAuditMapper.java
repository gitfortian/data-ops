package io.yak.ops.business.datasource.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditPO;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditQuery;
import io.yak.ops.business.datasource.dao.model.SqlExecutionAuditSummaryRow;
import io.yak.ops.business.datasource.dao.model.SqlStatementTypeCountRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for execution-level SQL audit records and aggregates.
 *
 * <p>原 XML 已移除。这批语句全部是聚合 / 窗口函数 / {@code EXISTS} 子查询 / {@code JOIN + GROUP BY}，
 * Wrapper 无法表达，SQL 逐字保留在注解里（{@code <script>} 承载原有的动态过滤条件）。
 *
 * <p>注意：原 XML 用两个 {@code <sql>} 片段（{@code ExecutionBaseFilters} /
 * {@code ExecutionStatementFilters}）被四条语句 {@code <include>} 复用；注解里没有片段机制，
 * 过滤块在四条语句中各展开一次——改过滤条件时需同步四处。
 */
@Mapper
public interface SqlExecutionAuditMapper extends BaseMapper<SqlExecutionAuditPO> {

  @Select(
      """
      <script>
      SELECT e.*
      FROM yak_ops_sql_execution e
      <where>
          1 = 1
          AND e.project_id = #{query.projectId}
          <if test="query.executionId != null">
              AND e.execution_id = #{query.executionId}
          </if>
          <if test="query.dataSourceId != null">
              AND e.data_source_id = #{query.dataSourceId}
          </if>
          <if test="query.caller != null">
              AND e.caller = #{query.caller}
          </if>
          <if test="query.callerReference != null">
              AND e.caller_reference = #{query.callerReference}
          </if>
          <if test="query.operatorName != null">
              AND e.operator_name LIKE CONCAT('%', COALESCE(#{query.operatorName}, ''), '%')
          </if>
          <if test="query.status != null">
              AND e.status = #{query.status}
          </if>
          <if test="query.transactionMode != null">
              AND e.transaction_mode = #{query.transactionMode}
          </if>
          <if test="query.minDurationMs != null">
              AND e.duration_ms &gt;= #{query.minDurationMs}
          </if>
          <if test="query.startedFrom != null">
              AND e.started_at &gt;= #{query.startedFrom}
          </if>
          <if test="query.startedTo != null">
              AND e.started_at &lt;= #{query.startedTo}
          </if>
          <if test="query.statementType != null or query.sqlFingerprint != null">
              AND EXISTS (
                  SELECT 1
                  FROM yak_ops_sql_statement_execution sf
                  WHERE sf.execution_id = e.execution_id
                  <if test="query.statementType != null">
                      AND sf.statement_type = #{query.statementType}
                  </if>
                  <if test="query.sqlFingerprint != null">
                      AND sf.sql_fingerprint = #{query.sqlFingerprint}
                  </if>
              )
          </if>
      </where>
      ORDER BY e.started_at DESC, e.id DESC
      </script>
      """)
  IPage<SqlExecutionAuditPO> selectAuditPage(
      Page<SqlExecutionAuditPO> page,
      @Param("query") SqlExecutionAuditQuery query);

  @Select(
      """
      <script>
      SELECT COUNT(*) AS total,
             COALESCE(SUM(CASE WHEN e.status = 'SUCCEEDED' THEN 1 ELSE 0 END), 0) AS succeeded,
             COALESCE(SUM(CASE WHEN e.status = 'FAILED' THEN 1 ELSE 0 END), 0) AS failed,
             COALESCE(SUM(CASE WHEN e.status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelled,
             COALESCE(SUM(CASE WHEN e.status = 'TIMED_OUT' THEN 1 ELSE 0 END), 0) AS timed_out,
             COALESCE(AVG(e.duration_ms), 0) AS avg_duration_ms,
             COALESCE(MAX(e.duration_ms), 0) AS max_duration_ms,
             COALESCE(SUM(e.returned_rows), 0) AS returned_rows,
             COALESCE(SUM(e.affected_rows), 0) AS affected_rows
      FROM yak_ops_sql_execution e
      <where>
          1 = 1
          AND e.project_id = #{query.projectId}
          <if test="query.executionId != null">
              AND e.execution_id = #{query.executionId}
          </if>
          <if test="query.dataSourceId != null">
              AND e.data_source_id = #{query.dataSourceId}
          </if>
          <if test="query.caller != null">
              AND e.caller = #{query.caller}
          </if>
          <if test="query.callerReference != null">
              AND e.caller_reference = #{query.callerReference}
          </if>
          <if test="query.operatorName != null">
              AND e.operator_name LIKE CONCAT('%', COALESCE(#{query.operatorName}, ''), '%')
          </if>
          <if test="query.status != null">
              AND e.status = #{query.status}
          </if>
          <if test="query.transactionMode != null">
              AND e.transaction_mode = #{query.transactionMode}
          </if>
          <if test="query.minDurationMs != null">
              AND e.duration_ms &gt;= #{query.minDurationMs}
          </if>
          <if test="query.startedFrom != null">
              AND e.started_at &gt;= #{query.startedFrom}
          </if>
          <if test="query.startedTo != null">
              AND e.started_at &lt;= #{query.startedTo}
          </if>
          <if test="query.statementType != null or query.sqlFingerprint != null">
              AND EXISTS (
                  SELECT 1
                  FROM yak_ops_sql_statement_execution sf
                  WHERE sf.execution_id = e.execution_id
                  <if test="query.statementType != null">
                      AND sf.statement_type = #{query.statementType}
                  </if>
                  <if test="query.sqlFingerprint != null">
                      AND sf.sql_fingerprint = #{query.sqlFingerprint}
                  </if>
              )
          </if>
      </where>
      </script>
      """)
  SqlExecutionAuditSummaryRow selectAuditSummary(@Param("query") SqlExecutionAuditQuery query);

  /** P95 用窗口函数（{@code ROW_NUMBER() OVER}）与派生表，Wrapper 无对应表达。 */
  @Select(
      """
      <script>
      SELECT ranked.duration_ms
      FROM (
          SELECT e.duration_ms,
                 ROW_NUMBER() OVER (ORDER BY e.duration_ms) AS row_number_value,
                 COUNT(*) OVER () AS total_count
          FROM yak_ops_sql_execution e
          <where>
              1 = 1
              AND e.project_id = #{query.projectId}
              <if test="query.executionId != null">
                  AND e.execution_id = #{query.executionId}
              </if>
              <if test="query.dataSourceId != null">
                  AND e.data_source_id = #{query.dataSourceId}
              </if>
              <if test="query.caller != null">
                  AND e.caller = #{query.caller}
              </if>
              <if test="query.callerReference != null">
                  AND e.caller_reference = #{query.callerReference}
              </if>
              <if test="query.operatorName != null">
                  AND e.operator_name LIKE CONCAT('%', COALESCE(#{query.operatorName}, ''), '%')
              </if>
              <if test="query.status != null">
                  AND e.status = #{query.status}
              </if>
              <if test="query.transactionMode != null">
                  AND e.transaction_mode = #{query.transactionMode}
              </if>
              <if test="query.minDurationMs != null">
                  AND e.duration_ms &gt;= #{query.minDurationMs}
              </if>
              <if test="query.startedFrom != null">
                  AND e.started_at &gt;= #{query.startedFrom}
              </if>
              <if test="query.startedTo != null">
                  AND e.started_at &lt;= #{query.startedTo}
              </if>
              <if test="query.statementType != null or query.sqlFingerprint != null">
                  AND EXISTS (
                      SELECT 1
                      FROM yak_ops_sql_statement_execution sf
                      WHERE sf.execution_id = e.execution_id
                      <if test="query.statementType != null">
                          AND sf.statement_type = #{query.statementType}
                      </if>
                      <if test="query.sqlFingerprint != null">
                          AND sf.sql_fingerprint = #{query.sqlFingerprint}
                      </if>
                  )
              </if>
          </where>
      ) ranked
      WHERE ranked.row_number_value &gt;= CEIL(ranked.total_count * 0.95)
      ORDER BY ranked.row_number_value
      LIMIT 1
      </script>
      """)
  Long selectP95DurationMs(@Param("query") SqlExecutionAuditQuery query);

  @Select(
      """
      <script>
      SELECT s.statement_type AS statement_type,
             COUNT(*) AS count
      FROM yak_ops_sql_statement_execution s
      INNER JOIN yak_ops_sql_execution e ON e.execution_id = s.execution_id
      <where>
          1 = 1
          AND e.project_id = #{query.projectId}
          <if test="query.executionId != null">
              AND e.execution_id = #{query.executionId}
          </if>
          <if test="query.dataSourceId != null">
              AND e.data_source_id = #{query.dataSourceId}
          </if>
          <if test="query.caller != null">
              AND e.caller = #{query.caller}
          </if>
          <if test="query.callerReference != null">
              AND e.caller_reference = #{query.callerReference}
          </if>
          <if test="query.operatorName != null">
              AND e.operator_name LIKE CONCAT('%', COALESCE(#{query.operatorName}, ''), '%')
          </if>
          <if test="query.status != null">
              AND e.status = #{query.status}
          </if>
          <if test="query.transactionMode != null">
              AND e.transaction_mode = #{query.transactionMode}
          </if>
          <if test="query.minDurationMs != null">
              AND e.duration_ms &gt;= #{query.minDurationMs}
          </if>
          <if test="query.startedFrom != null">
              AND e.started_at &gt;= #{query.startedFrom}
          </if>
          <if test="query.startedTo != null">
              AND e.started_at &lt;= #{query.startedTo}
          </if>
          <if test="query.statementType != null">
              AND s.statement_type = #{query.statementType}
          </if>
          <if test="query.sqlFingerprint != null">
              AND s.sql_fingerprint = #{query.sqlFingerprint}
          </if>
      </where>
      GROUP BY s.statement_type
      ORDER BY count DESC, s.statement_type ASC
      </script>
      """)
  List<SqlStatementTypeCountRow> selectStatementTypeCounts(
      @Param("query") SqlExecutionAuditQuery query);
}
