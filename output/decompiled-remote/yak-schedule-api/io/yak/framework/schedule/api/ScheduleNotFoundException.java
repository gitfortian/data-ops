/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleException;
import io.yak.framework.schedule.api.ScheduleKey;

public final class ScheduleNotFoundException
extends ScheduleException {
    public ScheduleNotFoundException(ScheduleKey key) {
        super("Unknown schedule: " + key.value());
    }
}

