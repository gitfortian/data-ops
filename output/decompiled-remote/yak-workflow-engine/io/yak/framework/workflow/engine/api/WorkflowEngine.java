/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.api;

import io.yak.framework.workflow.engine.command.WorkflowCommand;
import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import java.util.Map;
import java.util.Optional;

public interface WorkflowEngine {
    public void registerDefinition(WorkflowDefinition var1);

    public WorkflowExecution start(String var1, Map<String, Object> var2);

    default public WorkflowExecution submit(WorkflowCommand command) {
        throw new UnsupportedOperationException("This workflow engine does not expose command submission");
    }

    default public WorkflowExecution acknowledgeNodeStarted(String executionId, String nodeId, String attemptId) {
        return this.submit(new WorkflowCommand.NodeStarted(executionId, nodeId, attemptId));
    }

    default public WorkflowExecution pause(String executionId, String reason) {
        return this.submit(new WorkflowCommand.PauseWorkflow(executionId, reason));
    }

    default public WorkflowExecution acknowledgeNodePaused(String executionId, String nodeId, String attemptId) {
        return this.submit(new WorkflowCommand.NodePaused(executionId, nodeId, attemptId));
    }

    default public WorkflowExecution resume(String executionId) {
        return this.submit(new WorkflowCommand.ResumeWorkflow(executionId));
    }

    default public WorkflowExecution acknowledgeNodeResumed(String executionId, String nodeId, String attemptId) {
        return this.submit(new WorkflowCommand.NodeResumed(executionId, nodeId, attemptId));
    }

    default public WorkflowExecution completeNode(String executionId, String nodeId, String attemptId, Map<String, Object> output) {
        return this.submit(new WorkflowCommand.NodeSucceeded(executionId, nodeId, attemptId, output));
    }

    default public WorkflowExecution failNode(String executionId, String nodeId, String attemptId, String errorMessage) {
        return this.submit(new WorkflowCommand.NodeFailed(executionId, nodeId, attemptId, errorMessage));
    }

    default public WorkflowExecution checkTimeouts(String executionId) {
        return this.submit(new WorkflowCommand.CheckTimeouts(executionId));
    }

    default public WorkflowExecution continueAfterFailure(String executionId, String nodeId) {
        return this.submit(new WorkflowCommand.ContinueAfterFailure(executionId, nodeId));
    }

    default public WorkflowExecution retryFailedNode(String executionId, String nodeId) {
        return this.submit(new WorkflowCommand.RetryFailedNode(executionId, nodeId));
    }

    default public WorkflowExecution cancel(String executionId, String reason) {
        return this.submit(new WorkflowCommand.CancelWorkflow(executionId, reason));
    }

    default public WorkflowExecution retryFailedNodes(String executionId) {
        return this.submit(new WorkflowCommand.RetryFailedNodes(executionId));
    }

    default public WorkflowExecution restart(String sourceExecutionId) {
        return this.submit(new WorkflowCommand.RestartWorkflow(sourceExecutionId));
    }

    default public WorkflowExecution rerunFromNode(String sourceExecutionId, String nodeId) {
        return this.submit(new WorkflowCommand.RerunFromNode(sourceExecutionId, nodeId));
    }

    public Optional<WorkflowExecution> findExecution(String var1);
}

