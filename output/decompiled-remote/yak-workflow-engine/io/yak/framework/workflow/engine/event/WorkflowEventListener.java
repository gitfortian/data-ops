/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.event;

import io.yak.framework.workflow.engine.event.WorkflowEvent;

@FunctionalInterface
public interface WorkflowEventListener {
    public void onEvent(WorkflowEvent var1);

    public static WorkflowEventListener noop() {
        return event -> {};
    }
}

