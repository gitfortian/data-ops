package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository;
import io.yak.ops.business.workflow.repository.WorkflowDefinitionRepository.VersionRecord;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 直接执行指定不可变发布版本。
 *
 * <p>正常 Cron 始终 FOLLOW_ACTIVE；Backfill 在创建批次时固定 workflowVersionId，
 * 因此后续发布新版本不会改变已经创建的补数批次语义。</p>
 */
@Component
public class WorkflowPublishedVersionRunner {
  private final WorkflowRuntime runtimeService;
  private final ObjectProvider<WorkflowDefinitionRepository> persistence;

  public WorkflowPublishedVersionRunner(
      WorkflowRuntime runtimeService,
      ObjectProvider<WorkflowDefinitionRepository> persistence) {
    this.runtimeService = runtimeService;
    this.persistence = persistence;
  }

  public WorkflowInstanceVO run(String workflowId, String workflowVersionId) {
    String id = required(workflowId, "工作流 ID 不能为空");
    String versionId = required(workflowVersionId, "工作流版本 ID 不能为空");
    WorkflowDefinitionRepository catalog = persistence.getIfAvailable();
    if (catalog == null) {
      throw new IllegalStateException("Backfill 固定版本执行需要 durable WorkflowDefinitionRepository");
    }
    VersionRecord version = catalog.loadVersions(id).stream()
        .filter(candidate -> versionId.equals(candidate.id()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(
            "Backfill 固定的工作流发布版本不存在：" + versionId));

    WorkflowInstanceVO prepared = runtimeService.run(
        version.runSpec(),
        version.taskVersionsByNode(),
        version.id(),
        version.versionNo(),
        false);
    return runtimeService.activate(prepared.id());
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
