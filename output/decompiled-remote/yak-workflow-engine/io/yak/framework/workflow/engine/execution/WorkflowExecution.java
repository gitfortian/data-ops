/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.execution;

import io.yak.framework.workflow.engine.execution.ExecutionValueSnapshot;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.execution.NodeExecutionSnapshot;
import io.yak.framework.workflow.engine.execution.WorkflowExecutionSnapshot;
import io.yak.framework.workflow.engine.state.WorkflowExecutionStatus;
import io.yak.framework.workflow.engine.state.WorkflowStateMachine;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class WorkflowExecution {
    private final String id;
    private final String definitionId;
    private final String sourceExecutionId;
    private final Map<String, Object> input;
    private final Map<String, NodeExecution> nodes;
    private final Instant createdAt;
    private WorkflowExecutionStatus status;
    private boolean schedulingStopped;
    private Instant runStartedAt;
    private Instant pausedAt;
    private Duration pausedDuration = Duration.ZERO;
    private Instant updatedAt;
    private Instant endedAt;

    public WorkflowExecution(String id, String definitionId, String sourceExecutionId, Map<String, Object> input, Map<String, NodeExecution> nodes, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.definitionId = Objects.requireNonNull(definitionId, "definitionId");
        this.sourceExecutionId = sourceExecutionId;
        this.input = ExecutionValueSnapshot.immutableMap(input);
        this.nodes = new LinkedHashMap<String, NodeExecution>(Objects.requireNonNull(nodes, "nodes"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = createdAt;
        this.status = WorkflowExecutionStatus.CREATED;
    }

    private WorkflowExecution(WorkflowExecution source) {
        this.id = source.id;
        this.definitionId = source.definitionId;
        this.sourceExecutionId = source.sourceExecutionId;
        this.input = source.input;
        this.nodes = new LinkedHashMap<String, NodeExecution>();
        source.nodes.forEach((nodeId, execution) -> this.nodes.put((String)nodeId, execution.copy()));
        this.createdAt = source.createdAt;
        this.status = source.status;
        this.schedulingStopped = source.schedulingStopped;
        this.runStartedAt = source.runStartedAt;
        this.pausedAt = source.pausedAt;
        this.pausedDuration = source.pausedDuration;
        this.updatedAt = source.updatedAt;
        this.endedAt = source.endedAt;
    }

    public static WorkflowExecution restore(WorkflowExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        LinkedHashMap<String, NodeExecution> nodes = new LinkedHashMap<String, NodeExecution>();
        for (NodeExecutionSnapshot nodeSnapshot : snapshot.nodes()) {
            if (!snapshot.id().equals(nodeSnapshot.workflowExecutionId())) {
                throw new IllegalArgumentException("Node execution belongs to a different workflow execution: " + nodeSnapshot.nodeId());
            }
            NodeExecution node = NodeExecution.restore(nodeSnapshot);
            NodeExecution previous = nodes.putIfAbsent(node.nodeId(), node);
            if (previous == null) continue;
            throw new IllegalArgumentException("Duplicate node execution in snapshot: " + node.nodeId());
        }
        WorkflowExecution execution = new WorkflowExecution(snapshot.id(), snapshot.definitionId(), snapshot.sourceExecutionId(), snapshot.input(), nodes, snapshot.createdAt());
        execution.status = snapshot.status();
        execution.schedulingStopped = snapshot.schedulingStopped();
        execution.runStartedAt = snapshot.runStartedAt();
        execution.pausedAt = snapshot.pausedAt();
        execution.pausedDuration = snapshot.pausedDuration();
        execution.updatedAt = snapshot.updatedAt();
        execution.endedAt = snapshot.endedAt();
        return execution;
    }

    public String id() {
        return this.id;
    }

    public String definitionId() {
        return this.definitionId;
    }

    public String sourceExecutionId() {
        return this.sourceExecutionId;
    }

    public Map<String, Object> input() {
        return this.input;
    }

    public Map<String, NodeExecution> nodes() {
        return Collections.unmodifiableMap(this.nodes);
    }

    public NodeExecution node(String nodeId) {
        NodeExecution node = this.nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node execution: " + nodeId);
        }
        return node;
    }

    public WorkflowExecutionStatus status() {
        return this.status;
    }

    public boolean schedulingStopped() {
        return this.schedulingStopped;
    }

    public Instant createdAt() {
        return this.createdAt;
    }

    public Instant runStartedAt() {
        return this.runStartedAt;
    }

    public Instant pausedAt() {
        return this.pausedAt;
    }

    public Duration pausedDuration() {
        return this.pausedDuration;
    }

    public Instant workflowDeadline(Duration timeout) {
        if (this.runStartedAt == null || timeout == null || timeout.isZero()) {
            return null;
        }
        return this.runStartedAt.plus(timeout).plus(this.pausedDuration);
    }

    public Instant updatedAt() {
        return this.updatedAt;
    }

    public Instant endedAt() {
        return this.endedAt;
    }

    public void transitionTo(WorkflowExecutionStatus target, Instant now) {
        WorkflowExecutionStatus previous = this.status;
        WorkflowStateMachine.requireTransition(previous, target);
        this.status = target;
        this.updatedAt = now;
        if (target == WorkflowExecutionStatus.RUNNING && previous != WorkflowExecutionStatus.RUNNING) {
            if (previous == WorkflowExecutionStatus.RESUMING) {
                this.closePausedInterval(now);
            } else {
                this.runStartedAt = now;
                this.pausedAt = null;
                this.pausedDuration = Duration.ZERO;
            }
        }
        if (target == WorkflowExecutionStatus.PAUSED && this.pausedAt == null) {
            this.pausedAt = now;
        }
        this.endedAt = target.isTerminal() ? now : null;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public void stopScheduling() {
        this.schedulingStopped = true;
    }

    public void resumeScheduling() {
        this.schedulingStopped = false;
    }

    public WorkflowExecutionSnapshot snapshot() {
        return new WorkflowExecutionSnapshot(this.id, this.definitionId, this.sourceExecutionId, this.input, this.nodes.values().stream().map(NodeExecution::snapshot).toList(), this.createdAt, this.status, this.schedulingStopped, this.runStartedAt, this.pausedAt, this.pausedDuration, this.updatedAt, this.endedAt);
    }

    public WorkflowExecution copy() {
        return new WorkflowExecution(this);
    }

    private void closePausedInterval(Instant now) {
        if (this.pausedAt != null) {
            this.pausedDuration = this.pausedDuration.plus(Duration.between(this.pausedAt, now));
            this.pausedAt = null;
        }
    }
}

