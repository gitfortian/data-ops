package io.yak.ops.business.sync.offline.domain.core;

import java.util.Objects;

/** Immutable task definition evidence shared by all attempts in one batch. */
public record ExecutionSnapshot(
    String definitionSnapshot,
    int definitionRevision,
    RetryPolicySnapshot retryPolicy,
    String configDigest,
    String logicalJobSpec) {

  public ExecutionSnapshot {
    definitionSnapshot = requireText(definitionSnapshot, "definitionSnapshot 不能为空");
    if (definitionRevision < 1) {
      throw new IllegalArgumentException("definitionRevision 必须大于 0");
    }
    retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy 不能为空");
    configDigest = requireText(configDigest, "configDigest 不能为空");
    logicalJobSpec = requireText(logicalJobSpec, "logicalJobSpec 不能为空");
  }

  private static String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
