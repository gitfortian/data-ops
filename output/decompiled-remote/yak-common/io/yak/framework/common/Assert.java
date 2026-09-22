/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.common;

import io.yak.framework.common.BusinessException;
import io.yak.framework.common.ErrorCode;
import java.util.Collection;

public final class Assert {
    private Assert() {
    }

    public static void isTrue(boolean expression, ErrorCode errorCode) {
        if (!expression) {
            throw new BusinessException(errorCode);
        }
    }

    public static void notNull(Object value, ErrorCode errorCode) {
        Assert.isTrue(value != null, errorCode);
    }

    public static void notBlank(String value, ErrorCode errorCode) {
        Assert.isTrue(value != null && !value.trim().isEmpty(), errorCode);
    }

    public static void notEmpty(Collection<?> value, ErrorCode errorCode) {
        Assert.isTrue(value != null && !value.isEmpty(), errorCode);
    }
}

