package io.yak.ops.business.workflow.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/** In-process fallback used by focused runtime tests and database-disabled development. */
public final class InMemoryWorkflowRuntimeRepository implements WorkflowRuntimeRepository {

  private final ConcurrentMap<String, RuntimeMetadataRecord> definitionMetadata =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, RuntimeMetadataRecord> metadata = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> executionDefinitions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> externalExecutions = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, String> executionStatuses = new ConcurrentHashMap<>();
  private final ConcurrentLinkedDeque<String> executionOrder = new ConcurrentLinkedDeque<>();

  @Override
  public void prepareMetadata(String definitionId, RuntimeMetadataRecord value) {
    definitionMetadata.put(definitionId, value);
  }

  /** Test/runtime helper to associate an execution with its durable engine definition. */
  public void bindDefinition(String executionId, String definitionId) {
    executionDefinitions.put(executionId, definitionId);
  }

  @Override
  public void saveMetadata(String executionId, RuntimeMetadataRecord value) {
    metadata.put(executionId, value);
    executionOrder.remove(executionId);
    executionOrder.addFirst(executionId);
  }

  public void markExecutionStatus(String executionId, String status) {
    if (status != null) executionStatuses.put(executionId, status);
  }

  @Override
  public Optional<RuntimeMetadataRecord> findMetadata(String executionId) {
    RuntimeMetadataRecord direct = metadata.get(executionId);
    if (direct != null) return Optional.of(direct);
    String definitionId = executionDefinitions.get(executionId);
    return definitionId == null
        ? Optional.empty()
        : Optional.ofNullable(definitionMetadata.get(definitionId));
  }

  @Override
  public List<String> listExecutionIds() {
    return List.copyOf(executionOrder);
  }

  @Override
  public List<String> findRecoverableExecutionIds() {
    List<String> result = new ArrayList<>();
    for (String id : executionOrder) {
      String status = executionStatuses.get(id);
      if (status == null
          || "CREATED".equals(status)
          || "RUNNING".equals(status)
          || "PAUSING".equals(status)
          || "PAUSED".equals(status)
          || "RESUMING".equals(status)) {
        result.add(id);
      }
    }
    return List.copyOf(result);
  }

  @Override
  public void bindExternalExecution(String attemptId, String externalExecutionId) {
    if (attemptId != null && externalExecutionId != null) {
      externalExecutions.put(attemptId, externalExecutionId);
    }
  }

  @Override
  public Optional<String> findExternalExecution(String attemptId) {
    return Optional.ofNullable(externalExecutions.get(attemptId));
  }
}
