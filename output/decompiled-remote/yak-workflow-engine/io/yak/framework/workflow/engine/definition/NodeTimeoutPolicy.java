/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.definition;

import java.time.Duration;
import java.util.Objects;

public record NodeTimeoutPolicy(Duration dispatchTimeout, Duration executionTimeout) {
    public NodeTimeoutPolicy {
        dispatchTimeout = NodeTimeoutPolicy.requireNonNegative(dispatchTimeout, "dispatchTimeout");
        executionTimeout = NodeTimeoutPolicy.requireNonNegative(executionTimeout, "executionTimeout");
    }

    public static NodeTimeoutPolicy none() {
        return new NodeTimeoutPolicy(Duration.ZERO, Duration.ZERO);
    }

    public static NodeTimeoutPolicy of(Duration dispatchTimeout, Duration executionTimeout) {
        return new NodeTimeoutPolicy(dispatchTimeout, executionTimeout);
    }

    public boolean hasDispatchTimeout() {
        return !this.dispatchTimeout.isZero();
    }

    public boolean hasExecutionTimeout() {
        return !this.executionTimeout.isZero();
    }

    private static Duration requireNonNegative(Duration value, String field) {
        Duration duration = Objects.requireNonNullElse(value, Duration.ZERO);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return duration;
    }
}

