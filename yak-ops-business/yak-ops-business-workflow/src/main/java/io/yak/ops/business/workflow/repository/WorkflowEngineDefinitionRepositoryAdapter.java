package io.yak.ops.business.workflow.repository;

import io.yak.framework.workflow.engine.definition.EdgeDefinition;
import io.yak.framework.workflow.engine.definition.NodeDefinition;
import io.yak.framework.workflow.engine.definition.NodeFailurePolicy;
import io.yak.framework.workflow.engine.definition.NodeInputMapping;
import io.yak.framework.workflow.engine.definition.NodeTimeoutPolicy;
import io.yak.framework.workflow.engine.definition.RetryPolicy;
import io.yak.framework.workflow.engine.definition.TriggerRule;
import io.yak.framework.workflow.engine.definition.WorkflowDefinition;
import io.yak.framework.workflow.engine.definition.WorkflowFailureStrategy;
import io.yak.framework.workflow.engine.definition.WorkflowTimeoutPolicy;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.ops.business.workflow.dao.WorkflowCatalogDao;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for immutable Yak Framework workflow definitions. */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowEngineDefinitionRepositoryAdapter implements WorkflowDefinitionRepository {
  private final WorkflowCatalogDao catalogDao;
  private final WorkflowJsonCodec json;

  @Override
  public void save(WorkflowDefinition definition) {
    String value = json.write(EngineDefinitionSnapshot.from(definition));
    WorkflowVersionPO stored = catalogDao.selectVersionById(definition.id());
    if (stored == null) {
      WorkflowVersionPO runtime = new WorkflowVersionPO();
      runtime.setId(definition.id());
      runtime.setVersionKind("RUNTIME");
      runtime.setEngineDefinitionJson(value);
      runtime.setCreateTime(Instant.now());
      catalogDao.insertVersion(runtime);
      return;
    }

    if (stored.getEngineDefinitionJson() == null || stored.getEngineDefinitionJson().isBlank()) {
      catalogDao.initializeEngineDefinition(definition.id(), value);
      stored = catalogDao.selectVersionById(definition.id());
    }
    if (stored == null || stored.getEngineDefinitionJson() == null) {
      throw new IllegalStateException("工作流 Engine Definition 保存失败：" + definition.id());
    }
    if (!json.sameJson(value, stored.getEngineDefinitionJson())) {
      throw new IllegalStateException(
          "工作流版本的 Engine Definition 已固定，禁止覆盖：" + definition.id());
    }
  }

  @Override
  public Optional<WorkflowDefinition> findById(String definitionId) {
    WorkflowVersionPO stored = catalogDao.selectVersionById(definitionId);
    if (stored == null
        || stored.getEngineDefinitionJson() == null
        || stored.getEngineDefinitionJson().isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        json.read(stored.getEngineDefinitionJson(), EngineDefinitionSnapshot.class).toDefinition());
  }

  private record EngineDefinitionSnapshot(
      String id,
      String name,
      String failureStrategy,
      long workflowTimeoutMillis,
      List<NodeSnapshot> nodes,
      List<EdgeSnapshot> edges) {

    static EngineDefinitionSnapshot from(WorkflowDefinition definition) {
      return new EngineDefinitionSnapshot(
          definition.id(),
          definition.name(),
          definition.failureStrategy().name(),
          definition.timeoutPolicy().timeout().toMillis(),
          definition.nodes().values().stream().map(NodeSnapshot::from).toList(),
          definition.edges().stream().map(EdgeSnapshot::from).toList());
    }

    WorkflowDefinition toDefinition() {
      return new WorkflowDefinition(
          id,
          name,
          WorkflowFailureStrategy.valueOf(failureStrategy),
          workflowTimeoutMillis > 0L
              ? WorkflowTimeoutPolicy.of(Duration.ofMillis(workflowTimeoutMillis))
              : WorkflowTimeoutPolicy.none(),
          nodes.stream().map(NodeSnapshot::toDefinition).toList(),
          edges.stream().map(EdgeSnapshot::toDefinition).toList());
    }
  }

  private record NodeSnapshot(
      String id,
      String name,
      String triggerRule,
      int maxAttempts,
      long retryDelayMillis,
      String failurePolicy,
      long dispatchTimeoutMillis,
      long executionTimeoutMillis,
      Map<String, String> inputMapping,
      Map<String, Object> configuration) {

    static NodeSnapshot from(NodeDefinition node) {
      return new NodeSnapshot(
          node.id(),
          node.name(),
          node.triggerRule().name(),
          node.retryPolicy().maxAttempts(),
          node.retryPolicy().delay().toMillis(),
          node.failurePolicy().name(),
          node.timeoutPolicy().dispatchTimeout().toMillis(),
          node.timeoutPolicy().executionTimeout().toMillis(),
          node.inputMapping().bindings(),
          node.configuration());
    }

    NodeDefinition toDefinition() {
      return new NodeDefinition(
          id,
          name,
          TriggerRule.valueOf(triggerRule),
          maxAttempts > 1
              ? RetryPolicy.fixed(maxAttempts, Duration.ofMillis(retryDelayMillis))
              : RetryPolicy.none(),
          NodeFailurePolicy.valueOf(failurePolicy),
          NodeTimeoutPolicy.of(
              Duration.ofMillis(dispatchTimeoutMillis),
              Duration.ofMillis(executionTimeoutMillis)),
          NodeInputMapping.of(inputMapping),
          configuration);
    }
  }

  private record EdgeSnapshot(String fromNodeId, String toNodeId) {
    static EdgeSnapshot from(EdgeDefinition edge) {
      return new EdgeSnapshot(edge.fromNodeId(), edge.toNodeId());
    }

    EdgeDefinition toDefinition() {
      return new EdgeDefinition(fromNodeId, toNodeId);
    }
  }
}
