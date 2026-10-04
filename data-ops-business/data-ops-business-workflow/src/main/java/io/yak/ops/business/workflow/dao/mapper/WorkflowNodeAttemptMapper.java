package io.yak.ops.business.workflow.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.workflow.dao.model.WorkflowNodeAttemptPO;
import org.apache.ibatis.annotations.Insert;

/** 工作流节点 Attempt Mapper。 */
public interface WorkflowNodeAttemptMapper extends BaseMapper<WorkflowNodeAttemptPO> {

  @Insert(
      """
      INSERT INTO yak_workflow_node_attempt
      (id,node_execution_id,workflow_execution_id,node_id,attempt_no,available_at,status,resume_target_status,started_at,paused_at,paused_duration_ms,ended_at,error_message,failure_reason)
      VALUES
      (#{id},#{nodeExecutionId},#{workflowExecutionId},#{nodeId},#{attemptNo},#{availableAt},#{status},#{resumeTargetStatus},#{startedAt},#{pausedAt},#{pausedDurationMs},#{endedAt},#{errorMessage},#{failureReason})
      ON DUPLICATE KEY UPDATE
      available_at=VALUES(available_at),status=VALUES(status),resume_target_status=VALUES(resume_target_status),started_at=VALUES(started_at),paused_at=VALUES(paused_at),paused_duration_ms=VALUES(paused_duration_ms),ended_at=VALUES(ended_at),error_message=VALUES(error_message),failure_reason=VALUES(failure_reason)
      """)
  @Insert(databaseId = "postgresql", value = """
      INSERT INTO yak_workflow_node_attempt
      (id,node_execution_id,workflow_execution_id,node_id,attempt_no,available_at,status,resume_target_status,started_at,paused_at,paused_duration_ms,ended_at,error_message,failure_reason)
      VALUES
      (#{id},#{nodeExecutionId},#{workflowExecutionId},#{nodeId},#{attemptNo},#{availableAt},#{status},#{resumeTargetStatus},#{startedAt},#{pausedAt},#{pausedDurationMs},#{endedAt},#{errorMessage},#{failureReason})
      ON CONFLICT (id) DO UPDATE SET
        available_at = excluded.available_at,
        status = excluded.status,
        resume_target_status = excluded.resume_target_status,
        started_at = excluded.started_at,
        paused_at = excluded.paused_at,
        paused_duration_ms = excluded.paused_duration_ms,
        ended_at = excluded.ended_at,
        error_message = excluded.error_message,
        failure_reason = excluded.failure_reason
      """)
  int upsert(WorkflowNodeAttemptPO attempt);
}
