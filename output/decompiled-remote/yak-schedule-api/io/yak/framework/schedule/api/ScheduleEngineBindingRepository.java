/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleEngineBinding;
import io.yak.framework.schedule.api.ScheduleKey;
import java.util.Optional;

public interface ScheduleEngineBindingRepository {
    public void save(ScheduleEngineBinding var1);

    public Optional<ScheduleEngineBinding> find(ScheduleKey var1, String var2);

    public void delete(ScheduleKey var1, String var2);
}

