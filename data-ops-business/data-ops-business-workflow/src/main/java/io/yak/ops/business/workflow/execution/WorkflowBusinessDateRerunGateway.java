package io.yak.ops.business.workflow.execution;

import io.yak.ops.common.bean.dto.workflow.WorkflowBusinessDateRerunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;

/** Narrow execution-to-backfill port for operational business-date reruns. */
public interface WorkflowBusinessDateRerunGateway {

  WorkflowBackfillVO createBusinessDateRerun(
      String sourceExecutionId,
      WorkflowInstanceVO source,
      WorkflowBusinessDateRerunDTO request);
}
