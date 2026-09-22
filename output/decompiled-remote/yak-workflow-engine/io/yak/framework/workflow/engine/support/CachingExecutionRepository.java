/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.support;

import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecutionSnapshot;
import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CachingExecutionRepository
implements ExecutionRepository {
    private final ExecutionRepository delegate;
    private final ConcurrentMap<String, WorkflowExecutionSnapshot> activeSnapshots = new ConcurrentHashMap<String, WorkflowExecutionSnapshot>();

    public CachingExecutionRepository(ExecutionRepository delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void save(WorkflowExecution execution) {
        Objects.requireNonNull(execution, "execution");
        WorkflowExecutionSnapshot snapshot = execution.snapshot();
        WorkflowExecutionSnapshot cached = (WorkflowExecutionSnapshot)this.activeSnapshots.get(snapshot.id());
        if (snapshot.equals(cached)) {
            return;
        }
        this.delegate.save(execution);
        if (snapshot.status().isTerminal()) {
            this.activeSnapshots.remove(snapshot.id());
        } else {
            this.activeSnapshots.put(snapshot.id(), snapshot);
        }
    }

    @Override
    public Optional<WorkflowExecution> findById(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        WorkflowExecutionSnapshot cached = (WorkflowExecutionSnapshot)this.activeSnapshots.get(executionId);
        if (cached != null) {
            return Optional.of(WorkflowExecution.restore(cached));
        }
        Optional<WorkflowExecution> loaded = this.delegate.findById(executionId);
        loaded.ifPresent(execution -> {
            WorkflowExecutionSnapshot snapshot = execution.snapshot();
            if (!snapshot.status().isTerminal()) {
                this.activeSnapshots.put(executionId, snapshot);
            }
        });
        return loaded.map(WorkflowExecution::copy);
    }

    public void evict(String executionId) {
        if (executionId != null) {
            this.activeSnapshots.remove(executionId);
        }
    }

    public void clear() {
        this.activeSnapshots.clear();
    }

    int activeSize() {
        return this.activeSnapshots.size();
    }
}

