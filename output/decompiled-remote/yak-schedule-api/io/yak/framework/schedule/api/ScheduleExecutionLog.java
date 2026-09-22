/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ExecutionStatus;
import io.yak.framework.schedule.api.ScheduleKey;
import java.time.Instant;

public record ScheduleExecutionLog(String triggerId, ScheduleKey key, String engineType, String handler, Instant startedAt, Instant finishedAt, ExecutionStatus status, int attempt, String businessExecutionId, String message) {
}

