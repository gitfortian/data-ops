/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.policy;

import io.yak.framework.workflow.engine.definition.NodeDefinition;
import io.yak.framework.workflow.engine.execution.NodeExecution;

@FunctionalInterface
public interface RetryDecider {
    public boolean shouldRetry(NodeDefinition var1, NodeExecution var2);
}

