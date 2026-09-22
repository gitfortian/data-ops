/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.graph;

import java.util.List;
import java.util.Set;

public interface WorkflowGraph {
    public Set<String> nodes();

    public Set<String> predecessors(String var1);

    public Set<String> successors(String var1);

    public Set<String> startNodes();

    public Set<String> endNodes();

    public Set<String> ancestors(String var1);

    public Set<String> descendants(String var1);

    public List<String> topologicalSort();

    public boolean hasCycle();
}

