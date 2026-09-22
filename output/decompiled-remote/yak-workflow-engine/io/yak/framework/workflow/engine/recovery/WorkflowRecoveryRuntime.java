/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.recovery;

import io.yak.framework.workflow.engine.definition.NodeDefinition;
import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.execution.NodeAttempt;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.execution.NodeInputResolver;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.graph.WorkflowDefinitionValidator;
import io.yak.framework.workflow.engine.graph.WorkflowGraph;
import io.yak.framework.workflow.engine.graph.WorkflowGraphBuilder;
import io.yak.framework.workflow.engine.policy.DefaultTriggerRuleEvaluator;
import io.yak.framework.workflow.engine.scheduler.DefaultReadyNodeResolver;
import io.yak.framework.workflow.engine.scheduler.DefaultWorkflowScheduler;
import io.yak.framework.workflow.engine.scheduler.WorkflowCompletionResolver;
import io.yak.framework.workflow.engine.scheduler.WorkflowScheduler;
import io.yak.framework.workflow.engine.spi.ExecutionLock;
import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.spi.IdGenerator;
import io.yak.framework.workflow.engine.spi.NodeDispatch;
import io.yak.framework.workflow.engine.spi.NodeExecutor;
import io.yak.framework.workflow.engine.spi.NodeRecovery;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.framework.workflow.engine.state.NodeExecutionStatus;
import io.yak.framework.workflow.engine.state.WorkflowExecutionStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WorkflowRecoveryRuntime {
    private final WorkflowDefinitionRepository definitionRepository;
    private final ExecutionRepository executionRepository;
    private final NodeExecutor nodeExecutor;
    private final ExecutionLock executionLock;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();
    private final WorkflowGraphBuilder graphBuilder = new WorkflowGraphBuilder();
    private final WorkflowScheduler scheduler = new DefaultWorkflowScheduler(new DefaultReadyNodeResolver(new DefaultTriggerRuleEvaluator()));
    private final WorkflowCompletionResolver completionResolver = new WorkflowCompletionResolver();
    private final NodeInputResolver nodeInputResolver = new NodeInputResolver();

    public WorkflowRecoveryRuntime(WorkflowDefinitionRepository definitionRepository, ExecutionRepository executionRepository, NodeExecutor nodeExecutor, ExecutionLock executionLock, IdGenerator idGenerator, Clock clock) {
        this.definitionRepository = Objects.requireNonNull(definitionRepository, "definitionRepository");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository");
        this.nodeExecutor = Objects.requireNonNull(nodeExecutor, "nodeExecutor");
        this.executionLock = Objects.requireNonNull(executionLock, "executionLock");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WorkflowExecution recover(String executionId) {
        Objects.requireNonNull(executionId, "executionId");
        return this.executionLock.execute(executionId, () -> this.recoverLocked(executionId));
    }

    private WorkflowExecution recoverLocked(String executionId) {
        WorkflowExecution execution = this.executionRepository.findById(executionId).orElseThrow(() -> new IllegalArgumentException("Unknown workflow execution: " + executionId));
        if (execution.status().isTerminal()) {
            return execution.copy();
        }
        WorkflowDefinition definition = this.definitionRepository.findById(execution.definitionId()).orElseThrow(() -> new IllegalArgumentException("Unknown workflow definition: " + execution.definitionId()));
        this.validator.validate(definition);
        WorkflowGraph graph = this.graphBuilder.build(definition);
        this.reconcilePersistedAttempts(definition, graph, execution);
        if (execution.status() == WorkflowExecutionStatus.RUNNING && !execution.schedulingStopped()) {
            ArrayList<NodeExecution> ready = new ArrayList<NodeExecution>();
            for (NodeExecution node : execution.nodes().values()) {
                if (node.status() != NodeExecutionStatus.READY) continue;
                ready.add(node);
            }
            ready.addAll(this.scheduler.advance(definition, graph, execution, this.waitingNodeIds(execution)));
            this.dispatchReady(definition, graph, execution, ready);
            this.finishIfPossible(execution);
            this.executionRepository.save(execution);
        }
        return execution.copy();
    }

    private void reconcilePersistedAttempts(WorkflowDefinition definition, WorkflowGraph graph, WorkflowExecution execution) {
        for (NodeExecution node : execution.nodes().values()) {
            if (!this.isRecoverableAttemptState(node.status()) || node.attempts().isEmpty()) continue;
            NodeAttempt attempt = node.attempts().get(node.attempts().size() - 1);
            this.nodeExecutor.recover(new NodeRecovery(this.toDispatch(definition, graph, execution, node, attempt), execution.status(), node.status(), attempt.status()));
        }
    }

    private boolean isRecoverableAttemptState(NodeExecutionStatus status) {
        return status == NodeExecutionStatus.SUBMITTED || status == NodeExecutionStatus.RUNNING || status == NodeExecutionStatus.PAUSING || status == NodeExecutionStatus.PAUSED || status == NodeExecutionStatus.RESUMING;
    }

    private Collection<String> waitingNodeIds(WorkflowExecution execution) {
        return execution.nodes().values().stream().filter(node -> node.status() == NodeExecutionStatus.WAITING).map(NodeExecution::nodeId).toList();
    }

    private void dispatchReady(WorkflowDefinition definition, WorkflowGraph graph, WorkflowExecution execution, Collection<NodeExecution> readyNodes) {
        if (execution.status() != WorkflowExecutionStatus.RUNNING || execution.schedulingStopped()) {
            return;
        }
        for (NodeExecution node : readyNodes) {
            NodeDefinition nodeDefinition = definition.node(node.nodeId());
            Instant availableAt = node.attempts().isEmpty() ? this.now() : this.now().plus(nodeDefinition.retryPolicy().delay());
            NodeAttempt attempt = node.beginAttempt(this.idGenerator.nextId(), availableAt);
            this.executionRepository.save(execution);
            this.nodeExecutor.submit(this.toDispatch(definition, graph, execution, node, attempt));
        }
    }

    private NodeDispatch toDispatch(WorkflowDefinition definition, WorkflowGraph graph, WorkflowExecution execution, NodeExecution node, NodeAttempt attempt) {
        NodeDefinition nodeDefinition = definition.node(node.nodeId());
        Map<String, Map<String, Object>> predecessorOutputs = this.collectPredecessorOutputs(execution, graph.predecessors(node.nodeId()));
        Map<String, Object> nodeInput = this.nodeInputResolver.resolve(nodeDefinition.inputMapping(), execution.input(), predecessorOutputs);
        Instant dispatchDeadline = attempt.dispatchDeadline(nodeDefinition.timeoutPolicy().dispatchTimeout());
        return new NodeDispatch(execution.id(), node.id(), node.nodeId(), attempt.id(), attempt.attemptNumber(), attempt.availableAt(), execution.input(), nodeDefinition.configuration(), predecessorOutputs, nodeInput, dispatchDeadline, nodeDefinition.timeoutPolicy().executionTimeout());
    }

    private Map<String, Map<String, Object>> collectPredecessorOutputs(WorkflowExecution execution, Set<String> predecessorIds) {
        if (predecessorIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, Map<String, Object>> outputs = new LinkedHashMap<String, Map<String, Object>>();
        for (String predecessorId : predecessorIds) {
            outputs.put(predecessorId, execution.node(predecessorId).output());
        }
        return outputs;
    }

    private void finishIfPossible(WorkflowExecution execution) {
        if (execution.status() != WorkflowExecutionStatus.RUNNING) {
            return;
        }
        this.completionResolver.resolve(execution).ifPresent(status -> execution.transitionTo((WorkflowExecutionStatus)((Object)status), this.now()));
    }

    private Instant now() {
        return this.clock.instant();
    }
}

