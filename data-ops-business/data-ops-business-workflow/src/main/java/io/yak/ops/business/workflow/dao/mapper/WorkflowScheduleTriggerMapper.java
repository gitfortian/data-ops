package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 工作流调度 Trigger Ledger Mapper。 */
public interface WorkflowScheduleTriggerMapper extends BaseMapper<WorkflowScheduleTriggerPO> {

  @Insert("""
      INSERT IGNORE INTO yak_workflow_schedule_trigger
        (id, project_id, schedule_id, workflow_id, backfill_id, trigger_id, dedupe_key, trigger_source,
         planned_fire_time, actual_fire_time, business_date, execution_strategy, misfire_strategy,
         status, workflow_execution_id, execution_status, message, error_message,
         launched_at, completed_at, create_time, update_time)
      VALUES
        (#{id}, #{projectId}, #{scheduleId}, #{workflowId}, #{backfillId}, #{triggerId}, #{dedupeKey}, #{triggerSource},
         #{plannedFireTime}, #{actualFireTime}, #{businessDate}, #{executionStrategy}, #{misfireStrategy},
         #{status}, #{workflowExecutionId}, #{executionStatus}, #{message}, #{errorMessage},
         #{launchedAt}, #{completedAt}, #{createTime}, #{updateTime})
      """)
  int insertIgnore(WorkflowScheduleTriggerPO trigger);

  @Update("""
      UPDATE yak_workflow_schedule_trigger
      SET workflow_execution_id = #{executionId},
          execution_status = #{executionStatus},
          message = 'WorkflowExecution 已首次持久化',
          update_time = CURRENT_TIMESTAMP(3)
      WHERE trigger_id = #{triggerId}
        AND status = 'LAUNCHING'
        AND workflow_execution_id IS NULL
      """)
  int bindPreparedExecution(
      @Param("triggerId") String triggerId,
      @Param("executionId") String executionId,
      @Param("executionStatus") String executionStatus);

  @Select("SELECT id FROM yak_workflow_definition WHERE id = #{workflowId} FOR UPDATE")
  String lockWorkflow(@Param("workflowId") String workflowId);

  @Select("""
      SELECT COUNT(*)
      FROM yak_workflow_execution e
      INNER JOIN yak_workflow_version v ON v.id = e.definition_id
      WHERE v.workflow_id = #{workflowId}
        AND e.status IN ('CREATED', 'RUNNING', 'PAUSING', 'PAUSED', 'RESUMING')
      """)
  long countActiveExecutions(@Param("workflowId") String workflowId);

  @Select("""
      SELECT COUNT(*)
      FROM yak_workflow_schedule_trigger
      WHERE workflow_id = #{workflowId}
        AND status IN ('LAUNCHING', 'REACTIVATING')
      """)
  long countLaunchingTriggers(@Param("workflowId") String workflowId);

  @Select("""
      SELECT COUNT(*)
      FROM yak_workflow_schedule_trigger
      WHERE workflow_id = #{workflowId}
        AND status = 'WAITING'
      """)
  long countWaitingTriggers(@Param("workflowId") String workflowId);

  @Select("""
      SELECT v.workflow_id
      FROM yak_workflow_execution e
      INNER JOIN yak_workflow_version v ON v.id = e.definition_id
      WHERE e.id = #{executionId}
      LIMIT 1
      """)
  String selectWorkflowIdByExecution(@Param("executionId") String executionId);

  @Select("""
      SELECT COALESCE(
        (SELECT t.workflow_execution_id
         FROM yak_workflow_schedule_trigger t
         WHERE t.trigger_id = #{triggerId}
         LIMIT 1),
        (SELECT e.id
         FROM yak_workflow_execution e
         WHERE e.runtime_metadata_json IS NOT NULL
           AND JSON_VALID(e.runtime_metadata_json)
           AND JSON_UNQUOTE(JSON_EXTRACT(e.runtime_metadata_json, '$.triggerId')) = #{triggerId}
         ORDER BY e.created_at DESC
         LIMIT 1)
      )
      """)
  String selectExecutionIdByTrigger(@Param("triggerId") String triggerId);
}
