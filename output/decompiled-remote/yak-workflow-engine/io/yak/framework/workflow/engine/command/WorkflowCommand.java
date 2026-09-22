/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.command;

import io.yak.framework.workflow.engine.execution.ExecutionValueSnapshot;
import java.util.Map;
import java.util.Objects;

public sealed interface WorkflowCommand {
    public String executionId();

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public record RerunFromNode(String executionId, String nodeId) implements WorkflowCommand
    {
        public RerunFromNode {
            executionId = WorkflowCommand.requireText(executionId, "executionId");
            nodeId = WorkflowCommand.requireText(nodeId, "nodeId");
        }
    }

    public record RestartWorkflow(String executionId) implements WorkflowCommand
    {
        public RestartWorkflow(String executionId) {
            this.executionId = executionId = WorkflowCommand.requireText(executionId, "executionId");
        }
    }

    public record RetryFailedNodes(String executionId) implements WorkflowCommand
    {
        public RetryFailedNodes(String executionId) {
            this.executionId = executionId = WorkflowCommand.requireText(executionId, "executionId");
        }
    }

    public record RetryFailedNode(String executionId, String nodeId) implements WorkflowCommand
    {
        public RetryFailedNode {
            executionId = WorkflowCommand.requireText(executionId, "executionId");
            nodeId = WorkflowCommand.requireText(nodeId, "nodeId");
        }
    }

    public record ContinueAfterFailure(String executionId, String nodeId) implements WorkflowCommand
    {
        public ContinueAfterFailure {
            executionId = WorkflowCommand.requireText(executionId, "executionId");
            nodeId = WorkflowCommand.requireText(nodeId, "nodeId");
        }
    }

    public record CancelWorkflow(String executionId, String reason) implements WorkflowCommand
    {
        public CancelWorkflow(String executionId, String reason) {
            this.executionId = executionId = WorkflowCommand.requireText(executionId, "executionId");
            this.reason = reason;
        }
    }

    public record ResumeWorkflow(String executionId) implements WorkflowCommand
    {
        public ResumeWorkflow(String executionId) {
            this.executionId = executionId = WorkflowCommand.requireText(executionId, "executionId");
        }
    }

    public record PauseWorkflow(String executionId, String reason) implements WorkflowCommand
    {
        public PauseWorkflow(String executionId, String reason) {
            this.executionId = executionId = WorkflowCommand.requireText(executionId, "executionId");
            this.reason = reason;
        }
    }

    public record CheckTimeouts(String executionId) implements WorkflowCommand
    {
        public CheckTimeouts(String executionId) {
            this.executionId = executionId = WorkflowCommand.requireText(executionId, "executionId");
        }
    }

    public record NodeResumed(String executionId, String nodeId, String attemptId) implements WorkflowCommand
    {
        public NodeResumed {
            executionId = WorkflowCommand.requireText(executionId, "executionId");
            nodeId = WorkflowCommand.requireText(nodeId, "nodeId");
            attemptId = WorkflowCommand.requireText(attemptId, "attemptId");
        }
    }

    public record NodePaused(String executionId, String nodeId, String attemptId) implements WorkflowCommand
    {
        public NodePaused {
            executionId = WorkflowCommand.requireText(executionId, "executionId");
            nodeId = WorkflowCommand.requireText(nodeId, "nodeId");
            attemptId = WorkflowCommand.requireText(attemptId, "attemptId");
        }
    }

    public record NodeFailed(String executionId, String nodeId, String attemptId, String errorMessage) implements WorkflowCommand
    {
        public NodeFailed {
            executionId = WorkflowCommand.requireText(executionId, "executionId");
            nodeId = WorkflowCommand.requireText(nodeId, "nodeId");
            attemptId = WorkflowCommand.requireText(attemptId, "attemptId");
        }
    }

    public record NodeSucceeded(String executionId, String nodeId, String attemptId, Map<String, Object> output) implements WorkflowCommand
    {
        public NodeSucceeded {
            executionId = WorkflowCommand.requireText(executionId, "executionId");
            nodeId = WorkflowCommand.requireText(nodeId, "nodeId");
            attemptId = WorkflowCommand.requireText(attemptId, "attemptId");
            output = ExecutionValueSnapshot.immutableMap(output);
        }
    }

    public record NodeStarted(String executionId, String nodeId, String attemptId) implements WorkflowCommand
    {
        public NodeStarted {
            executionId = WorkflowCommand.requireText(executionId, "executionId");
            nodeId = WorkflowCommand.requireText(nodeId, "nodeId");
            attemptId = WorkflowCommand.requireText(attemptId, "attemptId");
        }
    }
}

