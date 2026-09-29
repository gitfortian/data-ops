package io.yak.ops.common.bean.vo.workflow;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 工作流实例运维上下文与不可变运行 DAG 拓扑。 */
public record WorkflowInstanceOperationsVO(
    String executionId,
    String workflowId,
    String triggerType,
    String triggerId,
    String scheduleId,
    String backfillId,
    String operationType,
    String sourceExecutionId,
    LocalDate businessDate,
    String scheduleTime,
    String scheduleTimezone,
    Instant plannedFireTime,
    String cronExpression,
    boolean businessDateRerunSupported,
    String businessDateRerunUnavailableReason,
    List<EdgeVO> edges) {

  public WorkflowInstanceOperationsVO {
    edges = edges == null ? List.of() : List.copyOf(edges);
  }

  public record EdgeVO(String source, String target) {
  }
}
