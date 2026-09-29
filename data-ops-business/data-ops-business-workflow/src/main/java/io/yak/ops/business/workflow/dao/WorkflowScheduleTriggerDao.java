package io.yak.ops.business.workflow.dao;

import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import java.time.Instant;
import java.util.List;

/** 工作流调度 Trigger Ledger 数据访问接口。 */
public interface WorkflowScheduleTriggerDao {
  WorkflowScheduleTriggerPO claim(WorkflowScheduleTriggerPO trigger);
  WorkflowScheduleTriggerPO selectByDedupeKey(String dedupeKey);
  WorkflowScheduleTriggerPO selectBySchedulePlan(String scheduleId, Instant plannedFireTime);
  WorkflowScheduleTriggerPO selectByExecutionId(String executionId);
  WorkflowScheduleTriggerPO selectNextWaiting(String workflowId);
  List<WorkflowScheduleTriggerPO> selectPending();

  /** Explicit cross-Project startup dispatcher; callers must restore each Project before recovery. */
  List<Long> selectPendingProjectIdsForRecovery();

  List<WorkflowScheduleTriggerPO> selectQueuedBySchedule(String scheduleId);
  List<WorkflowScheduleTriggerPO> selectByBackfillId(String backfillId);
  List<WorkflowScheduleTriggerPO> selectTriggers(
      String scheduleId, String workflowId, String backfillId, String status, int limit);

  default List<WorkflowScheduleTriggerPO> selectTriggers(
      String scheduleId, String workflowId, String status, int limit) {
    return selectTriggers(scheduleId, workflowId, null, status, limit);
  }

  int update(WorkflowScheduleTriggerPO trigger);
  int bindPreparedExecution(String triggerId, String executionId, String executionStatus);
  void lockWorkflow(String workflowId);
  long countActiveExecutions(String workflowId);
  long countLaunchingTriggers(String workflowId);
  long countWaitingTriggers(String workflowId);
  String selectWorkflowIdByExecution(String executionId);
  String selectExecutionIdByTrigger(String triggerId);
}
