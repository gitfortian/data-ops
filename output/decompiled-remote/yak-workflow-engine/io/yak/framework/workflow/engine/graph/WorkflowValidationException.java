/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.graph;

import java.util.List;

public final class WorkflowValidationException
extends IllegalArgumentException {
    private final List<String> errors;

    public WorkflowValidationException(List<String> errors) {
        super("Invalid workflow definition: " + String.join((CharSequence)"; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return this.errors;
    }
}

