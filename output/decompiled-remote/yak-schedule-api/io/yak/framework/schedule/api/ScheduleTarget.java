/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import java.util.LinkedHashMap;
import java.util.Map;

public record ScheduleTarget(String handler, Map<String, Object> payload) {
    public ScheduleTarget {
        if (handler == null || handler.isBlank()) {
            throw new IllegalArgumentException("handler must not be blank");
        }
        handler = handler.trim();
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<String, Object>(payload));
    }
}

