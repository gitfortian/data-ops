/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.execution;

import io.yak.framework.workflow.engine.execution.NodeAttemptSnapshot;
import io.yak.framework.workflow.engine.state.NodeAttemptFailureReason;
import io.yak.framework.workflow.engine.state.NodeAttemptStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class NodeAttempt {
    private final String id;
    private final int attemptNumber;
    private final Instant availableAt;
    private NodeAttemptStatus status;
    private NodeAttemptStatus resumeTargetStatus;
    private Instant startedAt;
    private Instant pausedAt;
    private Duration pausedDuration = Duration.ZERO;
    private Instant endedAt;
    private String errorMessage;
    private NodeAttemptFailureReason failureReason;

    public NodeAttempt(String id, int attemptNumber, Instant availableAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.attemptNumber = attemptNumber;
        this.availableAt = Objects.requireNonNull(availableAt, "availableAt");
        this.status = NodeAttemptStatus.SUBMITTED;
    }

    private NodeAttempt(NodeAttempt source) {
        this.id = source.id;
        this.attemptNumber = source.attemptNumber;
        this.availableAt = source.availableAt;
        this.status = source.status;
        this.resumeTargetStatus = source.resumeTargetStatus;
        this.startedAt = source.startedAt;
        this.pausedAt = source.pausedAt;
        this.pausedDuration = source.pausedDuration;
        this.endedAt = source.endedAt;
        this.errorMessage = source.errorMessage;
        this.failureReason = source.failureReason;
    }

    public static NodeAttempt restore(NodeAttemptSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        NodeAttempt attempt = new NodeAttempt(snapshot.id(), snapshot.attemptNumber(), snapshot.availableAt());
        attempt.status = snapshot.status();
        attempt.resumeTargetStatus = snapshot.resumeTargetStatus();
        attempt.startedAt = snapshot.startedAt();
        attempt.pausedAt = snapshot.pausedAt();
        attempt.pausedDuration = snapshot.pausedDuration();
        attempt.endedAt = snapshot.endedAt();
        attempt.errorMessage = snapshot.errorMessage();
        attempt.failureReason = snapshot.failureReason();
        return attempt;
    }

    public String id() {
        return this.id;
    }

    public int attemptNumber() {
        return this.attemptNumber;
    }

    public Instant availableAt() {
        return this.availableAt;
    }

    public NodeAttemptStatus status() {
        return this.status;
    }

    public NodeAttemptStatus resumeTargetStatus() {
        return this.resumeTargetStatus;
    }

    public Instant startedAt() {
        return this.startedAt;
    }

    public Instant pausedAt() {
        return this.pausedAt;
    }

    public Duration pausedDuration() {
        return this.pausedDuration;
    }

    public Instant endedAt() {
        return this.endedAt;
    }

    public String errorMessage() {
        return this.errorMessage;
    }

    public NodeAttemptFailureReason failureReason() {
        return this.failureReason;
    }

    public void markRunning(Instant now) {
        if (this.status != NodeAttemptStatus.SUBMITTED) {
            throw new IllegalStateException("Only a submitted attempt can start");
        }
        this.status = NodeAttemptStatus.RUNNING;
        this.startedAt = now;
    }

    public void markPausing() {
        if (this.status != NodeAttemptStatus.SUBMITTED && this.status != NodeAttemptStatus.RUNNING) {
            throw new IllegalStateException("Only submitted or running attempts can pause");
        }
        this.resumeTargetStatus = this.status;
        this.status = NodeAttemptStatus.PAUSING;
    }

    public void markPaused(Instant now) {
        if (this.status != NodeAttemptStatus.PAUSING) {
            throw new IllegalStateException("Only a pausing attempt can acknowledge pause");
        }
        this.status = NodeAttemptStatus.PAUSED;
        this.pausedAt = now;
    }

    public void markResuming() {
        if (this.status != NodeAttemptStatus.PAUSED) {
            throw new IllegalStateException("Only a paused attempt can resume");
        }
        this.status = NodeAttemptStatus.RESUMING;
    }

    public NodeAttemptStatus markResumed(Instant now) {
        NodeAttemptStatus target;
        if (this.status != NodeAttemptStatus.RESUMING) {
            throw new IllegalStateException("Only a resuming attempt can acknowledge resume");
        }
        if (this.pausedAt != null) {
            this.pausedDuration = this.pausedDuration.plus(Duration.between(this.pausedAt, now));
            this.pausedAt = null;
        }
        this.status = target = Objects.requireNonNull(this.resumeTargetStatus, "resumeTargetStatus");
        this.resumeTargetStatus = null;
        return target;
    }

    public Instant dispatchDeadline(Duration dispatchTimeout) {
        if (dispatchTimeout == null || dispatchTimeout.isZero()) {
            return null;
        }
        return this.availableAt.plus(dispatchTimeout).plus(this.pausedDuration);
    }

    public Instant executionDeadline(Duration executionTimeout) {
        if (this.startedAt == null || executionTimeout == null || executionTimeout.isZero()) {
            return null;
        }
        return this.startedAt.plus(executionTimeout).plus(this.pausedDuration);
    }

    public void markSuccess(Instant now) {
        this.requireCallbackActive();
        this.status = NodeAttemptStatus.SUCCESS;
        this.endedAt = now;
    }

    public void markFailure(String errorMessage, Instant now) {
        this.markFailure(NodeAttemptFailureReason.EXECUTOR_FAILURE, errorMessage, now);
    }

    public void markFailure(NodeAttemptFailureReason failureReason, String errorMessage, Instant now) {
        this.requireCallbackActive();
        this.status = NodeAttemptStatus.FAILED;
        this.failureReason = Objects.requireNonNull(failureReason, "failureReason");
        this.errorMessage = errorMessage;
        this.endedAt = now;
    }

    public void markCanceled(Instant now) {
        this.requireNonTerminal();
        this.status = NodeAttemptStatus.CANCELED;
        this.endedAt = now;
    }

    public NodeAttemptSnapshot snapshot() {
        return new NodeAttemptSnapshot(this.id, this.attemptNumber, this.availableAt, this.status, this.resumeTargetStatus, this.startedAt, this.pausedAt, this.pausedDuration, this.endedAt, this.errorMessage, this.failureReason);
    }

    public NodeAttempt copy() {
        return new NodeAttempt(this);
    }

    private void requireCallbackActive() {
        if (this.status != NodeAttemptStatus.SUBMITTED && this.status != NodeAttemptStatus.RUNNING && this.status != NodeAttemptStatus.PAUSING && this.status != NodeAttemptStatus.RESUMING) {
            throw new IllegalStateException("Attempt is not callback-active: " + String.valueOf((Object)this.status));
        }
    }

    private void requireNonTerminal() {
        if (this.status == NodeAttemptStatus.SUCCESS || this.status == NodeAttemptStatus.FAILED || this.status == NodeAttemptStatus.CANCELED) {
            throw new IllegalStateException("Attempt is already terminal: " + String.valueOf((Object)this.status));
        }
    }
}

