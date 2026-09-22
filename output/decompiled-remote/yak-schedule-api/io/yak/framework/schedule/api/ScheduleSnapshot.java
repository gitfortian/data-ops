/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleStatus;
import java.time.Instant;

public record ScheduleSnapshot(ScheduleDefinition definition, String engineType, String externalId, ScheduleStatus status, Instant nextFireTime, Instant lastFireTime) {
}

