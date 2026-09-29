package io.yak.ops.business.workflow.execution;

import java.util.Optional;

/** Read-only execution evidence required to build Workflow terminal notifications. */
public interface WorkflowExecutionNotificationReader {

  Optional<Snapshot> find(String executionId);

  record Snapshot(
      long projectId,
      String executionId,
      String workflowName,
      String status,
      String errorMessage) {
  }
}
