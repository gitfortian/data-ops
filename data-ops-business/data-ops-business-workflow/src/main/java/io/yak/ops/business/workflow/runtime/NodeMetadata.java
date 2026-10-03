package io.yak.ops.business.workflow.runtime;

import java.util.Map;
import io.yak.ops.business.job.task.TaskVersionSnapshot;

record NodeMetadata(
      TaskVersionSnapshot task,
      String triggerRule,
      String failurePolicy,
      int maxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping) {
  }
