package io.yak.ops.business.dataservice.domain;

/** Persisted runtime resilience policy. Process-local cache/circuit state is not part of this value. */
public record RuntimePolicy(
    boolean cacheEnabled,
    int cacheTtlSeconds,
    int cacheMaxEntries,
    boolean circuitBreakerEnabled,
    int failureThreshold,
    int recoverySeconds) {

  public static RuntimePolicy defaults(boolean creating) {
    return new RuntimePolicy(false, 60, 200, creating, 5, 30);
  }
}
