/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import java.util.List;
import java.util.Optional;

public interface ScheduleDefinitionRepository {
    public void save(ScheduleDefinition var1);

    public Optional<ScheduleDefinition> find(ScheduleKey var1);

    public List<ScheduleDefinition> findByNamespace(String var1);

    public void delete(ScheduleKey var1);
}

