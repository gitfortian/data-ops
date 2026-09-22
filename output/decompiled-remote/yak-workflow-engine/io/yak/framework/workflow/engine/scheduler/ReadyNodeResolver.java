/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.scheduler;

import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.graph.WorkflowGraph;
import io.yak.framework.workflow.engine.scheduler.NodeActivation;

@FunctionalInterface
public interface ReadyNodeResolver {
    public NodeActivation resolve(String var1, WorkflowDefinition var2, WorkflowGraph var3, WorkflowExecution var4, boolean var5);
}

