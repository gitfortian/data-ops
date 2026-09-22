/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.scheduler;

import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.graph.WorkflowGraph;
import java.util.Collection;
import java.util.List;

@FunctionalInterface
public interface WorkflowScheduler {
    public List<NodeExecution> advance(WorkflowDefinition var1, WorkflowGraph var2, WorkflowExecution var3, Collection<String> var4);
}

