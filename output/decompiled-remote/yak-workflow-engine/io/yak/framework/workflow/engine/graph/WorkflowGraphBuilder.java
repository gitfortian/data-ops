/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.graph;

import io.yak.framework.workflow.engine.definition.EdgeDefinition;
import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.graph.DefaultWorkflowGraph;
import io.yak.framework.workflow.engine.graph.WorkflowGraph;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

public final class WorkflowGraphBuilder {
    public WorkflowGraph build(WorkflowDefinition definition) {
        LinkedHashSet<String> nodes = new LinkedHashSet<String>(definition.nodes().keySet());
        LinkedHashMap<String, Set<String>> successors = new LinkedHashMap<String, Set<String>>();
        LinkedHashMap<String, Set<String>> predecessors = new LinkedHashMap<String, Set<String>>();
        for (String node : nodes) {
            successors.put(node, new LinkedHashSet());
            predecessors.put(node, new LinkedHashSet());
        }
        for (EdgeDefinition edge : definition.edges()) {
            successors.computeIfAbsent(edge.fromNodeId(), ignored -> new LinkedHashSet()).add(edge.toNodeId());
            predecessors.computeIfAbsent(edge.toNodeId(), ignored -> new LinkedHashSet()).add(edge.fromNodeId());
        }
        return new DefaultWorkflowGraph(nodes, successors, predecessors);
    }
}

