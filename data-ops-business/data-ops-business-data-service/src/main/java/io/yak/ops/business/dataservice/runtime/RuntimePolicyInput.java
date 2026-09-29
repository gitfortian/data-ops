package io.yak.ops.business.dataservice.runtime;

public record RuntimePolicyInput(
    Boolean cacheEnabled,
    Integer cacheTtlSeconds,
    Integer cacheMaxEntries,
    Boolean circuitBreakerEnabled,
    Integer failureThreshold,
    Integer recoverySeconds) {}
