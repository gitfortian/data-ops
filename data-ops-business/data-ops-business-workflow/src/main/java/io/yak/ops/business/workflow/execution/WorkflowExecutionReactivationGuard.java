package io.yak.ops.business.workflow.execution;

import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.function.Supplier;

/** Narrow guard applied before an existing WorkflowExecution is reactivated in place. */
public interface WorkflowExecutionReactivationGuard {

  WorkflowInstanceVO reactivateExecution(
      String executionId,
      String operation,
      Supplier<WorkflowInstanceVO> action);
}
