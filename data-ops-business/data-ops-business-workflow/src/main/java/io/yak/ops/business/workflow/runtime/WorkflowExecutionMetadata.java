package io.yak.ops.business.workflow.runtime;

import java.util.Map;

record WorkflowExecutionMetadata(
      String name,
      int edgeCount,
      long workflowTimeoutSeconds,
      String failureStrategy,
      String workflowVersionId,
      Integer workflowVersionNo,
      boolean testRun,
      Map<String, NodeMetadata> nodes) {
  }
