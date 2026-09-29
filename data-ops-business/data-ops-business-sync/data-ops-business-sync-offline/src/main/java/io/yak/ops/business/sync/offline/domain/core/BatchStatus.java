package io.yak.ops.business.sync.offline.domain.core;

/** Business lifecycle state for one offline batch. */
public enum BatchStatus {
  PENDING,
  RUNNING,
  WAITING_RETRY,
  SUCCEEDED,
  FAILED,
  CANCELED,
  UNKNOWN;

  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELED;
  }

  public boolean occupiesTaskExecutionSlot() {
    return this == RUNNING || this == WAITING_RETRY || this == UNKNOWN;
  }
}
