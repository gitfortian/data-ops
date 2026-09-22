/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

public record ScheduleExecutionResult(boolean accepted, String businessExecutionId, String message) {
    public static ScheduleExecutionResult accepted(String businessExecutionId) {
        return new ScheduleExecutionResult(true, businessExecutionId, null);
    }

    public static ScheduleExecutionResult accepted(String businessExecutionId, String message) {
        return new ScheduleExecutionResult(true, businessExecutionId, message);
    }
}

