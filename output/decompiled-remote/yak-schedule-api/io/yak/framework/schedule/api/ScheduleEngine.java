/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleEngineCapabilities;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTriggerResult;
import java.util.List;
import java.util.Optional;

public interface ScheduleEngine {
    public String type();

    public ScheduleEngineCapabilities capabilities();

    public ScheduleSnapshot save(ScheduleDefinition var1);

    public void pause(ScheduleKey var1);

    public void resume(ScheduleKey var1);

    public void delete(ScheduleKey var1);

    public ScheduleTriggerResult runNow(ScheduleKey var1);

    public Optional<ScheduleSnapshot> get(ScheduleKey var1);

    public List<ScheduleSnapshot> list(String var1);
}

