package io.yak.ops.business.sync.offline.domain.core;

/** Target lifecycle state for one execution attempt. */
public enum AttemptStatus {
  CREATED,
  SUBMITTING,
  SUBMITTED,
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELING,
  CANCELED,
  UNKNOWN;

  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELED;
  }

  public boolean blocksNextAttempt() {
    return !isTerminal();
  }
}
