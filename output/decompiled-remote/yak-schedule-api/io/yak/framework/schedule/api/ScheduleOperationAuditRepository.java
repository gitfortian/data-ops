/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleOperationAudit;
import java.util.List;

public interface ScheduleOperationAuditRepository {
    public void save(ScheduleOperationAudit var1);

    public List<ScheduleOperationAudit> find(ScheduleKey var1);
}

