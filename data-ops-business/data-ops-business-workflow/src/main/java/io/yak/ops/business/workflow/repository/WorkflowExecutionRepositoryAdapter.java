package io.yak.ops.business.workflow.repository;

import io.yak.framework.workflow.engine.definition.NodeFailurePolicy;
import io.yak.framework.workflow.engine.execution.NodeAttemptSnapshot;
import io.yak.framework.workflow.engine.execution.NodeExecutionSnapshot;
import io.yak.framework.workflow.engine.execution.WorkflowExecution;
import io.yak.framework.workflow.engine.execution.WorkflowExecutionSnapshot;
import io.yak.framework.workflow.engine.spi.ExecutionRepository;
import io.yak.framework.workflow.engine.state.NodeAttemptFailureReason;
import io.yak.framework.workflow.engine.state.NodeAttemptStatus;
import io.yak.framework.workflow.engine.state.NodeExecutionStatus;
import io.yak.framework.workflow.engine.state.WorkflowExecutionStatus;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.domain.WorkflowExecutionTerminalEvent;
import io.yak.ops.business.workflow.domain.WorkflowScheduleLaunchBindingScope;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeAttemptPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis-Plus adapter for the Yak Framework execution repository SPI. */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowExecutionRepositoryAdapter implements ExecutionRepository {
  private final WorkflowExecutionDao executionDao;
  private final WorkflowScheduleTriggerDao scheduleTriggerDao;
  private final WorkflowJsonCodec json;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void save(WorkflowExecution execution) {
    WorkflowExecutionSnapshot snapshot = execution.snapshot();
    // Yak Framework transitions a new execution to RUNNING before its first repository save.
    // Only scheduled launches need the existence lookup; normal runtime saves stay on the hot upsert path.
    String launchTriggerId = WorkflowScheduleLaunchBindingScope.currentTriggerId();
    boolean firstScheduledPersistence = launchTriggerId != null
        && executionDao.selectExecution(snapshot.id()) == null;
    executionDao.upsertExecution(toPO(snapshot));
    bindFirstScheduledExecution(snapshot, launchTriggerId, firstScheduledPersistence);
    for (NodeExecutionSnapshot node : snapshot.nodes()) {
      executionDao.upsertNodeExecution(toPO(node));
      for (NodeAttemptSnapshot attempt : node.attempts()) {
        executionDao.upsertNodeAttempt(toPO(snapshot.id(), node, attempt));
      }
    }
    if (snapshot.status().isTerminal()) {
      eventPublisher.publishEvent(new WorkflowExecutionTerminalEvent(
          snapshot.id(), snapshot.status().name(), snapshot.endedAt()));
    }
  }

  private void bindFirstScheduledExecution(
      WorkflowExecutionSnapshot snapshot,
      String triggerId,
      boolean firstScheduledPersistence) {
    if (!firstScheduledPersistence || triggerId == null) return;

    int bound = scheduleTriggerDao.bindPreparedExecution(
        triggerId,
        snapshot.id(),
        snapshot.status().name());
    if (bound == 1) return;

    String existingExecutionId = scheduleTriggerDao.selectExecutionIdByTrigger(triggerId);
    if (!Objects.equals(existingExecutionId, snapshot.id())) {
      throw new IllegalStateException(
          "调度 Trigger 无法原子绑定 WorkflowExecution：triggerId="
              + triggerId + ", executionId=" + snapshot.id());
    }
  }

  @Override
  public Optional<WorkflowExecution> findById(String executionId) {
    WorkflowExecutionPO root = executionDao.selectExecution(executionId);
    if (root == null) return Optional.empty();

    Map<String, List<NodeAttemptSnapshot>> attemptsByNodeExecution = new LinkedHashMap<>();
    for (WorkflowNodeAttemptPO attempt : executionDao.selectNodeAttempts(executionId)) {
      attemptsByNodeExecution
          .computeIfAbsent(attempt.getNodeExecutionId(), ignored -> new ArrayList<>())
          .add(toSnapshot(attempt));
    }

    List<NodeExecutionSnapshot> nodes = executionDao.selectNodeExecutions(executionId).stream()
        .map(node -> toSnapshot(
            node,
            attemptsByNodeExecution.getOrDefault(node.getId(), List.of())))
        .toList();

    WorkflowExecutionSnapshot snapshot = new WorkflowExecutionSnapshot(
        root.getId(),
        root.getDefinitionId(),
        root.getSourceExecutionId(),
        json.readMap(root.getInputJson()),
        nodes,
        root.getCreatedAt(),
        WorkflowExecutionStatus.valueOf(root.getStatus()),
        Boolean.TRUE.equals(root.getSchedulingStopped()),
        root.getRunStartedAt(),
        root.getPausedAt(),
        Duration.ofMillis(zero(root.getPausedDurationMs())),
        root.getUpdatedAt(),
        root.getEndedAt());
    return Optional.of(WorkflowExecution.restore(snapshot));
  }

  private WorkflowExecutionPO toPO(WorkflowExecutionSnapshot value) {
    WorkflowExecutionPO po = new WorkflowExecutionPO();
    po.setId(value.id());
    po.setDefinitionId(value.definitionId());
    po.setSourceExecutionId(value.sourceExecutionId());
    po.setStatus(value.status().name());
    po.setInputJson(json.write(value.input()));
    po.setSchedulingStopped(value.schedulingStopped());
    po.setRunStartedAt(value.runStartedAt());
    po.setPausedAt(value.pausedAt());
    po.setPausedDurationMs(value.pausedDuration().toMillis());
    po.setCreatedAt(value.createdAt());
    po.setUpdatedAt(value.updatedAt());
    po.setEndedAt(value.endedAt());
    return po;
  }

  private WorkflowNodeExecutionPO toPO(NodeExecutionSnapshot value) {
    WorkflowNodeExecutionPO po = new WorkflowNodeExecutionPO();
    po.setId(value.id());
    po.setWorkflowExecutionId(value.workflowExecutionId());
    po.setNodeId(value.nodeId());
    po.setFailurePolicy(value.failurePolicy().name());
    po.setStatus(value.status().name());
    po.setOutputJson(json.write(value.output()));
    po.setErrorMessage(value.errorMessage());
    po.setFailureHandled(value.failureHandled());
    po.setDownstreamContinuationAllowed(value.downstreamContinuationAllowed());
    return po;
  }

  private WorkflowNodeAttemptPO toPO(
      String workflowExecutionId,
      NodeExecutionSnapshot node,
      NodeAttemptSnapshot value) {
    WorkflowNodeAttemptPO po = new WorkflowNodeAttemptPO();
    po.setId(value.id());
    po.setNodeExecutionId(node.id());
    po.setWorkflowExecutionId(workflowExecutionId);
    po.setNodeId(node.nodeId());
    po.setAttemptNo(value.attemptNumber());
    po.setAvailableAt(value.availableAt());
    po.setStatus(value.status().name());
    po.setResumeTargetStatus(
        value.resumeTargetStatus() == null ? null : value.resumeTargetStatus().name());
    po.setStartedAt(value.startedAt());
    po.setPausedAt(value.pausedAt());
    po.setPausedDurationMs(value.pausedDuration().toMillis());
    po.setEndedAt(value.endedAt());
    po.setErrorMessage(value.errorMessage());
    po.setFailureReason(value.failureReason() == null ? null : value.failureReason().name());
    return po;
  }

  private NodeExecutionSnapshot toSnapshot(
      WorkflowNodeExecutionPO po,
      List<NodeAttemptSnapshot> attempts) {
    return new NodeExecutionSnapshot(
        po.getId(),
        po.getWorkflowExecutionId(),
        po.getNodeId(),
        NodeFailurePolicy.valueOf(po.getFailurePolicy()),
        NodeExecutionStatus.valueOf(po.getStatus()),
        attempts,
        json.readMap(po.getOutputJson()),
        po.getErrorMessage(),
        Boolean.TRUE.equals(po.getFailureHandled()),
        Boolean.TRUE.equals(po.getDownstreamContinuationAllowed()));
  }

  private NodeAttemptSnapshot toSnapshot(WorkflowNodeAttemptPO po) {
    return new NodeAttemptSnapshot(
        po.getId(),
        po.getAttemptNo(),
        po.getAvailableAt(),
        NodeAttemptStatus.valueOf(po.getStatus()),
        po.getResumeTargetStatus() == null ? null : NodeAttemptStatus.valueOf(po.getResumeTargetStatus()),
        po.getStartedAt(),
        po.getPausedAt(),
        Duration.ofMillis(zero(po.getPausedDurationMs())),
        po.getEndedAt(),
        po.getErrorMessage(),
        po.getFailureReason() == null ? null : NodeAttemptFailureReason.valueOf(po.getFailureReason()));
  }

  private long zero(Long value) {
    return value == null ? 0L : value;
  }
}
