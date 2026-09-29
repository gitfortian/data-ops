package io.yak.ops.business.sync.realtime.domain;

/** Stable reference to the adjacent Compute Environment context. */
public record RuntimeEnvironmentRef(long id) {
  public RuntimeEnvironmentRef {
    if (id <= 0) throw new IllegalArgumentException("RuntimeEnvironmentRef 必须大于 0");
  }
}
