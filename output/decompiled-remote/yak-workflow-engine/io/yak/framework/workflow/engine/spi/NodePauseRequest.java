/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.spi;

public record NodePauseRequest(String workflowExecutionId, String nodeExecutionId, String nodeId, String attemptId, String reason) {
}

