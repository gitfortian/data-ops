/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.schedule.api.ScheduleEngine
 *  io.yak.framework.schedule.api.ScheduleException
 */
package io.yak.framework.schedule.core;

import io.yak.framework.schedule.api.ScheduleEngine;
import io.yak.framework.schedule.api.ScheduleException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ScheduleEngineRegistry {
    private final Map<String, ScheduleEngine> engines;

    public ScheduleEngineRegistry(List<ScheduleEngine> candidates) {
        LinkedHashMap<String, ScheduleEngine> registered = new LinkedHashMap<String, ScheduleEngine>();
        for (ScheduleEngine engine : candidates) {
            String type = ScheduleEngineRegistry.normalize(engine.type());
            ScheduleEngine previous = registered.putIfAbsent(type, engine);
            if (previous == null) continue;
            throw new IllegalStateException("Duplicate schedule engine type: " + type + " (" + previous.getClass().getName() + ", " + engine.getClass().getName() + ")");
        }
        this.engines = Collections.unmodifiableMap(registered);
    }

    public ScheduleEngine required(String type) {
        String normalized = ScheduleEngineRegistry.normalize(type);
        ScheduleEngine engine = this.engines.get(normalized);
        if (engine == null) {
            throw new ScheduleException("Schedule engine '" + normalized + "' is not registered. Available: " + String.valueOf(this.engines.keySet()));
        }
        return engine;
    }

    public Set<String> types() {
        return this.engines.keySet();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("schedule engine type must not be blank");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

