/*
 * Decompiled with CFR 0.152.
 */
package io.yak.framework.workflow.engine.runtime;

import io.yak.framework.workflow.engine.command.WorkflowCommand;
import io.yak.framework.workflow.engine.definition.NodeDefinition;
import io.yak.framework.workflow.engine.definition.NodeTimeoutPolicy;
import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.event.WorkflowEvent;
import io.yak.framework.workflow.engine.event.WorkflowEventListener;
import io.yak.framework.workflow.engine.execution.NodeAttempt;
import io.yak.framework.workflow.engine.execution.NodeExecution;
import io.yak.framework.workflow.engine.execution.NodeInputResolver;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.graph.WorkflowDefinitionValidator;
import io.yak.framework.workflow.engine.graph.WorkflowGraph;
import io.yak.framework.workflow.engine.graph.WorkflowGraphBuilder;
import io.yak.framework.workflow.engine.policy.DefaultFailurePropagationPolicy;
import io.yak.framework.workflow.engine.policy.DefaultRetryDecider;
import io.yak.framework.workflow.engine.policy.DefaultTriggerRuleEvaluator;
import io.yak.framework.workflow.engine.policy.FailureHandlingResult;
import io.yak.framework.workflow.engine.policy.FailurePropagationPolicy;
import io.yak.framework.workflow.engine.policy.RetryDecider;
import io.yak.framework.workflow.engine.scheduler.DefaultReadyNodeResolver;
import io.yak.framework.workflow.engine.scheduler.DefaultWorkflowScheduler;
import io.yak.framework.workflow.engine.scheduler.WorkflowCompletionResolver;
import io.yak.framework.workflow.engine.scheduler.WorkflowScheduler;
import io.yak.framework.workflow.engine.spi.ExecutionLock;
import io.yak.framework.workflow.engine.spi.ExecutionMailbox;
import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.spi.IdGenerator;
import io.yak.framework.workflow.engine.spi.NodeCancellation;
import io.yak.framework.workflow.engine.spi.NodeControlResult;
import io.yak.framework.workflow.engine.spi.NodeDispatch;
import io.yak.framework.workflow.engine.spi.NodeExecutor;
import io.yak.framework.workflow.engine.spi.NodePauseRequest;
import io.yak.framework.workflow.engine.spi.NodeResumeRequest;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.framework.workflow.engine.state.NodeAttemptFailureReason;
import io.yak.framework.workflow.engine.state.NodeAttemptStatus;
import io.yak.framework.workflow.engine.state.NodeExecutionStatus;
import io.yak.framework.workflow.engine.state.WorkflowExecutionStatus;
import io.yak.framework.workflow.engine.support.InMemoryExecutionRepository;
import io.yak.framework.workflow.engine.support.InMemoryWorkflowDefinitionRepository;
import io.yak.framework.workflow.engine.support.LocalExecutionLock;
import io.yak.framework.workflow.engine.support.LocalExecutionMailbox;
import io.yak.framework.workflow.engine.support.UuidIdGenerator;
import java.lang.runtime.SwitchBootstraps;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DefaultWorkflowRuntime {
    private final WorkflowDefinitionRepository definitionRepository;
    private final ExecutionRepository executionRepository;
    private final NodeExecutor nodeExecutor;
    private final ExecutionMailbox executionMailbox;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final WorkflowEventListener eventListener;
    private final WorkflowDefinitionValidator validator;
    private final WorkflowGraphBuilder graphBuilder;
    private final WorkflowScheduler scheduler;
    private final WorkflowCompletionResolver completionResolver;
    private final RetryDecider retryDecider;
    private final FailurePropagationPolicy failurePropagationPolicy;
    private final NodeInputResolver nodeInputResolver;

    public DefaultWorkflowRuntime(WorkflowDefinitionRepository definitionRepository, ExecutionRepository executionRepository, NodeExecutor nodeExecutor, ExecutionLock executionLock, IdGenerator idGenerator, Clock clock, WorkflowEventListener eventListener) {
        this(definitionRepository, executionRepository, nodeExecutor, new LocalExecutionMailbox(executionLock), idGenerator, clock, eventListener);
    }

    public DefaultWorkflowRuntime(WorkflowDefinitionRepository definitionRepository, ExecutionRepository executionRepository, NodeExecutor nodeExecutor, ExecutionMailbox executionMailbox, IdGenerator idGenerator, Clock clock, WorkflowEventListener eventListener) {
        this.definitionRepository = Objects.requireNonNull(definitionRepository, "definitionRepository");
        this.executionRepository = Objects.requireNonNull(executionRepository, "executionRepository");
        this.nodeExecutor = Objects.requireNonNull(nodeExecutor, "nodeExecutor");
        this.executionMailbox = Objects.requireNonNull(executionMailbox, "executionMailbox");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener");
        this.validator = new WorkflowDefinitionValidator();
        this.graphBuilder = new WorkflowGraphBuilder();
        this.scheduler = new DefaultWorkflowScheduler(new DefaultReadyNodeResolver(new DefaultTriggerRuleEvaluator()));
        this.completionResolver = new WorkflowCompletionResolver();
        this.retryDecider = new DefaultRetryDecider();
        this.failurePropagationPolicy = new DefaultFailurePropagationPolicy();
        this.nodeInputResolver = new NodeInputResolver();
    }

    public static DefaultWorkflowRuntime inMemory(NodeExecutor nodeExecutor) {
        return DefaultWorkflowRuntime.inMemory(nodeExecutor, WorkflowEventListener.noop());
    }

    public static DefaultWorkflowRuntime inMemory(NodeExecutor nodeExecutor, WorkflowEventListener eventListener) {
        return DefaultWorkflowRuntime.inMemory(nodeExecutor, eventListener, Clock.systemUTC());
    }

    public static DefaultWorkflowRuntime inMemory(NodeExecutor nodeExecutor, WorkflowEventListener eventListener, Clock clock) {
        return new DefaultWorkflowRuntime((WorkflowDefinitionRepository)new InMemoryWorkflowDefinitionRepository(), (ExecutionRepository)new InMemoryExecutionRepository(), nodeExecutor, new LocalExecutionLock(), (IdGenerator)new UuidIdGenerator(), clock, eventListener);
    }

    public void registerDefinition(WorkflowDefinition definition) {
        this.validator.validate(definition);
        this.definitionRepository.save(definition);
    }

    public WorkflowExecution start(String definitionId, Map<String, Object> input) {
        WorkflowDefinition definition = this.requireDefinition(definitionId);
        return this.createAndStartExecution(definition, input, null, null);
    }

    public WorkflowExecution submit(WorkflowCommand command) {
        Objects.requireNonNull(command, "command");
        return this.executionMailbox.submit(command, this::handleCommand);
    }

    public Optional<WorkflowExecution> findExecution(String executionId) {
        return this.executionRepository.findById(executionId);
    }

    private WorkflowExecution handleCommand(WorkflowCommand command) {
        WorkflowCommand workflowCommand = command;
        Objects.requireNonNull(workflowCommand);
        WorkflowCommand workflowCommand2 = workflowCommand;
        int n = 0;
        return switch (SwitchBootstraps.typeSwitch("typeSwitch", new Object[]{WorkflowCommand.NodeStarted.class, WorkflowCommand.NodeSucceeded.class, WorkflowCommand.NodeFailed.class, WorkflowCommand.NodePaused.class, WorkflowCommand.NodeResumed.class, WorkflowCommand.CheckTimeouts.class, WorkflowCommand.PauseWorkflow.class, WorkflowCommand.ResumeWorkflow.class, WorkflowCommand.CancelWorkflow.class, WorkflowCommand.ContinueAfterFailure.class, WorkflowCommand.RetryFailedNode.class, WorkflowCommand.RetryFailedNodes.class, WorkflowCommand.RestartWorkflow.class, WorkflowCommand.RerunFromNode.class}, (Object)workflowCommand2, n)) {
            default -> throw new MatchException(null, null);
            case 0 -> {
                WorkflowCommand.NodeStarted value = (WorkflowCommand.NodeStarted)workflowCommand2;
                yield this.handleNodeStarted(value);
            }
            case 1 -> {
                WorkflowCommand.NodeSucceeded value = (WorkflowCommand.NodeSucceeded)workflowCommand2;
                yield this.handleNodeSucceeded(value);
            }
            case 2 -> {
                WorkflowCommand.NodeFailed value = (WorkflowCommand.NodeFailed)workflowCommand2;
                yield this.handleNodeFailed(value);
            }
            case 3 -> {
                WorkflowCommand.NodePaused value = (WorkflowCommand.NodePaused)workflowCommand2;
                yield this.handleNodePaused(value);
            }
            case 4 -> {
                WorkflowCommand.NodeResumed value = (WorkflowCommand.NodeResumed)workflowCommand2;
                yield this.handleNodeResumed(value);
            }
            case 5 -> {
                WorkflowCommand.CheckTimeouts value = (WorkflowCommand.CheckTimeouts)workflowCommand2;
                yield this.handleCheckTimeouts(value);
            }
            case 6 -> {
                WorkflowCommand.PauseWorkflow value = (WorkflowCommand.PauseWorkflow)workflowCommand2;
                yield this.handlePauseWorkflow(value);
            }
            case 7 -> {
                WorkflowCommand.ResumeWorkflow value = (WorkflowCommand.ResumeWorkflow)workflowCommand2;
                yield this.handleResumeWorkflow(value);
            }
            case 8 -> {
                WorkflowCommand.CancelWorkflow value = (WorkflowCommand.CancelWorkflow)workflowCommand2;
                yield this.handleCancelWorkflow(value);
            }
            case 9 -> {
                WorkflowCommand.ContinueAfterFailure value = (WorkflowCommand.ContinueAfterFailure)workflowCommand2;
                yield this.handleContinueAfterFailure(value);
            }
            case 10 -> {
                WorkflowCommand.RetryFailedNode value = (WorkflowCommand.RetryFailedNode)workflowCommand2;
                yield this.handleRetryFailedNode(value);
            }
            case 11 -> {
                WorkflowCommand.RetryFailedNodes value = (WorkflowCommand.RetryFailedNodes)workflowCommand2;
                yield this.handleRetryFailedNodes(value);
            }
            case 12 -> {
                WorkflowCommand.RestartWorkflow value = (WorkflowCommand.RestartWorkflow)workflowCommand2;
                yield this.handleRestartWorkflow(value);
            }
            case 13 -> {
                WorkflowCommand.RerunFromNode value = (WorkflowCommand.RerunFromNode)workflowCommand2;
                yield this.handleRerunFromNode(value);
            }
        };
    }

    private WorkflowExecution handleNodeStarted(WorkflowCommand.NodeStarted command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        NodeExecution node = execution.node(command.nodeId());
        if (!this.shouldApplyStartCallback(node, command.attemptId())) {
            return execution.copy();
        }
        this.ensureCallbackLifecycle(execution);
        node.markRunning(this.now());
        execution.touch(this.now());
        this.save(execution);
        this.publish(WorkflowEvent.Type.NODE_STARTED, command.executionId(), command.nodeId(), command.attemptId(), null);
        return execution.copy();
    }

    private WorkflowExecution handlePauseWorkflow(WorkflowCommand.PauseWorkflow command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        if (execution.status() == WorkflowExecutionStatus.PAUSING || execution.status() == WorkflowExecutionStatus.PAUSED) {
            return execution.copy();
        }
        if (execution.status() != WorkflowExecutionStatus.RUNNING) {
            throw new IllegalStateException("Only a running workflow can pause: " + String.valueOf((Object)execution.status()));
        }
        String pauseReason = command.reason() == null || command.reason().isBlank() ? "Workflow pause requested" : command.reason();
        execution.transitionTo(WorkflowExecutionStatus.PAUSING, this.now());
        this.publish(WorkflowEvent.Type.WORKFLOW_PAUSE_REQUESTED, execution.id(), null, null, pauseReason);
        for (NodeExecution node : execution.nodes().values()) {
            NodePauseRequest request;
            if (node.status() != NodeExecutionStatus.SUBMITTED && node.status() != NodeExecutionStatus.RUNNING || this.nodeExecutor.pause(request = new NodePauseRequest(execution.id(), node.id(), node.nodeId(), node.currentAttemptId(), pauseReason)) != NodeControlResult.ACCEPTED) continue;
            node.markPausing();
            this.publish(WorkflowEvent.Type.NODE_PAUSE_REQUESTED, execution.id(), node.nodeId(), node.currentAttemptId(), pauseReason);
        }
        this.settlePauseIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleNodePaused(WorkflowCommand.NodePaused command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        NodeExecution node = execution.node(command.nodeId());
        if (execution.status() != WorkflowExecutionStatus.PAUSING || !node.isCurrentAttempt(command.attemptId()) || node.currentAttemptStatus() != NodeAttemptStatus.PAUSING) {
            return execution.copy();
        }
        node.markPaused(this.now());
        execution.touch(this.now());
        this.publish(WorkflowEvent.Type.NODE_PAUSED, command.executionId(), command.nodeId(), command.attemptId(), null);
        this.settlePauseIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleResumeWorkflow(WorkflowCommand.ResumeWorkflow command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        if (execution.status() == WorkflowExecutionStatus.RUNNING || execution.status() == WorkflowExecutionStatus.RESUMING) {
            return execution.copy();
        }
        if (execution.status() == WorkflowExecutionStatus.PAUSING) {
            throw new IllegalStateException("Workflow is still pausing; wait until PAUSED before resume");
        }
        if (execution.status() != WorkflowExecutionStatus.PAUSED) {
            throw new IllegalStateException("Only a paused workflow can resume: " + String.valueOf((Object)execution.status()));
        }
        execution.transitionTo(WorkflowExecutionStatus.RESUMING, this.now());
        this.publish(WorkflowEvent.Type.WORKFLOW_RESUME_REQUESTED, execution.id(), null, null, null);
        for (NodeExecution node : execution.nodes().values()) {
            if (node.status() != NodeExecutionStatus.PAUSED) continue;
            node.markResuming();
            this.nodeExecutor.resume(new NodeResumeRequest(execution.id(), node.id(), node.nodeId(), node.currentAttemptId()));
            this.publish(WorkflowEvent.Type.NODE_RESUME_REQUESTED, execution.id(), node.nodeId(), node.currentAttemptId(), null);
        }
        this.completeResumeIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleNodeResumed(WorkflowCommand.NodeResumed command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        NodeExecution node = execution.node(command.nodeId());
        if (execution.status() != WorkflowExecutionStatus.RESUMING || !node.isCurrentAttempt(command.attemptId()) || node.currentAttemptStatus() != NodeAttemptStatus.RESUMING) {
            return execution.copy();
        }
        node.markResumed(this.now());
        execution.touch(this.now());
        this.publish(WorkflowEvent.Type.NODE_RESUMED, command.executionId(), command.nodeId(), command.attemptId(), null);
        this.completeResumeIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleNodeSucceeded(WorkflowCommand.NodeSucceeded command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        NodeExecution node = execution.node(command.nodeId());
        if (!this.shouldApplyTerminalCallback(node, command.attemptId())) {
            return execution.copy();
        }
        this.ensureCallbackLifecycle(execution);
        WorkflowDefinition definition = this.requireDefinition(execution.definitionId());
        WorkflowGraph graph = this.graphBuilder.build(definition);
        node.markSuccess(command.output(), this.now());
        this.publish(WorkflowEvent.Type.NODE_SUCCEEDED, command.executionId(), command.nodeId(), command.attemptId(), null);
        if (execution.status() == WorkflowExecutionStatus.RUNNING) {
            List<NodeExecution> ready = this.scheduler.advance(definition, graph, execution, graph.successors(command.nodeId()));
            this.dispatchReady(definition, execution, ready);
        }
        this.finishIfPossible(execution);
        this.settleLifecycleIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleNodeFailed(WorkflowCommand.NodeFailed command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        NodeExecution node = execution.node(command.nodeId());
        if (!this.shouldApplyTerminalCallback(node, command.attemptId())) {
            return execution.copy();
        }
        this.ensureCallbackLifecycle(execution);
        WorkflowDefinition definition = this.requireDefinition(execution.definitionId());
        WorkflowGraph graph = this.graphBuilder.build(definition);
        this.failCurrentAttempt(definition, graph, execution, node, NodeAttemptFailureReason.EXECUTOR_FAILURE, WorkflowEvent.Type.NODE_FAILED, command.errorMessage());
        this.finishIfPossible(execution);
        this.settleLifecycleIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleCheckTimeouts(WorkflowCommand.CheckTimeouts command) {
        Instant currentTime;
        WorkflowExecution execution = this.requireExecution(command.executionId());
        if (execution.status() != WorkflowExecutionStatus.RUNNING && execution.status() != WorkflowExecutionStatus.PAUSING) {
            return execution.copy();
        }
        WorkflowDefinition definition = this.requireDefinition(execution.definitionId());
        if (this.isWorkflowTimedOut(definition, execution, currentTime = this.now())) {
            this.timeoutWorkflow(definition, execution, currentTime);
            return execution.copy();
        }
        WorkflowGraph graph = this.graphBuilder.build(definition);
        for (NodeExecution node : new ArrayList<NodeExecution>(execution.nodes().values())) {
            Instant executionDeadline;
            if (execution.status().isTerminal()) break;
            NodeDefinition nodeDefinition = definition.node(node.nodeId());
            NodeTimeoutPolicy timeoutPolicy = nodeDefinition.timeoutPolicy();
            if (node.status() == NodeExecutionStatus.SUBMITTED && timeoutPolicy.hasDispatchTimeout()) {
                Instant dispatchDeadline = node.currentAttemptDispatchDeadline(timeoutPolicy.dispatchTimeout());
                if (dispatchDeadline == null || !this.hasReached(currentTime, dispatchDeadline)) continue;
                this.timeoutCurrentAttempt(definition, graph, execution, node, NodeAttemptFailureReason.DISPATCH_TIMEOUT, WorkflowEvent.Type.NODE_DISPATCH_TIMED_OUT, "Node dispatch timed out after " + String.valueOf(timeoutPolicy.dispatchTimeout()));
                continue;
            }
            if (node.status() != NodeExecutionStatus.RUNNING || !timeoutPolicy.hasExecutionTimeout() || (executionDeadline = node.currentAttemptExecutionDeadline(timeoutPolicy.executionTimeout())) == null || !this.hasReached(currentTime, executionDeadline)) continue;
            this.timeoutCurrentAttempt(definition, graph, execution, node, NodeAttemptFailureReason.EXECUTION_TIMEOUT, WorkflowEvent.Type.NODE_EXECUTION_TIMED_OUT, "Node execution timed out after " + String.valueOf(timeoutPolicy.executionTimeout()));
        }
        this.finishIfPossible(execution);
        this.settleLifecycleIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleContinueAfterFailure(WorkflowCommand.ContinueAfterFailure command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        this.ensureNotPauseLifecycle(execution);
        WorkflowDefinition definition = this.requireDefinition(execution.definitionId());
        WorkflowGraph graph = this.graphBuilder.build(definition);
        NodeExecution failedNode = execution.node(command.nodeId());
        if (failedNode.status() != NodeExecutionStatus.FAILED) {
            throw new IllegalStateException("Only a failed node can continue downstream: " + command.nodeId());
        }
        if (failedNode.downstreamContinuationAllowed()) {
            return execution.copy();
        }
        if (execution.status() == WorkflowExecutionStatus.SUCCESS) {
            throw new IllegalStateException("A successful workflow has no failed node to continue");
        }
        if (execution.status().isTerminal()) {
            execution.transitionTo(WorkflowExecutionStatus.RUNNING, this.now());
        }
        execution.resumeScheduling();
        failedNode.allowDownstreamContinuation();
        for (String descendantId : graph.descendants(command.nodeId())) {
            NodeExecution descendant = execution.node(descendantId);
            if (descendant.status() != NodeExecutionStatus.UPSTREAM_FAILED) continue;
            descendant.resetSyntheticState();
        }
        List<NodeExecution> ready = this.scheduler.advance(definition, graph, execution, graph.successors(command.nodeId()));
        this.dispatchReady(definition, execution, ready);
        this.finishIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleRetryFailedNode(WorkflowCommand.RetryFailedNode command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        this.ensureNotPauseLifecycle(execution);
        WorkflowDefinition definition = this.requireDefinition(execution.definitionId());
        WorkflowGraph graph = this.graphBuilder.build(definition);
        NodeExecution failedNode = execution.node(command.nodeId());
        if (failedNode.status() != NodeExecutionStatus.FAILED) {
            throw new IllegalStateException("Only a failed node can be retried: " + command.nodeId());
        }
        if (failedNode.downstreamContinuationAllowed()) {
            throw new IllegalStateException("Cannot retry a failed node after its downstream branch was continued: " + command.nodeId());
        }
        if (execution.status() == WorkflowExecutionStatus.SUCCESS) {
            throw new IllegalStateException("A successful workflow has no failed node to retry");
        }
        if (execution.status() == WorkflowExecutionStatus.CANCELED) {
            throw new IllegalStateException("A canceled workflow cannot retry a single failed node");
        }
        if (execution.status().isTerminal()) {
            execution.transitionTo(WorkflowExecutionStatus.RUNNING, this.now());
        }
        execution.resumeScheduling();
        failedNode.resetForManualRetry();
        for (String descendantId : graph.descendants(command.nodeId())) {
            NodeExecution descendant = execution.node(descendantId);
            if (descendant.status() != NodeExecutionStatus.UPSTREAM_FAILED) continue;
            descendant.resetSyntheticState();
        }
        List<NodeExecution> ready = this.scheduler.advance(definition, graph, execution, List.of(command.nodeId()));
        this.dispatchReady(definition, execution, ready);
        this.finishIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleCancelWorkflow(WorkflowCommand.CancelWorkflow command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        if (execution.status().isTerminal()) {
            return execution.copy();
        }
        execution.stopScheduling();
        this.cancelNonTerminalNodes(execution, command.reason());
        execution.transitionTo(WorkflowExecutionStatus.CANCELED, this.now());
        this.save(execution);
        this.publish(WorkflowEvent.Type.WORKFLOW_CANCELED, command.executionId(), null, null, command.reason());
        return execution.copy();
    }

    private WorkflowExecution handleRetryFailedNodes(WorkflowCommand.RetryFailedNodes command) {
        WorkflowExecution execution = this.requireExecution(command.executionId());
        if (!execution.status().isTerminal() || execution.status() == WorkflowExecutionStatus.SUCCESS) {
            throw new IllegalStateException("Only a failed, canceled, warning, or timed out workflow can be retried");
        }
        WorkflowDefinition definition = this.requireDefinition(execution.definitionId());
        WorkflowGraph graph = this.graphBuilder.build(definition);
        LinkedHashSet<String> resetNodes = new LinkedHashSet<String>();
        for (NodeExecution node : execution.nodes().values()) {
            if (node.status() == NodeExecutionStatus.FAILED) {
                node.resetForManualRetry();
                resetNodes.add(node.nodeId());
                continue;
            }
            if (node.status() != NodeExecutionStatus.UPSTREAM_FAILED && node.status() != NodeExecutionStatus.SKIPPED && node.status() != NodeExecutionStatus.CANCELED) continue;
            node.resetSyntheticState();
            resetNodes.add(node.nodeId());
        }
        if (resetNodes.isEmpty()) {
            throw new IllegalStateException("Workflow has no retryable nodes");
        }
        execution.resumeScheduling();
        execution.transitionTo(WorkflowExecutionStatus.RUNNING, this.now());
        List<NodeExecution> ready = this.scheduler.advance(definition, graph, execution, resetNodes);
        this.dispatchReady(definition, execution, ready);
        this.finishIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private WorkflowExecution handleRestartWorkflow(WorkflowCommand.RestartWorkflow command) {
        WorkflowExecution source = this.requireExecution(command.executionId());
        WorkflowDefinition definition = this.requireDefinition(source.definitionId());
        return this.createAndStartExecution(definition, source.input(), command.executionId(), null);
    }

    private WorkflowExecution handleRerunFromNode(WorkflowCommand.RerunFromNode command) {
        WorkflowExecution source = this.requireExecution(command.executionId());
        WorkflowDefinition definition = this.requireDefinition(source.definitionId());
        WorkflowGraph graph = this.graphBuilder.build(definition);
        if (!definition.nodes().containsKey(command.nodeId())) {
            throw new IllegalArgumentException("Unknown node: " + command.nodeId());
        }
        return this.createAndStartExecution(definition, source.input(), command.executionId(), rerun -> {
            Set<String> ancestors = graph.ancestors(command.nodeId());
            LinkedHashSet<String> descendants = new LinkedHashSet<String>(graph.descendants(command.nodeId()));
            descendants.add(command.nodeId());
            for (String ancestor : ancestors) {
                NodeExecution previous = source.node(ancestor);
                if (!previous.isEffectiveSuccess()) {
                    throw new IllegalStateException("Cannot rerun from " + command.nodeId() + ": ancestor " + ancestor + " was not successful");
                }
                rerun.node(ancestor).markCopiedSuccess(previous.output());
            }
            for (String candidate : graph.nodes()) {
                if (ancestors.contains(candidate) || descendants.contains(candidate)) continue;
                rerun.node(candidate).transitionTo(NodeExecutionStatus.SKIPPED);
            }
            return Set.of(command.nodeId());
        });
    }

    private WorkflowExecution createAndStartExecution(WorkflowDefinition definition, Map<String, Object> input, String sourceExecutionId, ExecutionInitializer initializer) {
        this.validator.validate(definition);
        WorkflowGraph graph = this.graphBuilder.build(definition);
        String executionId = this.idGenerator.nextId();
        LinkedHashMap<String, NodeExecution> nodes = new LinkedHashMap<String, NodeExecution>();
        for (NodeDefinition node : definition.nodes().values()) {
            nodes.put(node.id(), new NodeExecution(this.idGenerator.nextId(), executionId, node.id(), node.failurePolicy()));
        }
        WorkflowExecution execution = new WorkflowExecution(executionId, definition.id(), sourceExecutionId, input, nodes, this.now());
        Collection<String> candidates = graph.startNodes();
        if (initializer != null) {
            candidates = initializer.initialize(execution);
        }
        execution.transitionTo(WorkflowExecutionStatus.RUNNING, this.now());
        this.save(execution);
        this.publish(WorkflowEvent.Type.WORKFLOW_STARTED, executionId, null, null, null);
        List<NodeExecution> ready = this.scheduler.advance(definition, graph, execution, candidates);
        this.dispatchReady(definition, execution, ready);
        this.finishIfPossible(execution);
        this.save(execution);
        return execution.copy();
    }

    private void failCurrentAttempt(WorkflowDefinition definition, WorkflowGraph graph, WorkflowExecution execution, NodeExecution node, NodeAttemptFailureReason failureReason, WorkflowEvent.Type failureEventType, String errorMessage) {
        String attemptId = node.currentAttemptId();
        node.markFailure(failureReason, errorMessage, this.now());
        this.publish(failureEventType, execution.id(), node.nodeId(), attemptId, errorMessage);
        if (this.retryDecider.shouldRetry(definition.node(node.nodeId()), node)) {
            node.transitionTo(NodeExecutionStatus.READY);
            this.publish(WorkflowEvent.Type.NODE_RETRY_SCHEDULED, execution.id(), node.nodeId(), attemptId, errorMessage);
            if (execution.status() == WorkflowExecutionStatus.RUNNING && !execution.schedulingStopped()) {
                this.dispatchReady(definition, execution, List.of(node));
            }
        } else {
            this.handleFinalFailure(definition, graph, execution, node);
        }
    }

    private void timeoutCurrentAttempt(WorkflowDefinition definition, WorkflowGraph graph, WorkflowExecution execution, NodeExecution node, NodeAttemptFailureReason failureReason, WorkflowEvent.Type timeoutEventType, String errorMessage) {
        this.nodeExecutor.cancel(new NodeCancellation(execution.id(), node.id(), node.nodeId(), node.currentAttemptId(), errorMessage));
        this.failCurrentAttempt(definition, graph, execution, node, failureReason, timeoutEventType, errorMessage);
    }

    private boolean isWorkflowTimedOut(WorkflowDefinition definition, WorkflowExecution execution, Instant currentTime) {
        Instant deadline = execution.workflowDeadline(definition.timeoutPolicy().timeout());
        return definition.timeoutPolicy().enabled() && deadline != null && this.hasReached(currentTime, deadline);
    }

    private void timeoutWorkflow(WorkflowDefinition definition, WorkflowExecution execution, Instant currentTime) {
        String message = "Workflow timed out after " + String.valueOf(definition.timeoutPolicy().timeout());
        execution.stopScheduling();
        this.cancelNonTerminalNodes(execution, message);
        execution.transitionTo(WorkflowExecutionStatus.TIMED_OUT, currentTime);
        this.save(execution);
        this.publish(WorkflowEvent.Type.WORKFLOW_TIMED_OUT, execution.id(), null, null, message);
    }

    private boolean hasReached(Instant currentTime, Instant deadline) {
        return !currentTime.isBefore(deadline);
    }

    private void handleFinalFailure(WorkflowDefinition definition, WorkflowGraph graph, WorkflowExecution execution, NodeExecution failedNode) {
        FailureHandlingResult result = this.failurePropagationPolicy.onFinalFailure(definition, execution, failedNode);
        if (result.stopScheduling()) {
            execution.stopScheduling();
        }
        if (result.terminateActiveNodes()) {
            this.cancelNonTerminalNodes(execution, "Workflow terminated after node failure");
            return;
        }
        if (execution.status() != WorkflowExecutionStatus.RUNNING) {
            return;
        }
        Set<String> candidates = result.stopScheduling() ? this.waitingNodeIds(execution) : graph.successors(failedNode.nodeId());
        List<NodeExecution> ready = this.scheduler.advance(definition, graph, execution, candidates);
        this.dispatchReady(definition, execution, ready);
    }

    private void dispatchReady(WorkflowDefinition definition, WorkflowExecution execution, Collection<NodeExecution> readyNodes) {
        if (execution.status() != WorkflowExecutionStatus.RUNNING || execution.schedulingStopped()) {
            return;
        }
        WorkflowGraph graph = this.graphBuilder.build(definition);
        for (NodeExecution node : readyNodes) {
            NodeDefinition nodeDefinition = definition.node(node.nodeId());
            Instant availableAt = node.attempts().isEmpty() ? this.now() : this.now().plus(nodeDefinition.retryPolicy().delay());
            NodeAttempt attempt = node.beginAttempt(this.idGenerator.nextId(), availableAt);
            Map<String, Map<String, Object>> predecessorOutputs = this.collectPredecessorOutputs(execution, graph.predecessors(node.nodeId()));
            Map<String, Object> nodeInput = this.nodeInputResolver.resolve(nodeDefinition.inputMapping(), execution.input(), predecessorOutputs);
            Instant dispatchDeadline = attempt.dispatchDeadline(nodeDefinition.timeoutPolicy().dispatchTimeout());
            this.save(execution);
            this.nodeExecutor.submit(new NodeDispatch(execution.id(), node.id(), node.nodeId(), attempt.id(), attempt.attemptNumber(), attempt.availableAt(), execution.input(), nodeDefinition.configuration(), predecessorOutputs, nodeInput, dispatchDeadline, nodeDefinition.timeoutPolicy().executionTimeout()));
            this.publish(WorkflowEvent.Type.NODE_SUBMITTED, execution.id(), node.nodeId(), attempt.id(), null);
        }
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

    private void cancelNonTerminalNodes(WorkflowExecution execution, String reason) {
        ArrayList<NodeExecution> nodes = new ArrayList<NodeExecution>(execution.nodes().values());
        for (NodeExecution node : nodes) {
            if (node.status().isTerminal()) continue;
            if (node.status() == NodeExecutionStatus.SUBMITTED || node.status() == NodeExecutionStatus.RUNNING || node.status() == NodeExecutionStatus.PAUSING || node.status() == NodeExecutionStatus.PAUSED || node.status() == NodeExecutionStatus.RESUMING) {
                this.nodeExecutor.cancel(new NodeCancellation(execution.id(), node.id(), node.nodeId(), node.currentAttemptId(), reason));
            }
            node.markCanceled(this.now());
        }
    }

    private void finishIfPossible(WorkflowExecution execution) {
        if (execution.status() == WorkflowExecutionStatus.CANCELED || execution.status() == WorkflowExecutionStatus.TIMED_OUT || execution.status() == WorkflowExecutionStatus.PAUSED) {
            return;
        }
        this.completionResolver.resolve(execution).ifPresent(status -> {
            execution.transitionTo((WorkflowExecutionStatus)((Object)status), this.now());
            this.publish(WorkflowEvent.Type.WORKFLOW_COMPLETED, execution.id(), null, null, status.name());
        });
    }

    private void settleLifecycleIfPossible(WorkflowExecution execution) {
        if (execution.status() == WorkflowExecutionStatus.PAUSING) {
            this.settlePauseIfPossible(execution);
        } else if (execution.status() == WorkflowExecutionStatus.RESUMING) {
            this.completeResumeIfPossible(execution);
        }
    }

    private void settlePauseIfPossible(WorkflowExecution execution) {
        if (execution.status() != WorkflowExecutionStatus.PAUSING) {
            return;
        }
        boolean activeUnsettled = execution.nodes().values().stream().map(NodeExecution::status).anyMatch(status -> status == NodeExecutionStatus.SUBMITTED || status == NodeExecutionStatus.RUNNING || status == NodeExecutionStatus.PAUSING || status == NodeExecutionStatus.RESUMING);
        if (!activeUnsettled) {
            execution.transitionTo(WorkflowExecutionStatus.PAUSED, this.now());
            this.publish(WorkflowEvent.Type.WORKFLOW_PAUSED, execution.id(), null, null, null);
        }
    }

    private void completeResumeIfPossible(WorkflowExecution execution) {
        if (execution.status() != WorkflowExecutionStatus.RESUMING) {
            return;
        }
        boolean waitingForAttempt = execution.nodes().values().stream().map(NodeExecution::status).anyMatch(status -> status == NodeExecutionStatus.PAUSED || status == NodeExecutionStatus.RESUMING || status == NodeExecutionStatus.PAUSING);
        if (waitingForAttempt) {
            return;
        }
        execution.transitionTo(WorkflowExecutionStatus.RUNNING, this.now());
        this.publish(WorkflowEvent.Type.WORKFLOW_RESUMED, execution.id(), null, null, null);
        WorkflowDefinition definition = this.requireDefinition(execution.definitionId());
        WorkflowGraph graph = this.graphBuilder.build(definition);
        if (!execution.schedulingStopped()) {
            List<NodeExecution> deferredReady = execution.nodes().values().stream().filter(node -> node.status() == NodeExecutionStatus.READY).toList();
            this.dispatchReady(definition, execution, deferredReady);
        }
        List<NodeExecution> ready = this.scheduler.advance(definition, graph, execution, this.waitingNodeIds(execution));
        this.dispatchReady(definition, execution, ready);
        this.finishIfPossible(execution);
    }

    private boolean shouldApplyStartCallback(NodeExecution node, String attemptId) {
        return node.isCurrentAttempt(attemptId) && node.currentAttemptStatus() == NodeAttemptStatus.SUBMITTED;
    }

    private boolean shouldApplyTerminalCallback(NodeExecution node, String attemptId) {
        if (!node.isCurrentAttempt(attemptId)) {
            return false;
        }
        NodeAttemptStatus status = node.currentAttemptStatus();
        return status == NodeAttemptStatus.SUBMITTED || status == NodeAttemptStatus.RUNNING || status == NodeAttemptStatus.PAUSING || status == NodeAttemptStatus.RESUMING;
    }

    private Set<String> waitingNodeIds(WorkflowExecution execution) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        for (NodeExecution node : execution.nodes().values()) {
            if (node.status() != NodeExecutionStatus.WAITING) continue;
            result.add(node.nodeId());
        }
        return result;
    }

    private WorkflowDefinition requireDefinition(String definitionId) {
        return this.definitionRepository.findById(definitionId).orElseThrow(() -> new IllegalArgumentException("Workflow definition not found: " + definitionId));
    }

    private WorkflowExecution requireExecution(String executionId) {
        return this.executionRepository.findById(executionId).orElseThrow(() -> new IllegalArgumentException("Workflow execution not found: " + executionId));
    }

    private void ensureCallbackLifecycle(WorkflowExecution execution) {
        WorkflowExecutionStatus status = execution.status();
        if (status != WorkflowExecutionStatus.RUNNING && status != WorkflowExecutionStatus.PAUSING && status != WorkflowExecutionStatus.RESUMING) {
            throw new IllegalStateException("Workflow does not accept executor callbacks: " + String.valueOf((Object)status));
        }
    }

    private void ensureNotPauseLifecycle(WorkflowExecution execution) {
        if (execution.status().isPauseLifecycle()) {
            throw new IllegalStateException("Workflow recovery is unavailable while pause/resume is in progress: " + String.valueOf((Object)execution.status()));
        }
    }

    private void save(WorkflowExecution execution) {
        this.executionRepository.save(execution);
    }

    private Instant now() {
        return this.clock.instant();
    }

    private void publish(WorkflowEvent.Type type, String executionId, String nodeId, String attemptId, String message) {
        this.eventListener.onEvent(new WorkflowEvent(type, executionId, nodeId, attemptId, message, this.now()));
    }

    @FunctionalInterface
    private static interface ExecutionInitializer {
        public Collection<String> initialize(WorkflowExecution var1);
    }
}

