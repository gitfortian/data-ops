/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.spi;

import io.yak.framework.workflow.engine.spi.NodeDispatch;
import io.yak.framework.workflow.engine.state.NodeAttemptStatus;
import io.yak.framework.workflow.engine.state.NodeExecutionStatus;
import io.yak.framework.workflow.engine.state.WorkflowExecutionStatus;
import java.util.Objects;

public record NodeRecovery(NodeDispatch dispatch, WorkflowExecutionStatus workflowStatus, NodeExecutionStatus nodeStatus, NodeAttemptStatus attemptStatus) {
    public NodeRecovery {
        dispatch = Objects.requireNonNull(dispatch, "dispatch");
        workflowStatus = Objects.requireNonNull(workflowStatus, "workflowStatus");
        nodeStatus = Objects.requireNonNull(nodeStatus, "nodeStatus");
        attemptStatus = Objects.requireNonNull(attemptStatus, "attemptStatus");
    }
}

