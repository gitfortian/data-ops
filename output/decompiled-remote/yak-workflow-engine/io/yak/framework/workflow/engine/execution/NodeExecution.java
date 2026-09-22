/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.execution;

import io.yak.framework.workflow.engine.definition.NodeFailurePolicy;
import io.yak.framework.workflow.engine.execution.ExecutionValueSnapshot;
import io.yak.framework.workflow.engine.execution.NodeAttempt;
import io.yak.framework.workflow.engine.execution.NodeExecutionSnapshot;
import io.yak.framework.workflow.engine.state.NodeAttemptFailureReason;
import io.yak.framework.workflow.engine.state.NodeAttemptStatus;
import io.yak.framework.workflow.engine.state.NodeExecutionStatus;
import io.yak.framework.workflow.engine.state.NodeStateMachine;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NodeExecution {
    private final String id;
    private final String workflowExecutionId;
    private final String nodeId;
    private final NodeFailurePolicy failurePolicy;
    private final List<NodeAttempt> attempts;
    private NodeExecutionStatus status;
    private Map<String, Object> output;
    private String errorMessage;
    private boolean failureHandled;
    private boolean downstreamContinuationAllowed;

    public NodeExecution(String id, String workflowExecutionId, String nodeId, NodeFailurePolicy failurePolicy) {
        this.id = Objects.requireNonNull(id, "id");
        this.workflowExecutionId = Objects.requireNonNull(workflowExecutionId, "workflowExecutionId");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.failurePolicy = Objects.requireNonNull(failurePolicy, "failurePolicy");
        this.status = NodeExecutionStatus.WAITING;
        this.attempts = new ArrayList<NodeAttempt>();
        this.output = Map.of();
    }

    private NodeExecution(NodeExecution source) {
        this.id = source.id;
        this.workflowExecutionId = source.workflowExecutionId;
        this.nodeId = source.nodeId;
        this.failurePolicy = source.failurePolicy;
        this.status = source.status;
        this.attempts = new ArrayList<NodeAttempt>(source.attempts.stream().map(NodeAttempt::copy).toList());
        this.output = source.output;
        this.errorMessage = source.errorMessage;
        this.failureHandled = source.failureHandled;
        this.downstreamContinuationAllowed = source.downstreamContinuationAllowed;
    }

    public static NodeExecution restore(NodeExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        NodeExecution execution = new NodeExecution(snapshot.id(), snapshot.workflowExecutionId(), snapshot.nodeId(), snapshot.failurePolicy());
        execution.status = snapshot.status();
        execution.attempts.clear();
        snapshot.attempts().stream().map(NodeAttempt::restore).forEach(execution.attempts::add);
        execution.output = ExecutionValueSnapshot.immutableMap(snapshot.output());
        execution.errorMessage = snapshot.errorMessage();
        execution.failureHandled = snapshot.failureHandled();
        execution.downstreamContinuationAllowed = snapshot.downstreamContinuationAllowed();
        return execution;
    }

    public String id() {
        return this.id;
    }

    public String workflowExecutionId() {
        return this.workflowExecutionId;
    }

    public String nodeId() {
        return this.nodeId;
    }

    public NodeFailurePolicy failurePolicy() {
        return this.failurePolicy;
    }

    public NodeExecutionStatus status() {
        return this.status;
    }

    public List<NodeAttempt> attempts() {
        return Collections.unmodifiableList(this.attempts);
    }

    public Map<String, Object> output() {
        return this.output;
    }

    public String errorMessage() {
        return this.errorMessage;
    }

    public boolean failureHandled() {
        return this.failureHandled;
    }

    public boolean downstreamContinuationAllowed() {
        return this.downstreamContinuationAllowed;
    }

    public String currentAttemptId() {
        return this.currentAttempt().id();
    }

    public NodeAttemptStatus currentAttemptStatus() {
        return this.currentAttempt().status();
    }

    public Instant currentAttemptAvailableAt() {
        return this.currentAttempt().availableAt();
    }

    public Instant currentAttemptStartedAt() {
        return this.currentAttempt().startedAt();
    }

    public Instant currentAttemptDispatchDeadline(Duration dispatchTimeout) {
        return this.attempts.isEmpty() ? null : this.currentAttempt().dispatchDeadline(dispatchTimeout);
    }

    public Instant currentAttemptExecutionDeadline(Duration executionTimeout) {
        return this.attempts.isEmpty() ? null : this.currentAttempt().executionDeadline(executionTimeout);
    }

    public boolean isCurrentAttempt(String attemptId) {
        return !this.attempts.isEmpty() && Objects.equals(this.currentAttempt().id(), attemptId);
    }

    public boolean isEffectiveSuccess() {
        return this.status == NodeExecutionStatus.SUCCESS || this.status == NodeExecutionStatus.FAILED && (this.failurePolicy == NodeFailurePolicy.IGNORE_FAILURE || this.downstreamContinuationAllowed);
    }

    public boolean isFailureLike() {
        return this.status == NodeExecutionStatus.UPSTREAM_FAILED || this.status == NodeExecutionStatus.CANCELED || this.status == NodeExecutionStatus.FAILED && this.failurePolicy != NodeFailurePolicy.IGNORE_FAILURE && !this.downstreamContinuationAllowed;
    }

    public void transitionTo(NodeExecutionStatus target) {
        NodeStateMachine.requireTransition(this.status, target);
        this.status = target;
    }

    public NodeAttempt beginAttempt(String attemptId, Instant availableAt) {
        if (this.status != NodeExecutionStatus.READY) {
            throw new IllegalStateException("Node must be READY before submission");
        }
        NodeAttempt attempt = new NodeAttempt(attemptId, this.attempts.size() + 1, availableAt);
        this.attempts.add(attempt);
        this.transitionTo(NodeExecutionStatus.SUBMITTED);
        this.errorMessage = null;
        this.failureHandled = false;
        this.downstreamContinuationAllowed = false;
        return attempt;
    }

    public void markRunning(Instant now) {
        this.currentAttempt().markRunning(now);
        this.transitionTo(NodeExecutionStatus.RUNNING);
    }

    public void markPausing() {
        this.currentAttempt().markPausing();
        this.transitionTo(NodeExecutionStatus.PAUSING);
    }

    public void markPaused(Instant now) {
        this.currentAttempt().markPaused(now);
        this.transitionTo(NodeExecutionStatus.PAUSED);
    }

    public void markResuming() {
        this.currentAttempt().markResuming();
        this.transitionTo(NodeExecutionStatus.RESUMING);
    }

    public void markResumed(Instant now) {
        NodeAttemptStatus resumedStatus = this.currentAttempt().markResumed(now);
        this.transitionTo(resumedStatus == NodeAttemptStatus.SUBMITTED ? NodeExecutionStatus.SUBMITTED : NodeExecutionStatus.RUNNING);
    }

    public void markSuccess(Map<String, Object> output, Instant now) {
        this.currentAttempt().markSuccess(now);
        this.output = ExecutionValueSnapshot.immutableMap(output);
        this.transitionTo(NodeExecutionStatus.SUCCESS);
    }

    public void markFailure(String errorMessage, Instant now) {
        this.markFailure(NodeAttemptFailureReason.EXECUTOR_FAILURE, errorMessage, now);
    }

    public void markFailure(NodeAttemptFailureReason failureReason, String errorMessage, Instant now) {
        this.currentAttempt().markFailure(failureReason, errorMessage, now);
        this.errorMessage = errorMessage;
        this.downstreamContinuationAllowed = false;
        this.transitionTo(NodeExecutionStatus.FAILED);
    }

    public void markCanceled(Instant now) {
        if (this.status == NodeExecutionStatus.SUBMITTED || this.status == NodeExecutionStatus.RUNNING || this.status == NodeExecutionStatus.PAUSING || this.status == NodeExecutionStatus.PAUSED || this.status == NodeExecutionStatus.RESUMING) {
            this.currentAttempt().markCanceled(now);
        }
        this.transitionTo(NodeExecutionStatus.CANCELED);
    }

    public void markFailureHandled() {
        this.failureHandled = true;
    }

    public void allowDownstreamContinuation() {
        if (this.status != NodeExecutionStatus.FAILED) {
            throw new IllegalStateException("Only a failed node can continue downstream");
        }
        this.failureHandled = true;
        this.downstreamContinuationAllowed = true;
    }

    public void resetForManualRetry() {
        this.transitionTo(NodeExecutionStatus.WAITING);
        this.errorMessage = null;
        this.failureHandled = false;
        this.downstreamContinuationAllowed = false;
    }

    public void resetSyntheticState() {
        if (this.status != NodeExecutionStatus.UPSTREAM_FAILED && this.status != NodeExecutionStatus.SKIPPED && this.status != NodeExecutionStatus.CANCELED) {
            throw new IllegalStateException("Node is not in a resettable synthetic state: " + String.valueOf((Object)this.status));
        }
        this.transitionTo(NodeExecutionStatus.WAITING);
        this.errorMessage = null;
        this.failureHandled = false;
        this.downstreamContinuationAllowed = false;
    }

    public void markCopiedSuccess(Map<String, Object> copiedOutput) {
        this.output = ExecutionValueSnapshot.immutableMap(copiedOutput);
        this.transitionTo(NodeExecutionStatus.SUCCESS);
    }

    public NodeExecutionSnapshot snapshot() {
        return new NodeExecutionSnapshot(this.id, this.workflowExecutionId, this.nodeId, this.failurePolicy, this.status, this.attempts.stream().map(NodeAttempt::snapshot).toList(), this.output, this.errorMessage, this.failureHandled, this.downstreamContinuationAllowed);
    }

    public NodeExecution copy() {
        return new NodeExecution(this);
    }

    private NodeAttempt currentAttempt() {
        if (this.attempts.isEmpty()) {
            throw new IllegalStateException("Node has no execution attempt");
        }
        return this.attempts.get(this.attempts.size() - 1);
    }
}

