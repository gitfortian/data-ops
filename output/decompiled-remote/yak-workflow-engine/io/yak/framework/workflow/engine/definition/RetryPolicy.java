/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.definition;

import java.time.Duration;
import java.util.Objects;

public record RetryPolicy(int maxAttempts, Duration delay) {
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if ((delay = Objects.requireNonNull(delay, "delay")).isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO);
    }

    public static RetryPolicy fixed(int maxAttempts, Duration delay) {
        return new RetryPolicy(maxAttempts, delay);
    }
}

