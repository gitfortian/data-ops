/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

import io.yak.framework.schedule.api.ScheduleException;

public final class ScheduleProviderException
extends ScheduleException {
    public ScheduleProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public ScheduleProviderException(String message) {
        super(message);
    }
}

