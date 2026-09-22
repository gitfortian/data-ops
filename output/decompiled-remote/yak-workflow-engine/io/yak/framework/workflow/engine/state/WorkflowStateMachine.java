/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.state;

import io.yak.framework.workflow.engine.state.WorkflowExecutionStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class WorkflowStateMachine {
    private static final Map<WorkflowExecutionStatus, Set<WorkflowExecutionStatus>> TRANSITIONS = new EnumMap<WorkflowExecutionStatus, Set<WorkflowExecutionStatus>>(WorkflowExecutionStatus.class);

    private WorkflowStateMachine() {
    }

    public static void requireTransition(WorkflowExecutionStatus current, WorkflowExecutionStatus target) {
        if (current == target) {
            return;
        }
        if (!TRANSITIONS.getOrDefault((Object)current, Set.of()).contains((Object)target)) {
            throw new IllegalStateException("Invalid workflow state transition: " + String.valueOf((Object)current) + " -> " + String.valueOf((Object)target));
        }
    }

    private static void allow(WorkflowExecutionStatus source, WorkflowExecutionStatus ... targets) {
        TRANSITIONS.put(source, EnumSet.of(targets[0], targets));
    }

    static {
        WorkflowStateMachine.allow(WorkflowExecutionStatus.CREATED, WorkflowExecutionStatus.RUNNING, WorkflowExecutionStatus.CANCELED, WorkflowExecutionStatus.TIMED_OUT);
        WorkflowStateMachine.allow(WorkflowExecutionStatus.RUNNING, WorkflowExecutionStatus.PAUSING, WorkflowExecutionStatus.SUCCESS, WorkflowExecutionStatus.SUCCESS_WITH_WARNINGS, WorkflowExecutionStatus.FAILED, WorkflowExecutionStatus.CANCELED, WorkflowExecutionStatus.TIMED_OUT);
        WorkflowStateMachine.allow(WorkflowExecutionStatus.PAUSING, WorkflowExecutionStatus.PAUSED, WorkflowExecutionStatus.SUCCESS, WorkflowExecutionStatus.SUCCESS_WITH_WARNINGS, WorkflowExecutionStatus.FAILED, WorkflowExecutionStatus.CANCELED, WorkflowExecutionStatus.TIMED_OUT);
        WorkflowStateMachine.allow(WorkflowExecutionStatus.PAUSED, WorkflowExecutionStatus.RESUMING, WorkflowExecutionStatus.CANCELED, WorkflowExecutionStatus.TIMED_OUT);
        WorkflowStateMachine.allow(WorkflowExecutionStatus.RESUMING, WorkflowExecutionStatus.RUNNING, WorkflowExecutionStatus.SUCCESS, WorkflowExecutionStatus.SUCCESS_WITH_WARNINGS, WorkflowExecutionStatus.FAILED, WorkflowExecutionStatus.CANCELED, WorkflowExecutionStatus.TIMED_OUT);
        WorkflowStateMachine.allow(WorkflowExecutionStatus.FAILED, WorkflowExecutionStatus.RUNNING);
        WorkflowStateMachine.allow(WorkflowExecutionStatus.SUCCESS_WITH_WARNINGS, WorkflowExecutionStatus.RUNNING);
        WorkflowStateMachine.allow(WorkflowExecutionStatus.CANCELED, WorkflowExecutionStatus.RUNNING);
        WorkflowStateMachine.allow(WorkflowExecutionStatus.TIMED_OUT, WorkflowExecutionStatus.RUNNING);
    }
}

