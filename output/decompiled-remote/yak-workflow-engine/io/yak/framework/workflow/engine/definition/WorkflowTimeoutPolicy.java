/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.definition;

import java.time.Duration;
import java.util.Objects;

public record WorkflowTimeoutPolicy(Duration timeout) {
    public WorkflowTimeoutPolicy {
        timeout = Objects.requireNonNullElse(timeout, Duration.ZERO);
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
    }

    public static WorkflowTimeoutPolicy none() {
        return new WorkflowTimeoutPolicy(Duration.ZERO);
    }

    public static WorkflowTimeoutPolicy of(Duration timeout) {
        return new WorkflowTimeoutPolicy(timeout);
    }

    public boolean enabled() {
        return !this.timeout.isZero();
    }
}

