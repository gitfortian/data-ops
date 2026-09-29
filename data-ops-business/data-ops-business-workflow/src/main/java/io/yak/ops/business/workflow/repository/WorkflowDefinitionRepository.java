package io.yak.ops.business.workflow.repository;

import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.workflow.domain.WorkflowEdgeSpec;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable business catalog boundary for editable workflows and immutable published versions. */
public interface WorkflowDefinitionRepository {

  List<DefinitionRecord> loadDefinitions();

  /** Current-Project lookup. Durable implementations must fail closed below this boundary. */
  default Optional<DefinitionRecord> findDefinition(String workflowId) {
    if (workflowId == null || workflowId.isBlank()) return Optional.empty();
    return loadDefinitions().stream()
        .filter(value -> workflowId.trim().equals(value.id()))
        .findFirst();
  }

  List<VersionRecord> loadVersions(String workflowId);

  void saveDefinition(DefinitionRecord definition);

  /** Persist a new immutable version and advance the current definition projection atomically. */
  void publish(DefinitionRecord definition, VersionRecord version);

  void deleteDefinition(String workflowId);

  /** Database-disabled fallback keeps WorkflowDefinitionManager's in-memory catalog authoritative. */
  default boolean authoritative() {
    return true;
  }

  record DefinitionRecord(
      String id,
      String name,
      String description,
      String status,
      String failureStrategy,
      List<WorkflowNodeSpec> nodes,
      List<WorkflowEdgeSpec> edges,
      Map<String, Object> input,
      Map<String, Object> editorMeta,
      long workflowTimeoutSeconds,
      long draftRevision,
      int latestVersionNo,
      String activeVersionId,
      String latestExecutionId,
      String latestExecutionStatus,
      Instant createTime,
      Instant updateTime) {
  }

  record VersionRecord(
      String id,
      String workflowId,
      int versionNo,
      long draftRevision,
      WorkflowRunSpec runSpec,
      Map<String, Object> editorMeta,
      Map<String, TaskVersionSnapshot> taskVersionsByNode,
      Instant publishedAt) {
  }
}
