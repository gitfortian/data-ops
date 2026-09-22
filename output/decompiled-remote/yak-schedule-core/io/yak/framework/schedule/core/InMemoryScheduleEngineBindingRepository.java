/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.schedule.api.ScheduleEngineBinding
 *  io.yak.framework.schedule.api.ScheduleEngineBindingRepository
 *  io.yak.framework.schedule.api.ScheduleKey
 */
package io.yak.framework.schedule.core;

import io.yak.framework.schedule.api.ScheduleEngineBinding;
import io.yak.framework.schedule.api.ScheduleEngineBindingRepository;
import io.yak.framework.schedule.api.ScheduleKey;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryScheduleEngineBindingRepository
implements ScheduleEngineBindingRepository {
    private final ConcurrentMap<String, ScheduleEngineBinding> bindings = new ConcurrentHashMap<String, ScheduleEngineBinding>();

    public void save(ScheduleEngineBinding binding) {
        this.bindings.put(InMemoryScheduleEngineBindingRepository.key(binding.key(), binding.engineType()), binding);
    }

    public Optional<ScheduleEngineBinding> find(ScheduleKey scheduleKey, String engineType) {
        return Optional.ofNullable((ScheduleEngineBinding)this.bindings.get(InMemoryScheduleEngineBindingRepository.key(scheduleKey, engineType)));
    }

    public void delete(ScheduleKey scheduleKey, String engineType) {
        this.bindings.remove(InMemoryScheduleEngineBindingRepository.key(scheduleKey, engineType));
    }

    private static String key(ScheduleKey scheduleKey, String engineType) {
        return engineType + "|" + scheduleKey.value();
    }
}

