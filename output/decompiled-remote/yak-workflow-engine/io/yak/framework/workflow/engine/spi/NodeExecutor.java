/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.spi;

import io.yak.framework.workflow.engine.spi.NodeCancellation;
import io.yak.framework.workflow.engine.spi.NodeControlResult;
import io.yak.framework.workflow.engine.spi.NodeDispatch;
import io.yak.framework.workflow.engine.spi.NodePauseRequest;
import io.yak.framework.workflow.engine.spi.NodeRecovery;
import io.yak.framework.workflow.engine.spi.NodeResumeRequest;
import io.yak.framework.workflow.engine.state.NodeAttemptStatus;

public interface NodeExecutor {
    public void submit(NodeDispatch var1);

    default public void recover(NodeRecovery recovery) {
        if (recovery.attemptStatus() == NodeAttemptStatus.SUBMITTED) {
            this.submit(recovery.dispatch());
        }
    }

    default public void cancel(NodeCancellation cancellation) {
    }

    default public NodeControlResult pause(NodePauseRequest request) {
        return NodeControlResult.UNSUPPORTED;
    }

    default public void resume(NodeResumeRequest request) {
    }
}

