package io.yak.ops.business.workflow.definition;

import io.yak.ops.common.enums.PublishState;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionCreateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO.EdgeDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowDefinitionUpdateDTO.NodeDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO.EdgeVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO.NodeVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Adds business audit semantics around Workflow definition lifecycle commands. */
@Component
public class WorkflowDefinitionAuditCoordinator {

  private static final String RESOURCE_TYPE = "WORKFLOW";
  private static final String EXECUTION_RESOURCE_TYPE = "WORKFLOW_EXECUTION";
  private static final String SOURCE = "WEB";
  private static final BusinessAuditService NOOP_AUDIT =
      request -> AuditOperationHandle.noop(null);

  private final WorkflowDefinitionManager definitions;
  private final BusinessAuditService auditService;

  @Autowired
  public WorkflowDefinitionAuditCoordinator(
      WorkflowDefinitionManager definitions,
      ObjectProvider<BusinessAuditService> auditServiceProvider) {
    this(definitions, auditServiceProvider.getIfAvailable(() -> NOOP_AUDIT));
  }

  WorkflowDefinitionAuditCoordinator(
      WorkflowDefinitionManager definitions, BusinessAuditService auditService) {
    this.definitions = definitions;
    this.auditService = auditService == null ? NOOP_AUDIT : auditService;
  }

  public WorkflowDefinitionVO create(WorkflowDefinitionCreateDTO request) {
    AuditOperationHandle audit =
        start(
            "WORKFLOW_CREATE",
            "Create workflow",
            null,
            request == null ? null : trimToNull(request.name()),
            Map.of("initialStatus", "DRAFT"));
    try {
      WorkflowDefinitionVO created = definitions.create(request);
      audit.resource(created.id(), created.name());
      audit.event(
          AuditEventType.RESOURCE_CREATED,
          "Workflow draft created",
          Map.of(
              "status", created.status(),
              "nodeCount", created.nodeCount(),
              "edgeCount", created.edgeCount()));
      audit.success("Workflow created");
      return created;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_CREATE_FAILED", exception);
      throw exception;
    }
  }

  public WorkflowDefinitionVO update(String id, WorkflowDefinitionUpdateDTO request) {
    WorkflowDefinitionVO before = definitions.get(id);
    UpdateChanges changes = updateChanges(before, request);
    if (!changes.changed()) {
      return definitions.update(id, request);
    }

    AuditOperationHandle audit =
        start(
            "WORKFLOW_UPDATE",
            "Update workflow draft",
            before.id(),
            before.name(),
            operationMetadata(before));
    try {
      WorkflowDefinitionVO updated = definitions.update(id, request);
      audit.resource(updated.id(), updated.name());
      audit.event(
          AuditEventType.RESOURCE_UPDATED,
          "Workflow draft updated",
          changes.payload());
      audit.success("Workflow updated");
      return updated;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  public WorkflowDefinitionVO upgradeTaskRevision(String id, String nodeId) {
    WorkflowDefinitionVO before = definitions.get(id);
    NodeVO current = findNode(before.nodes(), nodeId);
    if (current != null
        && current.taskAssetId() != null
        && !current.taskRevisionUpdateAvailable()) {
      return definitions.upgradeTaskRevision(id, nodeId);
    }

    Map<String, Object> metadata = new LinkedHashMap<>(operationMetadata(before));
    putIfNotNull(metadata, "nodeId", nodeId);
    AuditOperationHandle audit =
        start(
            "WORKFLOW_TASK_REVISION_UPGRADE",
            "Upgrade workflow task revision",
            before.id(),
            before.name(),
            Map.copyOf(metadata));
    try {
      WorkflowDefinitionVO updated = definitions.upgradeTaskRevision(id, nodeId);
      NodeVO next = findNode(updated.nodes(), nodeId);
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("changeType", "TASK_REVISION_UPGRADE");
      putIfNotNull(payload, "nodeId", nodeId);
      if (current != null) {
        putIfNotNull(payload, "taskAssetId", current.taskAssetId());
        putIfNotNull(payload, "fromTaskRevisionNo", current.taskRevisionNo());
      }
      if (next != null) {
        putIfNotNull(payload, "toTaskRevisionNo", next.taskRevisionNo());
      }
      audit.event(
          AuditEventType.RESOURCE_UPDATED,
          "Workflow task revision upgraded",
          Map.copyOf(payload));
      audit.success("Workflow task revision upgraded");
      return updated;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_TASK_REVISION_UPGRADE_FAILED", exception);
      throw exception;
    }
  }

  public WorkflowDefinitionVO online(String id) {
    WorkflowDefinitionVO before = definitions.get(id);
    boolean publish = before.activeVersionId() == null || before.draftChanged();
    boolean enable = !PublishState.PUBLISHED.matches(before.status());
    if (!publish && !enable) {
      return definitions.online(id);
    }

    String operationType = publish ? "WORKFLOW_PUBLISH" : "WORKFLOW_ENABLE";
    String operationName = publish ? "Publish workflow" : "Enable workflow";
    AuditOperationHandle audit =
        start(
            operationType,
            operationName,
            before.id(),
            before.name(),
            operationMetadata(before));
    try {
      WorkflowDefinitionVO updated = definitions.online(id);
      audit.resource(updated.id(), updated.name());
      if (publish) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("changeType", "VERSION_PUBLISHED");
        putIfNotNull(payload, "workflowVersionId", updated.activeVersionId());
        putIfNotNull(payload, "workflowVersionNo", updated.activeVersionNo());
        payload.put("nodeCount", updated.nodeCount());
        payload.put("edgeCount", updated.edgeCount());
        audit.event(
            AuditEventType.RESOURCE_UPDATED,
            "Workflow version published",
            Map.copyOf(payload));
      }
      if (enable) {
        audit.event(
            AuditEventType.RESOURCE_UPDATED,
            "Workflow enabled",
            lifecyclePayload("RESOURCE_ENABLED", updated));
      }
      audit.success(publish ? "Workflow published" : "Workflow enabled");
      return updated;
    } catch (RuntimeException exception) {
      audit.failure(publish ? "WORKFLOW_PUBLISH_FAILED" : "WORKFLOW_ENABLE_FAILED", exception);
      throw exception;
    }
  }

  public WorkflowDefinitionVO offline(String id) {
    WorkflowDefinitionVO before = definitions.get(id);
    if (PublishState.OFFLINE.matches(before.status())) {
      return definitions.offline(id);
    }

    AuditOperationHandle audit =
        start(
            "WORKFLOW_DISABLE",
            "Disable workflow",
            before.id(),
            before.name(),
            operationMetadata(before));
    try {
      WorkflowDefinitionVO updated = definitions.offline(id);
      audit.resource(updated.id(), updated.name());
      audit.event(
          AuditEventType.RESOURCE_UPDATED,
          "Workflow disabled",
          lifecyclePayload("RESOURCE_DISABLED", updated));
      audit.success("Workflow disabled");
      return updated;
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_DISABLE_FAILED", exception);
      throw exception;
    }
  }

  /** The editor-facing pause route controls the latest execution but keeps its original carrier. */
  public WorkflowDefinitionVO pauseExecution(String workflowId) {
    return controlExecution(
        workflowId,
        "WORKFLOW_EXECUTION_PAUSE",
        "Pause workflow execution",
        "EXECUTION_PAUSED",
        "Workflow execution paused",
        () -> definitions.pause(workflowId));
  }

  /** The editor-facing resume route controls the latest execution but keeps its original carrier. */
  public WorkflowDefinitionVO resumeExecution(String workflowId) {
    return controlExecution(
        workflowId,
        "WORKFLOW_EXECUTION_RESUME",
        "Resume workflow execution",
        "EXECUTION_RESUMED",
        "Workflow execution resumed",
        () -> definitions.resume(workflowId));
  }

  public void delete(String id) {
    WorkflowDefinitionVO before = definitions.get(id);
    AuditOperationHandle audit =
        start(
            "WORKFLOW_DELETE",
            "Delete workflow",
            before.id(),
            before.name(),
            operationMetadata(before));
    try {
      definitions.delete(id);
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("status", before.status());
      putIfNotNull(payload, "activeVersionId", before.activeVersionId());
      putIfNotNull(payload, "activeVersionNo", before.activeVersionNo());
      audit.event(
          AuditEventType.RESOURCE_DELETED,
          "Workflow deleted",
          Map.copyOf(payload));
      audit.success("Workflow deleted");
    } catch (RuntimeException exception) {
      audit.failure("WORKFLOW_DELETE_FAILED", exception);
      throw exception;
    }
  }

  private WorkflowDefinitionVO controlExecution(
      String workflowId,
      String operationType,
      String operationName,
      String changeType,
      String message,
      Supplier<WorkflowDefinitionVO> action) {
    WorkflowDefinitionVO before = definitions.get(workflowId);
    String executionId = trimToNull(before.latestExecutionId());
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("workflowId", before.id());
    putIfNotNull(metadata, "latestExecutionStatus", before.latestExecutionStatus());
    AuditOperationHandle audit =
        startExecution(
            operationType,
            operationName,
            executionId,
            before.name(),
            Map.copyOf(metadata));
    try {
      WorkflowDefinitionVO updated = action.get();
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("changeType", changeType);
      payload.put("workflowId", before.id());
      putIfNotNull(payload, "executionId", executionId);
      putIfNotNull(payload, "status", updated.latestExecutionStatus());
      audit.event(AuditEventType.RESOURCE_UPDATED, message, Map.copyOf(payload));
      audit.success(message);
      return updated;
    } catch (RuntimeException exception) {
      audit.failure(operationType + "_FAILED", exception);
      throw exception;
    }
  }

  private AuditOperationHandle start(
      String operationType,
      String operationName,
      String resourceId,
      String resourceName,
      Map<String, ?> metadata) {
    return auditService.start(
        new AuditOperationRequest(
            operationType,
            operationName,
            RESOURCE_TYPE,
            resourceId,
            resourceName,
            SOURCE,
            metadata));
  }

  private AuditOperationHandle startExecution(
      String operationType,
      String operationName,
      String executionId,
      String resourceName,
      Map<String, ?> metadata) {
    return auditService.start(
        new AuditOperationRequest(
            operationType,
            operationName,
            EXECUTION_RESOURCE_TYPE,
            executionId,
            resourceName,
            SOURCE,
            metadata));
  }

  private UpdateChanges updateChanges(
      WorkflowDefinitionVO before, WorkflowDefinitionUpdateDTO request) {
    if (request == null) return new UpdateChanges(true, Map.of());

    String nextName = request.name() == null ? null : request.name().trim();
    String nextDescription = trimToNull(request.description());
    boolean nameChanged = !Objects.equals(before.name(), nextName);
    boolean descriptionChanged = !Objects.equals(before.description(), nextDescription);
    boolean nodesChanged =
        !draftViewNodes(before.nodes()).equals(draftRequestNodes(request.nodes()));
    boolean edgesChanged =
        !draftViewEdges(before.edges()).equals(draftRequestEdges(request.edges()));
    boolean inputChanged = !Objects.equals(before.input(), request.input());
    boolean editorMetaChanged = !Objects.equals(before.editorMeta(), request.editorMeta());
    boolean timeoutChanged = before.workflowTimeoutSeconds() != request.workflowTimeoutSeconds();
    boolean failureStrategyChanged =
        !Objects.equals(before.failureStrategy(), request.failureStrategy());

    boolean changed =
        nameChanged
            || descriptionChanged
            || nodesChanged
            || edgesChanged
            || inputChanged
            || editorMetaChanged
            || timeoutChanged
            || failureStrategyChanged;
    if (!changed) return new UpdateChanges(false, Map.of());

    Map<String, Object> payload = new LinkedHashMap<>();
    addValueChange(payload, "name", before.name(), nextName);
    if (descriptionChanged) payload.put("descriptionChanged", true);
    if (nodesChanged || edgesChanged) {
      payload.put("graphChanged", true);
      payload.put(
          "nodeCount",
          Map.of("before", before.nodeCount(), "after", request.nodes().size()));
      payload.put(
          "edgeCount",
          Map.of("before", before.edgeCount(), "after", request.edges().size()));
    }
    if (inputChanged) payload.put("inputChanged", true);
    if (editorMetaChanged) payload.put("editorMetaChanged", true);
    addValueChange(
        payload,
        "workflowTimeoutSeconds",
        before.workflowTimeoutSeconds(),
        request.workflowTimeoutSeconds());
    addValueChange(
        payload,
        "failureStrategy",
        before.failureStrategy(),
        request.failureStrategy());
    return new UpdateChanges(true, Map.copyOf(payload));
  }

  private Map<String, Object> operationMetadata(WorkflowDefinitionVO workflow) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("previousStatus", workflow.status());
    putIfNotNull(metadata, "activeVersionId", workflow.activeVersionId());
    putIfNotNull(metadata, "activeVersionNo", workflow.activeVersionNo());
    return Map.copyOf(metadata);
  }

  private Map<String, Object> lifecyclePayload(
      String changeType, WorkflowDefinitionVO workflow) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("changeType", changeType);
    payload.put("status", workflow.status());
    putIfNotNull(payload, "workflowVersionId", workflow.activeVersionId());
    putIfNotNull(payload, "workflowVersionNo", workflow.activeVersionNo());
    return Map.copyOf(payload);
  }

  private List<DraftNode> draftViewNodes(List<NodeVO> nodes) {
    return nodes == null ? List.of() : nodes.stream().map(DraftNode::from).toList();
  }

  private List<DraftNode> draftRequestNodes(List<NodeDTO> nodes) {
    return nodes == null ? List.of() : nodes.stream().map(DraftNode::from).toList();
  }

  private List<DraftEdge> draftViewEdges(List<EdgeVO> edges) {
    return edges == null ? List.of() : edges.stream().map(DraftEdge::from).toList();
  }

  private List<DraftEdge> draftRequestEdges(List<EdgeDTO> edges) {
    return edges == null ? List.of() : edges.stream().map(DraftEdge::from).toList();
  }

  private NodeVO findNode(List<NodeVO> nodes, String nodeId) {
    if (nodes == null) return null;
    return nodes.stream()
        .filter(node -> Objects.equals(node.id(), nodeId))
        .findFirst()
        .orElse(null);
  }

  private static void addValueChange(
      Map<String, Object> payload, String field, Object before, Object after) {
    if (!Objects.equals(before, after)) {
      payload.put(field, Map.of("before", before, "after", after));
    }
  }

  private static void putIfNotNull(Map<String, Object> values, String key, Object value) {
    if (value != null) values.put(key, value);
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private record UpdateChanges(boolean changed, Map<String, Object> payload) {}

  private record DraftEdge(String source, String target) {
    static DraftEdge from(EdgeVO edge) {
      return new DraftEdge(edge.source(), edge.target());
    }

    static DraftEdge from(EdgeDTO edge) {
      return new DraftEdge(edge.source(), edge.target());
    }
  }

  private record DraftNode(
      String id,
      String taskId,
      Long taskAssetId,
      Long taskRevisionId,
      Integer taskRevisionNo,
      double positionX,
      double positionY,
      int maxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping,
      String triggerRule,
      String failurePolicy) {

    static DraftNode from(NodeVO node) {
      return new DraftNode(
          node.id(),
          node.taskId(),
          node.taskAssetId(),
          node.taskRevisionId(),
          node.taskRevisionNo(),
          node.positionX(),
          node.positionY(),
          node.maxAttempts(),
          node.retryDelaySeconds(),
          node.dispatchTimeoutSeconds(),
          node.executionTimeoutSeconds(),
          node.inputMapping(),
          node.triggerRule(),
          node.failurePolicy());
    }

    static DraftNode from(NodeDTO node) {
      return new DraftNode(
          node.id(),
          node.taskId(),
          node.taskAssetId(),
          node.taskRevisionId(),
          node.taskRevisionNo(),
          node.positionX(),
          node.positionY(),
          node.maxAttempts(),
          node.retryDelaySeconds(),
          node.dispatchTimeoutSeconds(),
          node.executionTimeoutSeconds(),
          node.inputMapping(),
          node.triggerRule(),
          node.failurePolicy());
    }
  }
}
