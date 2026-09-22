/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.spi;

import io.yak.framework.workflow.engine.execution.NodeExecutionContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public record NodeDispatch(String workflowExecutionId, String nodeExecutionId, String nodeId, String attemptId, int attemptNumber, Instant availableAt, Map<String, Object> workflowInput, Map<String, Object> nodeConfiguration, Map<String, Map<String, Object>> predecessorOutputs, Map<String, Object> nodeInput, Instant dispatchDeadline, Duration executionTimeout) {
    public NodeDispatch {
        NodeExecutionContext context = new NodeExecutionContext(workflowExecutionId, nodeExecutionId, nodeId, attemptId, attemptNumber, availableAt, dispatchDeadline, executionTimeout, workflowInput, predecessorOutputs, nodeInput, nodeConfiguration);
        workflowInput = context.workflowInput();
        nodeConfiguration = context.nodeConfiguration();
        predecessorOutputs = context.predecessorOutputs();
        nodeInput = context.nodeInput();
        executionTimeout = context.executionTimeout();
    }

    public NodeDispatch(String workflowExecutionId, String nodeExecutionId, String nodeId, String attemptId, int attemptNumber, Instant availableAt, Map<String, Object> workflowInput, Map<String, Object> nodeConfiguration) {
        this(workflowExecutionId, nodeExecutionId, nodeId, attemptId, attemptNumber, availableAt, workflowInput, nodeConfiguration, Map.of(), Map.of(), null, Duration.ZERO);
    }

    public NodeExecutionContext context() {
        return new NodeExecutionContext(this.workflowExecutionId, this.nodeExecutionId, this.nodeId, this.attemptId, this.attemptNumber, this.availableAt, this.dispatchDeadline, this.executionTimeout, this.workflowInput, this.predecessorOutputs, this.nodeInput, this.nodeConfiguration);
    }
}

