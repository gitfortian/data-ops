/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import java.time.Instant;

public record ScheduleTriggerResult(String triggerId, String externalId, Instant acceptedAt) {
}

