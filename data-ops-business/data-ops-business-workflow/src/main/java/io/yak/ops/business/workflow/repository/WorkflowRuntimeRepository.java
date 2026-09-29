package io.yak.ops.business.workflow.repository;

import io.yak.ops.business.job.task.TaskVersionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Durable runtime metadata and external-attempt binding needed for restart recovery. */
public interface WorkflowRuntimeRepository {

  /** Persist recovery metadata on the immutable engine definition before an execution is created. */
  void prepareMetadata(String definitionId, RuntimeMetadataRecord metadata);

  /** Copy recovery metadata to the concrete execution after the engine creates it. */
  void saveMetadata(String executionId, RuntimeMetadataRecord metadata);

  /** Load execution metadata, falling back to its immutable definition snapshot when necessary. */
  Optional<RuntimeMetadataRecord> findMetadata(String executionId);

  List<String> listExecutionIds();

  /** Current-Project recovery list used after ProjectContext has been restored. */
  List<String> findRecoverableExecutionIds();

  /**
   * Explicit cross-Project startup discovery. Business IO must restore the returned Project before
   * calling any ordinary repository/runtime method.
   */
  default List<ProjectExecutionRef> findRecoverableExecutionsForDispatch() {
    return List.of();
  }

  void bindExternalExecution(String attemptId, String externalExecutionId);

  Optional<String> findExternalExecution(String attemptId);

  record ProjectExecutionRef(long projectId, String executionId) {
    public ProjectExecutionRef {
      if (projectId <= 0L) throw new IllegalArgumentException("projectId must be positive");
      if (executionId == null || executionId.isBlank()) {
        throw new IllegalArgumentException("executionId must not be blank");
      }
    }
  }

  record RuntimeMetadataRecord(
      String name,
      int edgeCount,
      long workflowTimeoutSeconds,
      String failureStrategy,
      String workflowVersionId,
      Integer workflowVersionNo,
      boolean testRun,
      Map<String, NodeMetadataRecord> nodes,
      String triggerType,
      String triggerId,
      String scheduleId,
      Instant plannedFireTime) {

    public RuntimeMetadataRecord {
      nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    }

    /** 兼容现有 Runtime 调用；Trigger 会在统一 Launch 边界创建实例后补充。 */
    public RuntimeMetadataRecord(
        String name,
        int edgeCount,
        long workflowTimeoutSeconds,
        String failureStrategy,
        String workflowVersionId,
        Integer workflowVersionNo,
        boolean testRun,
        Map<String, NodeMetadataRecord> nodes) {
      this(
          name,
          edgeCount,
          workflowTimeoutSeconds,
          failureStrategy,
          workflowVersionId,
          workflowVersionNo,
          testRun,
          nodes,
          null,
          null,
          null,
          null);
    }
  }

  record NodeMetadataRecord(
      TaskVersionSnapshot task,
      String triggerRule,
      String failurePolicy,
      int maxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping) {

    public NodeMetadataRecord {
      inputMapping = inputMapping == null ? Map.of() : Map.copyOf(inputMapping);
    }
  }
}
