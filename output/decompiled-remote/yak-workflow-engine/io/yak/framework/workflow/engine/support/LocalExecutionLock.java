/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.support;

import io.yak.framework.workflow.engine.spi.ExecutionLock;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public final class LocalExecutionLock
implements ExecutionLock {
    private static final int DEFAULT_STRIPE_COUNT = 256;
    private final ReentrantLock[] stripes;

    public LocalExecutionLock() {
        this(256);
    }

    public LocalExecutionLock(int stripeCount) {
        if (stripeCount <= 0) {
            throw new IllegalArgumentException("stripeCount must be greater than zero");
        }
        this.stripes = new ReentrantLock[stripeCount];
        for (int index = 0; index < stripeCount; ++index) {
            this.stripes[index] = new ReentrantLock();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public <T> T execute(String executionId, Supplier<T> action) {
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(action, "action");
        ReentrantLock lock = this.stripe(executionId);
        lock.lock();
        try {
            T t = action.get();
            return t;
        }
        finally {
            lock.unlock();
        }
    }

    int stripeCount() {
        return this.stripes.length;
    }

    private ReentrantLock stripe(String executionId) {
        int hash = executionId.hashCode();
        hash ^= hash >>> 16;
        return this.stripes[Math.floorMod(hash, this.stripes.length)];
    }
}

