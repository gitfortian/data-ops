/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.spi;

import java.util.function.Supplier;

@FunctionalInterface
public interface ExecutionLock {
    public <T> T execute(String var1, Supplier<T> var2);
}

