/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleExecutionContext;
import io.yak.framework.schedule.api.ScheduleExecutionResult;

@FunctionalInterface
public interface ScheduleHandler {
    public ScheduleExecutionResult execute(ScheduleExecutionContext var1) throws Exception;
}

