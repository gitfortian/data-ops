/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.security.util;

public final class DatabaseNumberUtils {
    private DatabaseNumberUtils() {
    }

    public static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number)value).longValue();
        }
        throw new IllegalStateException("\u6570\u636e\u5e93 ID \u67e5\u8be2\u7ed3\u679c\u4e0d\u662f\u6570\u5b57\u7c7b\u578b\uff0c\u5b9e\u9645\u7c7b\u578b=" + value.getClass().getName());
    }
}

