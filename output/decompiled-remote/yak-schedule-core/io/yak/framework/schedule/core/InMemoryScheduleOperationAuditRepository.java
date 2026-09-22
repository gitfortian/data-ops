/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.schedule.api.ScheduleKey
 *  io.yak.framework.schedule.api.ScheduleOperationAudit
 *  io.yak.framework.schedule.api.ScheduleOperationAuditRepository
 */
package io.yak.framework.schedule.core;

import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleOperationAudit;
import io.yak.framework.schedule.api.ScheduleOperationAuditRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class InMemoryScheduleOperationAuditRepository
implements ScheduleOperationAuditRepository {
    private final int capacity;
    private final ArrayDeque<ScheduleOperationAudit> audits = new ArrayDeque();

    public InMemoryScheduleOperationAuditRepository(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized void save(ScheduleOperationAudit audit) {
        this.audits.addFirst(audit);
        while (this.audits.size() > this.capacity) {
            this.audits.removeLast();
        }
    }

    public synchronized List<ScheduleOperationAudit> find(ScheduleKey key) {
        ArrayList<ScheduleOperationAudit> result = new ArrayList<ScheduleOperationAudit>();
        for (ScheduleOperationAudit audit : this.audits) {
            if (!audit.key().equals((Object)key)) continue;
            result.add(audit);
        }
        return List.copyOf(result);
    }
}

