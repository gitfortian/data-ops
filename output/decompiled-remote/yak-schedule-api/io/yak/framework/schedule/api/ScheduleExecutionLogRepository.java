/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleExecutionLog;
import io.yak.framework.schedule.api.ScheduleKey;
import java.util.List;

public interface ScheduleExecutionLogRepository {
    public void save(ScheduleExecutionLog var1);

    public List<ScheduleExecutionLog> find(ScheduleKey var1);
}

