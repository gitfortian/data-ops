package io.yak.ops.business.workflow.schedule.trigger;

import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.time.LocalDate;

/** Backfill-specific trigger creation and launch kept behind the Trigger Ledger boundary. */
public interface WorkflowBackfillTriggerGateway {

  WorkflowScheduleTriggerPO createTrigger(
      String backfillId,
      LocalDate businessDate,
      Instant plannedFireTime);

  boolean runnable(String backfillId);

  WorkflowInstanceVO launch(WorkflowScheduleTriggerPO trigger);
}
