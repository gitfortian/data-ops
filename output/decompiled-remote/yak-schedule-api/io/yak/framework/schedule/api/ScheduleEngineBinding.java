/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleKey;
import java.util.Map;

public record ScheduleEngineBinding(ScheduleKey key, String engineType, String externalId, Map<String, String> attributes) {
    public ScheduleEngineBinding {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}

