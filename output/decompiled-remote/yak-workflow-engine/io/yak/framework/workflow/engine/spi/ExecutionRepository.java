/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.spi;

import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import java.util.Optional;

public interface ExecutionRepository {
    public void save(WorkflowExecution var1);

    public Optional<WorkflowExecution> findById(String var1);
}

