/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleKey;
import java.time.Instant;

public record ScheduleOperationAudit(ScheduleKey key, String operation, String operator, Instant operatedAt) {
}

