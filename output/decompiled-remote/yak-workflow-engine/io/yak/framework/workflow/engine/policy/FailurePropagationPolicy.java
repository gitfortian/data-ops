/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.policy;

import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.policy.FailureHandlingResult;

@FunctionalInterface
public interface FailurePropagationPolicy {
    public FailureHandlingResult onFinalFailure(WorkflowDefinition var1, WorkflowExecution var2, NodeExecution var3);
}

