/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.support;

import io.yak.framework.workflow.engine.command.WorkflowCommand;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.spi.ExecutionLock;
import io.yak.framework.workflow.engine.spi.ExecutionMailbox;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LocalExecutionMailbox
implements ExecutionMailbox {
    private final ExecutionLock executionLock;
    private final ConcurrentMap<String, MailboxState> mailboxes = new ConcurrentHashMap<String, MailboxState>();

    public LocalExecutionMailbox(ExecutionLock executionLock) {
        this.executionLock = Objects.requireNonNull(executionLock, "executionLock");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public WorkflowExecution submit(WorkflowCommand command, ExecutionMailbox.WorkflowCommandHandler handler) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(handler, "handler");
        String executionId = command.executionId();
        MailboxState state = this.mailboxes.computeIfAbsent(executionId, ignored -> new MailboxState());
        Envelope envelope = new Envelope(command, handler);
        boolean shouldDrain = false;
        MailboxState mailboxState = state;
        synchronized (mailboxState) {
            if (state.draining && state.owner == Thread.currentThread()) {
                throw new IllegalStateException("Reentrant command submission to the same execution mailbox is not supported: " + executionId);
            }
            state.queue.addLast(envelope);
            if (!state.draining) {
                state.draining = true;
                state.owner = Thread.currentThread();
                shouldDrain = true;
            }
        }
        if (shouldDrain) {
            this.drain(executionId, state);
        }
        return this.await(envelope.result);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void drain(String executionId, MailboxState state) {
        while (true) {
            Envelope envelope;
            MailboxState mailboxState = state;
            synchronized (mailboxState) {
                envelope = state.queue.pollFirst();
                if (envelope == null) {
                    state.draining = false;
                    state.owner = null;
                    this.mailboxes.remove(executionId, state);
                    return;
                }
            }
            try {
                WorkflowExecution result = this.executionLock.execute(executionId, () -> envelope.handler.handle(envelope.command));
                envelope.result.complete(result);
                continue;
            }
            catch (Throwable throwable) {
                envelope.result.completeExceptionally(throwable);
                continue;
            }
            break;
        }
    }

    private WorkflowExecution await(CompletableFuture<WorkflowExecution> future) {
        try {
            return future.join();
        }
        catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                RuntimeException runtimeException = (RuntimeException)cause;
                throw runtimeException;
            }
            if (cause instanceof Error) {
                Error error = (Error)cause;
                throw error;
            }
            throw exception;
        }
    }

    private static final class MailboxState {
        private final Deque<Envelope> queue = new ArrayDeque<Envelope>();
        private boolean draining;
        private Thread owner;

        private MailboxState() {
        }
    }

    private static final class Envelope {
        private final WorkflowCommand command;
        private final ExecutionMailbox.WorkflowCommandHandler handler;
        private final CompletableFuture<WorkflowExecution> result = new CompletableFuture();

        private Envelope(WorkflowCommand command, ExecutionMailbox.WorkflowCommandHandler handler) {
            this.command = command;
            this.handler = handler;
        }
    }
}

