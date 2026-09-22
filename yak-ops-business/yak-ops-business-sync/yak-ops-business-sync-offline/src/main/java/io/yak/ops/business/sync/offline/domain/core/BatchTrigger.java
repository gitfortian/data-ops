package io.yak.ops.business.sync.offline.domain.core;

/** Business reason that created a BatchExecution. Retry is an AttemptReason, not a BatchTrigger. */
public enum BatchTrigger {
  MANUAL,
  SCHEDULE,
  WORKFLOW,
  BACKFILL
}
