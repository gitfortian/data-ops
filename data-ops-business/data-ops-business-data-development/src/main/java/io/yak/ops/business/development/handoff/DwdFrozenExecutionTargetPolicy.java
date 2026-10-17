package io.yak.ops.business.development.handoff;

import io.yak.ops.business.job.task.TaskVersionSnapshot;

/**
 * Requires a separately approved, installed DataDev/Security target write policy.
 * There is deliberately NO permissive default bean: two flags alone cannot
 * grant SQL target-write permission or bypass DR-07.
 */
public interface DwdFrozenExecutionTargetPolicy {
  void requireWriteAuthorization(
      long projectId, DevelopmentModelTaskBinding binding, TaskVersionSnapshot publishedSnapshot);
}
