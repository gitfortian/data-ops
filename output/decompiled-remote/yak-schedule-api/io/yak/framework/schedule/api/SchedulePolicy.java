/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ConcurrencyPolicy;
import io.yak.framework.schedule.api.MisfirePolicy;

public record SchedulePolicy(ConcurrencyPolicy concurrencyPolicy, MisfirePolicy misfirePolicy, int triggerRetries) {
    public SchedulePolicy {
        concurrencyPolicy = concurrencyPolicy == null ? ConcurrencyPolicy.FORBID : concurrencyPolicy;
        MisfirePolicy misfirePolicy2 = misfirePolicy = misfirePolicy == null ? MisfirePolicy.FIRE_ONCE_NOW : misfirePolicy;
        if (triggerRetries < 0) {
            throw new IllegalArgumentException("triggerRetries must not be negative");
        }
    }

    public static SchedulePolicy defaults() {
        return new SchedulePolicy(ConcurrencyPolicy.FORBID, MisfirePolicy.FIRE_ONCE_NOW, 0);
    }
}

