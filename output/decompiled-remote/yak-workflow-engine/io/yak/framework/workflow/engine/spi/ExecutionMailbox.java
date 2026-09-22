/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.spi;

import io.yak.framework.workflow.engine.command.WorkflowCommand;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;

public interface ExecutionMailbox {
    public WorkflowExecution submit(WorkflowCommand var1, WorkflowCommandHandler var2);

    @FunctionalInterface
    public static interface WorkflowCommandHandler {
        public WorkflowExecution handle(WorkflowCommand var1);
    }
}

