/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.scheduler;

import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.graph.WorkflowGraph;
import io.yak.framework.workflow.engine.scheduler.NodeActivation;
import io.yak.framework.workflow.engine.scheduler.ReadyNodeResolver;
import io.yak.framework.workflow.engine.scheduler.WorkflowScheduler;
import io.yak.framework.workflow.engine.state.NodeExecutionStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public final class DefaultWorkflowScheduler
implements WorkflowScheduler {
    private final ReadyNodeResolver readyNodeResolver;

    public DefaultWorkflowScheduler(ReadyNodeResolver readyNodeResolver) {
        this.readyNodeResolver = readyNodeResolver;
    }

    @Override
    public List<NodeExecution> advance(WorkflowDefinition definition, WorkflowGraph graph, WorkflowExecution execution, Collection<String> candidateNodeIds) {
        ArrayDeque<String> candidates = new ArrayDeque<String>(candidateNodeIds);
        HashSet<String> processed = new HashSet<String>();
        ArrayList<NodeExecution> ready = new ArrayList<NodeExecution>();
        while (!candidates.isEmpty()) {
            NodeExecution node;
            String nodeId = (String)candidates.poll();
            if (processed.contains(nodeId) || (node = execution.node(nodeId)).status() != NodeExecutionStatus.WAITING) continue;
            NodeActivation activation = this.readyNodeResolver.resolve(nodeId, definition, graph, execution, execution.schedulingStopped());
            switch (activation) {
                case WAIT: {
                    break;
                }
                case RUN: {
                    processed.add(nodeId);
                    node.transitionTo(NodeExecutionStatus.READY);
                    ready.add(node);
                    break;
                }
                case SKIP: {
                    processed.add(nodeId);
                    node.transitionTo(NodeExecutionStatus.SKIPPED);
                    candidates.addAll(graph.successors(nodeId));
                    break;
                }
                case BLOCK: {
                    processed.add(nodeId);
                    node.transitionTo(NodeExecutionStatus.UPSTREAM_FAILED);
                    candidates.addAll(graph.successors(nodeId));
                }
            }
        }
        return List.copyOf(ready);
    }
}

