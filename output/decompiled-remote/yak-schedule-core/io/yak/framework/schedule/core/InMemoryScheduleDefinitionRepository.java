/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.schedule.api.ScheduleDefinition
 *  io.yak.framework.schedule.api.ScheduleDefinitionRepository
 *  io.yak.framework.schedule.api.ScheduleKey
 */
package io.yak.framework.schedule.core;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleDefinitionRepository;
import io.yak.framework.schedule.api.ScheduleKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryScheduleDefinitionRepository
implements ScheduleDefinitionRepository {
    private final ConcurrentMap<ScheduleKey, ScheduleDefinition> definitions = new ConcurrentHashMap<ScheduleKey, ScheduleDefinition>();

    public void save(ScheduleDefinition definition) {
        this.definitions.put(definition.key(), definition);
    }

    public Optional<ScheduleDefinition> find(ScheduleKey key) {
        return Optional.ofNullable((ScheduleDefinition)this.definitions.get(key));
    }

    public List<ScheduleDefinition> findByNamespace(String namespace) {
        ArrayList<ScheduleDefinition> result = new ArrayList<ScheduleDefinition>();
        for (ScheduleDefinition definition : this.definitions.values()) {
            if (!definition.key().namespace().equals(namespace)) continue;
            result.add(definition);
        }
        result.sort(Comparator.comparing(item -> item.key().name()));
        return List.copyOf(result);
    }

    public void delete(ScheduleKey key) {
        this.definitions.remove(key);
    }
}

