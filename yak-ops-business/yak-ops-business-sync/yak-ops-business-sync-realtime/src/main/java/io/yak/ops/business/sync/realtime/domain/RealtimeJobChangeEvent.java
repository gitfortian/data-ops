package io.yak.ops.business.sync.realtime.domain;

/** Lightweight post-commit notification used by the realtime SSE stream. */
public record RealtimeJobChangeEvent(
    long definitionId, String eventType, String fromState, String toState, String message) {}
