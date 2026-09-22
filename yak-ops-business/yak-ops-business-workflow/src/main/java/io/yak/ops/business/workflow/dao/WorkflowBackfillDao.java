package io.yak.ops.business.workflow.dao;

import io.yak.ops.common.bean.po.workflow.WorkflowBackfillPO;
import java.util.List;

/** 工作流 Backfill 批次数据访问接口。 */
public interface WorkflowBackfillDao {
  int insert(WorkflowBackfillPO backfill);
  int update(WorkflowBackfillPO backfill);
  WorkflowBackfillPO select(String id);
  List<WorkflowBackfillPO> selectList(String workflowId, String scheduleId);

  /** Explicit cross-Project startup dispatcher for RUNNING batches. */
  List<ProjectBackfillRef> selectRunningForReconciliation();

  record ProjectBackfillRef(long projectId, String backfillId) {
    public ProjectBackfillRef {
      if (projectId <= 0L) throw new IllegalArgumentException("projectId must be positive");
      if (backfillId == null || backfillId.isBlank()) {
        throw new IllegalArgumentException("backfillId must not be blank");
      }
    }
  }
}
