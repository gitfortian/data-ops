/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.spi;

import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import java.util.Optional;

public interface WorkflowDefinitionRepository {
    public void save(WorkflowDefinition var1);

    public Optional<WorkflowDefinition> findById(String var1);
}

