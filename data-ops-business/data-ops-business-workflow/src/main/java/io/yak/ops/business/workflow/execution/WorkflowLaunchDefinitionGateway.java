package io.yak.ops.business.workflow.execution;

import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;

/** Definition capabilities needed by the unified workflow launch corridor. */
public interface WorkflowLaunchDefinitionGateway {

  WorkflowDefinitionVO runPublishedDefinition(String workflowId);

  WorkflowDefinitionVO runConcurrentPublishedDefinition(String workflowId);

  WorkflowDefinitionVO currentDefinition(String workflowId);

  WorkflowDefinitionVO testRunDraft(String workflowId);
}
