/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import java.util.LinkedHashMap;
import java.util.Map;

public record ScheduleDefinition(ScheduleKey key, String description, ScheduleTrigger trigger, ScheduleTarget target, SchedulePolicy policy, boolean enabled, Map<String, String> metadata) {
    public ScheduleDefinition {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (trigger == null) {
            throw new IllegalArgumentException("trigger must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        policy = policy == null ? SchedulePolicy.defaults() : policy;
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<String, String>(metadata));
    }
}

