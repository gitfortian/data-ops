package io.yak.ops.business.sync.realtime.domain;

/** Stable application result for validating the current Flink CDC pipeline. */
public record RealtimeValidationResult(boolean valid, String deliverySemantics) {}
