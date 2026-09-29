package io.yak.ops.business.sync.realtime.domain;

import java.time.LocalDateTime;

public record RealtimeJobEventView(
    long id,
    Long deploymentId,
    String eventType,
    String fromState,
    String toState,
    String message,
    LocalDateTime createTime) {}
