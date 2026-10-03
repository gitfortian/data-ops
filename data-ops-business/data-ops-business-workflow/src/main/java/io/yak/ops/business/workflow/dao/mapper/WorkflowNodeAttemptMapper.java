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
  int upsert(WorkflowNodeAttemptPO attempt);
}
