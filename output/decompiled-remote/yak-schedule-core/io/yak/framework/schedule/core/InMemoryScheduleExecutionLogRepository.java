/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.yak.framework.schedule.api.ScheduleExecutionLog
 *  io.yak.framework.schedule.api.ScheduleExecutionLogRepository
 *  io.yak.framework.schedule.api.ScheduleKey
 */
package io.yak.framework.schedule.core;

import io.yak.framework.schedule.api.ScheduleExecutionLog;
import io.yak.framework.schedule.api.ScheduleExecutionLogRepository;
import io.yak.framework.schedule.api.ScheduleKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class InMemoryScheduleExecutionLogRepository
implements ScheduleExecutionLogRepository {
    private final int capacity;
    private final ArrayDeque<ScheduleExecutionLog> logs = new ArrayDeque();

    public InMemoryScheduleExecutionLogRepository(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized void save(ScheduleExecutionLog log) {
        this.logs.addFirst(log);
        while (this.logs.size() > this.capacity) {
            this.logs.removeLast();
        }
    }

    public synchronized List<ScheduleExecutionLog> find(ScheduleKey key) {
        ArrayList<ScheduleExecutionLog> result = new ArrayList<ScheduleExecutionLog>();
        for (ScheduleExecutionLog log : this.logs) {
            if (!log.key().equals((Object)key)) continue;
            result.add(log);
        }
        return List.copyOf(result);
    }
}

