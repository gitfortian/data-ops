package io.yak.ops.business.workflow.repository;

import io.yak.ops.business.workflow.dao.WorkflowCatalogDao;
import io.yak.ops.business.workflow.domain.WorkflowEdgeSpec;
import io.yak.ops.business.workflow.domain.WorkflowNodeSpec;
import io.yak.ops.business.workflow.domain.WorkflowRunSpec;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowDefinitionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** MyBatis-Plus adapter for editable workflow definitions and immutable published versions. */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowCatalogRepositoryAdapter implements WorkflowDefinitionRepository {
  private final WorkflowCatalogDao catalogDao;
  private final WorkflowJsonCodec json;

  @Override
  public List<DefinitionRecord> loadDefinitions() {
    return catalogDao.selectDefinitions().stream().map(this::toRecord).toList();
  }

  @Override
  public List<VersionRecord> loadVersions(String workflowId) {
    return catalogDao.selectPublishedVersions(workflowId).stream().map(this::toRecord).toList();
  }

  @Override
  public void saveDefinition(DefinitionRecord definition) {
    catalogDao.upsertDefinition(toPO(definition));
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void publish(DefinitionRecord definition, VersionRecord version) {
    // 与调度 Trigger Ledger 同用定义行锁：发布/回滚跨实例串行，内存聚合只是缓存层。
    catalogDao.lockDefinition(definition.id());
    catalogDao.insertVersion(toPO(version));
    catalogDao.upsertDefinition(toPO(definition));
  }

  @Override
  public void deleteDefinition(String workflowId) {
    // Published versions are intentionally retained because historical executions still reference them.
    catalogDao.deleteDefinition(workflowId);
  }

  private WorkflowDefinitionPO toPO(DefinitionRecord value) {
    WorkflowDefinitionPO po = new WorkflowDefinitionPO();
    po.setId(value.id());
    po.setName(value.name());
    po.setDescription(value.description());
    po.setStatus(value.status());
    po.setDraftRevision(value.draftRevision());
    po.setLatestVersionNo(value.latestVersionNo());
    po.setActiveVersionId(value.activeVersionId());
    po.setDraftJson(json.write(new DraftPayload(
        value.failureStrategy(),
        value.nodes(),
        value.edges(),
        value.input(),
        value.editorMeta(),
        value.workflowTimeoutSeconds())));
    po.setLatestExecutionId(value.latestExecutionId());
    po.setLatestExecutionStatus(value.latestExecutionStatus());
    po.setCreateTime(value.createTime());
    po.setUpdateTime(value.updateTime());
    return po;
  }

  private WorkflowVersionPO toPO(VersionRecord value) {
    WorkflowVersionPO po = new WorkflowVersionPO();
    po.setId(value.id());
    po.setWorkflowId(value.workflowId());
    po.setVersionNo(value.versionNo());
    po.setDraftRevision(value.draftRevision());
    po.setRunRequestJson(json.write(RunPayload.from(value.runSpec())));
    po.setEditorMetaJson(json.write(value.editorMeta()));
    po.setTaskVersionsJson(json.write(value.taskVersionsByNode()));
    po.setCreateTime(value.publishedAt());
    return po;
  }

  private DefinitionRecord toRecord(WorkflowDefinitionPO po) {
    DraftPayload draft = json.read(po.getDraftJson(), DraftPayload.class);
    return new DefinitionRecord(
        po.getId(),
        po.getName(),
        po.getDescription(),
        po.getStatus(),
        draft.failureStrategy(),
        draft.nodes(),
        draft.edges(),
        draft.input(),
        draft.editorMeta(),
        draft.workflowTimeoutSeconds(),
        po.getDraftRevision(),
        po.getLatestVersionNo(),
        po.getActiveVersionId(),
        po.getLatestExecutionId(),
        po.getLatestExecutionStatus(),
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private VersionRecord toRecord(WorkflowVersionPO po) {
    RunPayload run = json.read(po.getRunRequestJson(), RunPayload.class);
    return new VersionRecord(
        po.getId(),
        po.getWorkflowId(),
        po.getVersionNo(),
        po.getDraftRevision(),
        run.toSpec(),
        json.readMap(po.getEditorMetaJson()),
        json.readTaskVersions(po.getTaskVersionsJson()),
        po.getCreateTime());
  }

  private record DraftPayload(
      String failureStrategy,
      List<WorkflowNodeSpec> nodes,
      List<WorkflowEdgeSpec> edges,
      Map<String, Object> input,
      Map<String, Object> editorMeta,
      long workflowTimeoutSeconds) {
    private DraftPayload {
      nodes = nodes == null ? List.of() : List.copyOf(nodes);
      edges = edges == null ? List.of() : List.copyOf(edges);
      input = input == null ? Map.of() : Map.copyOf(input);
      editorMeta = editorMeta == null ? Map.of() : Map.copyOf(editorMeta);
      failureStrategy = failureStrategy == null || failureStrategy.isBlank()
          ? "CONTINUE_INDEPENDENT_BRANCHES"
          : failureStrategy;
    }
  }

  /** Stable runtime-version JSON projection; canvas coordinates stay in draft/editor metadata. */
  private record RunPayload(
      String name,
      List<RunNodePayload> nodes,
      List<WorkflowEdgeSpec> edges,
      Map<String, Object> input,
      long workflowTimeoutSeconds,
      String failureStrategy) {

    static RunPayload from(WorkflowRunSpec spec) {
      return new RunPayload(
          spec.name(),
          spec.nodes().stream().map(RunNodePayload::from).toList(),
          spec.edges(),
          spec.input(),
          spec.workflowTimeoutSeconds(),
          spec.failureStrategy());
    }

    WorkflowRunSpec toSpec() {
      return new WorkflowRunSpec(
          name,
          nodes == null ? List.of() : nodes.stream().map(RunNodePayload::toSpec).toList(),
          edges == null ? List.of() : edges,
          input == null ? Map.of() : input,
          workflowTimeoutSeconds,
          failureStrategy);
    }
  }

  private record RunNodePayload(
      String id,
      String taskId,
      int maxAttempts,
      long retryDelaySeconds,
      long dispatchTimeoutSeconds,
      long executionTimeoutSeconds,
      Map<String, String> inputMapping,
      String triggerRule,
      String failurePolicy) {

    static RunNodePayload from(WorkflowNodeSpec node) {
      return new RunNodePayload(
          node.id(),
          node.taskId(),
          node.maxAttempts(),
          node.retryDelaySeconds(),
          node.dispatchTimeoutSeconds(),
          node.executionTimeoutSeconds(),
          node.inputMapping(),
          node.triggerRule(),
          node.failurePolicy());
    }

    WorkflowNodeSpec toSpec() {
      return new WorkflowNodeSpec(
          id,
          taskId,
          0D,
          0D,
          maxAttempts,
          retryDelaySeconds,
          dispatchTimeoutSeconds,
          executionTimeoutSeconds,
          inputMapping,
          triggerRule,
          failurePolicy);
    }
  }
}
