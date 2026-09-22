/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.schedule.api;

public record ScheduleKey(String namespace, String name) {
    public ScheduleKey {
        namespace = ScheduleKey.requireText(namespace, "namespace");
        name = ScheduleKey.requireText(name, "name");
    }

    public String value() {
        return this.namespace + ":" + this.name;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

