package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 工作流执行实例 Mapper。
 *
 * <p>单表条件查询/更新走 BaseMapper + Wrapper（见 DAO 实现）；只有 upsert、跨表统计与
 * PO 未映射列（audit_carrier_json）才保留注解 SQL。
 */
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecutionPO> {

  @Insert(
      """
      INSERT INTO yak_workflow_execution
        (id, project_id, definition_id, source_execution_id, status, input_json, scheduling_stopped,
         run_started_at, paused_at, paused_duration_ms, created_at, updated_at, ended_at)
      VALUES
        (#{id}, #{projectId}, #{definitionId}, #{sourceExecutionId}, #{status}, #{inputJson}, #{schedulingStopped},
         #{runStartedAt}, #{pausedAt}, #{pausedDurationMs}, #{createdAt}, #{updatedAt}, #{endedAt})
      ON DUPLICATE KEY UPDATE
        project_id = VALUES(project_id),
        definition_id = VALUES(definition_id),
        source_execution_id = VALUES(source_execution_id),
        status = VALUES(status),
        input_json = VALUES(input_json),
        scheduling_stopped = VALUES(scheduling_stopped),
        run_started_at = VALUES(run_started_at),
        paused_at = VALUES(paused_at),
        paused_duration_ms = VALUES(paused_duration_ms),
        updated_at = VALUES(updated_at),
        ended_at = VALUES(ended_at)
      """)
  int upsert(WorkflowExecutionPO execution);

  /** 跨表统计：需要 LEFT JOIN 定义/版本表并按 workflow 归属聚合，Wrapper 无法表达。 */
  @Select(
      """
      SELECT COUNT(*)
      FROM yak_workflow_execution e
      LEFT JOIN yak_workflow_version v
        ON v.id = e.definition_id
       AND v.project_id = #{projectId}
      LEFT JOIN yak_workflow_definition d
        ON d.id = #{workflowId}
       AND d.project_id = #{projectId}
      WHERE e.project_id = #{projectId}
        AND (
          v.workflow_id = #{workflowId}
          OR e.id = d.latest_execution_id
        )
        AND e.status IN ('CREATED', 'RUNNING', 'PAUSING', 'PAUSED', 'RESUMING')
      """)
  long countActiveExecutions(
      @Param("workflowId") String workflowId,
      @Param("projectId") long projectId);

  /** 版本回退取值：COALESCE(e.runtime_metadata_json, v.runtime_metadata_json) 是表达式，不是列。 */
  @Select(
      """
      SELECT COALESCE(e.runtime_metadata_json, v.runtime_metadata_json)
      FROM yak_workflow_execution e
      LEFT JOIN yak_workflow_version v
        ON v.id = e.definition_id
       AND v.project_id = #{projectId}
      WHERE e.id = #{executionId}
        AND e.project_id = #{projectId}
      """)
  String selectEffectiveRuntimeMetadata(
      @Param("executionId") String executionId,
      @Param("projectId") long projectId);

  /** audit_carrier_json 未映射到 WorkflowExecutionPO，无法用 Wrapper 读写。 */
  @Select(
      """
      SELECT audit_carrier_json
      FROM yak_workflow_execution
      WHERE id = #{executionId}
        AND project_id = #{projectId}
      """)
  String selectAuditCarrierJson(
      @Param("executionId") String executionId,
      @Param("projectId") long projectId);

  @Update(
      """
      UPDATE yak_workflow_execution
      SET audit_carrier_json = #{carrierJson}
      WHERE id = #{executionId}
        AND project_id = #{projectId}
      """)
  int updateAuditCarrier(
      @Param("executionId") String executionId,
      @Param("projectId") long projectId,
      @Param("carrierJson") String carrierJson);
}
