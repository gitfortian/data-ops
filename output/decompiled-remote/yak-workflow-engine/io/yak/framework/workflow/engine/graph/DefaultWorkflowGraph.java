/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.graph;

import io.yak.framework.workflow.engine.graph.WorkflowGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DefaultWorkflowGraph
implements WorkflowGraph {
    private final Set<String> nodes;
    private final Map<String, Set<String>> successors;
    private final Map<String, Set<String>> predecessors;

    public DefaultWorkflowGraph(Set<String> nodes, Map<String, Set<String>> successors, Map<String, Set<String>> predecessors) {
        this.nodes = Collections.unmodifiableSet(new LinkedHashSet<String>(nodes));
        this.successors = DefaultWorkflowGraph.immutableAdjacency(nodes, successors);
        this.predecessors = DefaultWorkflowGraph.immutableAdjacency(nodes, predecessors);
    }

    @Override
    public Set<String> nodes() {
        return this.nodes;
    }

    @Override
    public Set<String> predecessors(String nodeId) {
        this.requireNode(nodeId);
        return this.predecessors.get(nodeId);
    }

    @Override
    public Set<String> successors(String nodeId) {
        this.requireNode(nodeId);
        return this.successors.get(nodeId);
    }

    @Override
    public Set<String> startNodes() {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        for (String node : this.nodes) {
            if (!this.predecessors.get(node).isEmpty()) continue;
            result.add(node);
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<String> endNodes() {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        for (String node : this.nodes) {
            if (!this.successors.get(node).isEmpty()) continue;
            result.add(node);
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public Set<String> ancestors(String nodeId) {
        return this.traverse(nodeId, this.predecessors);
    }

    @Override
    public Set<String> descendants(String nodeId) {
        return this.traverse(nodeId, this.successors);
    }

    @Override
    public List<String> topologicalSort() {
        LinkedHashMap<String, Integer> indegree = new LinkedHashMap<String, Integer>();
        ArrayDeque<String> ready = new ArrayDeque<String>();
        for (String node : this.nodes) {
            int degree = this.predecessors.get(node).size();
            indegree.put(node, degree);
            if (degree != 0) continue;
            ready.offer(node);
        }
        ArrayList<String> result = new ArrayList<String>(this.nodes.size());
        while (!ready.isEmpty()) {
            String current = (String)ready.poll();
            result.add(current);
            for (String successor : this.successors.get(current)) {
                int degree = indegree.computeIfPresent(successor, (key, value) -> value - 1);
                if (degree != 0) continue;
                ready.offer(successor);
            }
        }
        if (result.size() != this.nodes.size()) {
            throw new IllegalStateException("Workflow graph contains a cycle");
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public boolean hasCycle() {
        try {
            this.topologicalSort();
            return false;
        }
        catch (IllegalStateException exception) {
            return true;
        }
    }

    private Set<String> traverse(String nodeId, Map<String, Set<String>> adjacency) {
        this.requireNode(nodeId);
        LinkedHashSet<String> visited = new LinkedHashSet<String>();
        ArrayDeque stack = new ArrayDeque(adjacency.get(nodeId));
        while (!stack.isEmpty()) {
            String current = (String)stack.pop();
            if (!visited.add(current)) continue;
            stack.addAll(adjacency.get(current));
        }
        return Collections.unmodifiableSet(visited);
    }

    private void requireNode(String nodeId) {
        if (!this.nodes.contains(nodeId)) {
            throw new IllegalArgumentException("Unknown node: " + nodeId);
        }
    }

    private static Map<String, Set<String>> immutableAdjacency(Set<String> nodes, Map<String, Set<String>> source) {
        LinkedHashMap result = new LinkedHashMap();
        for (String node : nodes) {
            result.put(node, Collections.unmodifiableSet(new LinkedHashSet(source.getOrDefault(node, Set.of()))));
        }
        return Collections.unmodifiableMap(result);
    }
}

