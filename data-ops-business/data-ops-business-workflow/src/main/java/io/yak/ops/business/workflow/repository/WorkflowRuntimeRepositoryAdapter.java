package io.yak.ops.business.workflow.repository;

import io.yak.ops.business.workflow.dao.WorkflowCatalogDao;
import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.repository.support.WorkflowJsonCodec;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeAttemptPO;
import io.yak.ops.common.bean.po.workflow.WorkflowVersionPO;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/** MyBatis-Plus adapter for durable runtime metadata and restart indexes. */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowRuntimeRepositoryAdapter implements WorkflowRuntimeRepository {
  private final WorkflowCatalogDao catalogDao;
  private final WorkflowExecutionDao executionDao;
  private final WorkflowJsonCodec json;

  @Override
  public void prepareMetadata(String definitionId, RuntimeMetadataRecord metadata) {
    String value = json.write(metadata);
    catalogDao.initializeRuntimeMetadata(definitionId, value);
    WorkflowVersionPO stored = catalogDao.selectVersionById(definitionId);
    if (stored == null) {
      throw new IllegalArgumentException("工作流运行定义不存在：" + definitionId);
    }
    if (stored.getRuntimeMetadataJson() == null || stored.getRuntimeMetadataJson().isBlank()) {
      throw new IllegalStateException("工作流运行元数据保存失败：" + definitionId);
    }
    if (!json.sameJson(value, stored.getRuntimeMetadataJson())) {
      throw new IllegalStateException(
          "工作流版本的 Runtime Metadata 已固定，禁止覆盖：" + definitionId);
    }
  }

  @Override
  public void saveMetadata(String executionId, RuntimeMetadataRecord metadata) {
    WorkflowExecutionPO po = new WorkflowExecutionPO();
    po.setId(executionId);
    po.setWorkflowName(metadata.name());
    po.setWorkflowVersionId(metadata.workflowVersionId());
    po.setWorkflowVersionNo(metadata.workflowVersionNo());
    po.setTestRun(metadata.testRun());
    po.setEdgeCount(metadata.edgeCount());
    po.setWorkflowTimeoutSeconds(metadata.workflowTimeoutSeconds());
    po.setFailureStrategy(metadata.failureStrategy());
    po.setRuntimeMetadataJson(json.write(metadata));
    if (executionDao.updateExecution(po) == 0) {
      throw new IllegalArgumentException("工作流执行实例不存在：" + executionId);
    }
  }

  @Override
  public Optional<RuntimeMetadataRecord> findMetadata(String executionId) {
    String value = executionDao.selectEffectiveRuntimeMetadata(executionId);
    return value == null || value.isBlank()
        ? Optional.empty()
        : Optional.of(json.read(value, RuntimeMetadataRecord.class));
  }

  @Override
  public List<String> listExecutionIds() {
    return executionDao.selectExecutionIds();
  }

  @Override
  public List<String> findRecoverableExecutionIds() {
    return executionDao.selectRecoverableExecutionIds();
  }

  @Override
  public List<ProjectExecutionRef> findRecoverableExecutionsForDispatch() {
    return executionDao.selectRecoverableExecutionsForDispatch().stream()
        .map(value -> new ProjectExecutionRef(value.projectId(), value.executionId()))
        .toList();
  }

  @Override
  public void bindExternalExecution(String attemptId, String externalExecutionId) {
    if (attemptId == null || attemptId.isBlank()
        || externalExecutionId == null || externalExecutionId.isBlank()) {
      throw new IllegalArgumentException("attemptId 和 externalExecutionId 不能为空");
    }
    int updated = executionDao.bindExternalExecution(attemptId, externalExecutionId);
    if (updated > 0) return;

    WorkflowNodeAttemptPO existing = executionDao.selectAttempt(attemptId);
    if (existing == null) {
      throw new IllegalArgumentException("工作流 Attempt 不存在：" + attemptId);
    }
    if (!externalExecutionId.equals(existing.getExternalExecutionId())) {
      throw new IllegalStateException(
          "工作流 Attempt 已绑定其他远端执行："
              + attemptId + " -> " + existing.getExternalExecutionId());
    }
  }

  @Override
  public Optional<String> findExternalExecution(String attemptId) {
    WorkflowNodeAttemptPO attempt = executionDao.selectAttempt(attemptId);
    return attempt == null ? Optional.empty() : Optional.ofNullable(attempt.getExternalExecutionId());
  }
}
