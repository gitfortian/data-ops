/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.state;

import io.yak.framework.workflow.engine.state.NodeExecutionStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class NodeStateMachine {
    private static final Map<NodeExecutionStatus, Set<NodeExecutionStatus>> TRANSITIONS = new EnumMap<NodeExecutionStatus, Set<NodeExecutionStatus>>(NodeExecutionStatus.class);

    private NodeStateMachine() {
    }

    public static void requireTransition(NodeExecutionStatus current, NodeExecutionStatus target) {
        if (current == target) {
            return;
        }
        if (!TRANSITIONS.getOrDefault((Object)current, Set.of()).contains((Object)target)) {
            throw new IllegalStateException("Invalid node state transition: " + String.valueOf((Object)current) + " -> " + String.valueOf((Object)target));
        }
    }

    private static void allow(NodeExecutionStatus source, NodeExecutionStatus ... targets) {
        TRANSITIONS.put(source, EnumSet.of(targets[0], targets));
    }

    static {
        NodeStateMachine.allow(NodeExecutionStatus.WAITING, NodeExecutionStatus.READY, NodeExecutionStatus.SUCCESS, NodeExecutionStatus.SKIPPED, NodeExecutionStatus.UPSTREAM_FAILED, NodeExecutionStatus.CANCELED);
        NodeStateMachine.allow(NodeExecutionStatus.READY, NodeExecutionStatus.SUBMITTED, NodeExecutionStatus.UPSTREAM_FAILED, NodeExecutionStatus.CANCELED);
        NodeStateMachine.allow(NodeExecutionStatus.SUBMITTED, NodeExecutionStatus.RUNNING, NodeExecutionStatus.PAUSING, NodeExecutionStatus.SUCCESS, NodeExecutionStatus.FAILED, NodeExecutionStatus.CANCELED);
        NodeStateMachine.allow(NodeExecutionStatus.RUNNING, NodeExecutionStatus.PAUSING, NodeExecutionStatus.SUCCESS, NodeExecutionStatus.FAILED, NodeExecutionStatus.CANCELED);
        NodeStateMachine.allow(NodeExecutionStatus.PAUSING, NodeExecutionStatus.PAUSED, NodeExecutionStatus.SUCCESS, NodeExecutionStatus.FAILED, NodeExecutionStatus.CANCELED);
        NodeStateMachine.allow(NodeExecutionStatus.PAUSED, NodeExecutionStatus.RESUMING, NodeExecutionStatus.CANCELED);
        NodeStateMachine.allow(NodeExecutionStatus.RESUMING, NodeExecutionStatus.SUBMITTED, NodeExecutionStatus.RUNNING, NodeExecutionStatus.SUCCESS, NodeExecutionStatus.FAILED, NodeExecutionStatus.CANCELED);
        NodeStateMachine.allow(NodeExecutionStatus.FAILED, NodeExecutionStatus.WAITING, NodeExecutionStatus.READY);
        NodeStateMachine.allow(NodeExecutionStatus.UPSTREAM_FAILED, NodeExecutionStatus.WAITING);
        NodeStateMachine.allow(NodeExecutionStatus.SKIPPED, NodeExecutionStatus.WAITING);
        NodeStateMachine.allow(NodeExecutionStatus.CANCELED, NodeExecutionStatus.WAITING);
    }
}

