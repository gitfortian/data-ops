package io.yak.ops.business.sync.offline.domain.core;

/** Retry policy frozen when a BatchExecution is created. */
public record RetryPolicySnapshot(int maxAttempts, int backoffSeconds) {

  public RetryPolicySnapshot {
    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts 必须大于 0");
    if (backoffSeconds < 0) throw new IllegalArgumentException("backoffSeconds 不能小于 0");
  }
}
