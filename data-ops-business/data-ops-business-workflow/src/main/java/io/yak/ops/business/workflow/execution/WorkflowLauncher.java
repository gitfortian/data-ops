package io.yak.ops.business.workflow.execution;

import io.yak.ops.common.enums.PublishState;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import io.yak.ops.business.workflow.domain.WorkflowRunInputScope;
import io.yak.ops.business.workflow.domain.WorkflowScheduleLaunchBindingScope;
import io.yak.ops.business.workflow.domain.WorkflowTriggerContext;
import io.yak.ops.business.workflow.repository.WorkflowExecutionTriggerRecorder;
import io.yak.ops.common.bean.dto.workflow.WorkflowRunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowDefinitionVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 工作流统一启动入口：Trigger -> Launch -> Definition/Runtime。 */
@Service
public class WorkflowLauncher {
  private static final Logger log = LoggerFactory.getLogger(WorkflowLauncher.class);

  private final WorkflowLaunchDefinitionGateway definitionGateway;
  private final WorkflowRuntime runtimeService;
  private final WorkflowExecutionTriggerRecorder triggerRecorder;
  private final WorkflowPublishedVersionRunner publishedVersionRunner;
  private final WorkflowExecutionAuditBridge auditBridge;

  @Autowired
  public WorkflowLauncher(
      WorkflowLaunchDefinitionGateway definitionGateway,
      WorkflowRuntime runtimeService,
      WorkflowExecutionTriggerRecorder triggerRecorder,
      WorkflowPublishedVersionRunner publishedVersionRunner,
      WorkflowExecutionAuditBridge auditBridge) {
    this.definitionGateway = definitionGateway;
    this.runtimeService = runtimeService;
    this.triggerRecorder = triggerRecorder;
    this.publishedVersionRunner = publishedVersionRunner;
    this.auditBridge = auditBridge;
  }

  /** Focused tests retain the previous constructor without Audit wiring. */
  public WorkflowLauncher(
      WorkflowLaunchDefinitionGateway definitionGateway,
      WorkflowRuntime runtimeService,
      WorkflowExecutionTriggerRecorder triggerRecorder,
      WorkflowPublishedVersionRunner publishedVersionRunner) {
    this(definitionGateway, runtimeService, triggerRecorder, publishedVersionRunner, null);
  }

  /** Focused tests retain the lightweight constructor without pinned-version wiring. */
  public WorkflowLauncher(
      WorkflowLaunchDefinitionGateway definitionGateway,
      WorkflowRuntime runtimeService,
      WorkflowExecutionTriggerRecorder triggerRecorder) {
    this(definitionGateway, runtimeService, triggerRecorder, null, null);
  }

  /** 正式执行当前启用的已发布版本；手工/API 启动仍保持单实例安全默认。 */
  public WorkflowDefinitionVO runPublished(
      String workflowId,
      WorkflowTriggerContext triggerContext) {
    String id = required(workflowId, "工作流 ID 不能为空");
    return launch(
        "PUBLISHED",
        id,
        triggerContext,
        () -> definitionGateway.runPublishedDefinition(id),
        WorkflowDefinitionVO::latestExecutionId,
        WorkflowDefinitionVO::name);
  }

  public WorkflowDefinitionVO runScheduledPublished(
      String workflowId,
      WorkflowTriggerContext triggerContext) {
    return runScheduledPublished(workflowId, triggerContext, Map.of());
  }

  /** 正常调度始终 FOLLOW_ACTIVE；运行参数只覆盖本次 engine.start，不修改发布版本。 */
  public WorkflowDefinitionVO runScheduledPublished(
      String workflowId,
      WorkflowTriggerContext triggerContext,
      Map<String, Object> runtimeInput) {
    String id = required(workflowId, "工作流 ID 不能为空");
    try (var binding = WorkflowScheduleLaunchBindingScope.open(triggerContext.triggerId());
         var input = WorkflowRunInputScope.open(runtimeInput)) {
      return launch(
          "SCHEDULED_PUBLISHED",
          id,
          triggerContext,
          () -> definitionGateway.runConcurrentPublishedDefinition(id),
          WorkflowDefinitionVO::latestExecutionId,
          WorkflowDefinitionVO::name);
    }
  }

  /**
   * Backfill 执行创建批次时固定的不可变发布版本，而不是跟随之后变更的 activeVersion。
   * 工作流下线仍阻止新的普通补数实例启动。
   */
  public WorkflowInstanceVO runBackfillPublished(
      String workflowId,
      String workflowVersionId,
      WorkflowTriggerContext triggerContext,
      Map<String, Object> runtimeInput) {
    String id = required(workflowId, "工作流 ID 不能为空");
    String versionId = required(workflowVersionId, "Backfill workflowVersionId 不能为空");
    WorkflowDefinitionVO current = definitionGateway.currentDefinition(id);
    if (!PublishState.PUBLISHED.matches(current.status())) {
      throw new IllegalStateException("工作流已下线，不能启动新的 Backfill 实例");
    }
    return runPinnedVersion("BACKFILL_PUBLISHED", id, versionId, triggerContext, runtimeInput);
  }

  /**
   * 运维补跑允许执行来源实例固定的不可变版本。
   * 与正常 Cron/Backfill 不同，这里不跟随当前 activeVersion，也不以当前 ONLINE 状态作为历史恢复门槛。
   */
  public WorkflowInstanceVO runOperationalPublished(
      String workflowId,
      String workflowVersionId,
      WorkflowTriggerContext triggerContext,
      Map<String, Object> runtimeInput) {
    String id = required(workflowId, "工作流 ID 不能为空");
    String versionId = required(workflowVersionId, "运维补跑 workflowVersionId 不能为空");
    return runPinnedVersion("OPERATIONAL_RERUN", id, versionId, triggerContext, runtimeInput);
  }

  private WorkflowInstanceVO runPinnedVersion(
      String mode,
      String workflowId,
      String workflowVersionId,
      WorkflowTriggerContext triggerContext,
      Map<String, Object> runtimeInput) {
    if (publishedVersionRunner == null) {
      throw new IllegalStateException("WorkflowPublishedVersionRunner 不可用");
    }
    try (var binding = WorkflowScheduleLaunchBindingScope.open(triggerContext.triggerId());
         var input = WorkflowRunInputScope.open(runtimeInput)) {
      return launch(
          mode,
          workflowId + "@" + workflowVersionId,
          triggerContext,
          () -> publishedVersionRunner.run(workflowId, workflowVersionId),
          WorkflowInstanceVO::id,
          WorkflowInstanceVO::name);
    }
  }

  /** 测试执行当前草稿，不改变已发布版本。 */
  public WorkflowDefinitionVO testRunDraft(
      String workflowId,
      WorkflowTriggerContext triggerContext) {
    String id = required(workflowId, "工作流 ID 不能为空");
    return launch(
        "DRAFT_TEST",
        id,
        triggerContext,
        () -> definitionGateway.testRunDraft(id),
        WorkflowDefinitionVO::latestExecutionId,
        WorkflowDefinitionVO::name);
  }

  /** 兼容直接提交 DAG 的底层运行接口，同时纳入统一 Trigger 入口并真正激活实例。 */
  public WorkflowInstanceVO runAdHoc(
      WorkflowRunDTO request,
      WorkflowTriggerContext triggerContext) {
    Objects.requireNonNull(request, "workflow run request");
    return launchPrepared(
        "AD_HOC",
        request.name(),
        triggerContext,
        () -> runtimeService.run(request));
  }

  /** 整实例重新运行会创建并激活新的 WorkflowExecution，因此也经过 Launch。 */
  public WorkflowInstanceVO restart(
      String executionId,
      WorkflowTriggerContext triggerContext) {
    String id = required(executionId, "工作流实例 ID 不能为空");
    return launchPrepared(
        "RESTART",
        id,
        triggerContext,
        () -> runtimeService.restart(id));
  }

  /** 从指定节点重跑会创建并激活新的 WorkflowExecution，因此也经过 Launch。 */
  public WorkflowInstanceVO rerunFromNode(
      String executionId,
      String nodeId,
      WorkflowTriggerContext triggerContext) {
    String safeExecutionId = required(executionId, "工作流实例 ID 不能为空");
    String safeNodeId = required(nodeId, "工作流节点 ID 不能为空");
    return launchPrepared(
        "RERUN_FROM_NODE",
        safeExecutionId + "/" + safeNodeId,
        triggerContext,
        () -> runtimeService.rerunFromNode(safeExecutionId, safeNodeId));
  }

  /**
   * Runtime 的 engine.start 会先生成 RUNNING 快照，但 Yak Ops 仍需 activate 才会 drain NodeDispatch。
   * 先记录 Trigger，再激活，保证任何对外返回的 RUNNING 实例都已经进入真实执行生命周期。
   */
  private WorkflowInstanceVO launchPrepared(
      String mode,
      String target,
      WorkflowTriggerContext triggerContext,
      Supplier<WorkflowInstanceVO> prepare) {
    return launch(
        mode,
        target,
        triggerContext,
        prepare,
        WorkflowInstanceVO::id,
        WorkflowInstanceVO::name,
        prepared -> runtimeService.activate(prepared.id()));
  }

  private <T> T launch(
      String mode,
      String target,
      WorkflowTriggerContext triggerContext,
      Supplier<T> action,
      Function<T, String> executionIdExtractor,
      Function<T, String> resourceNameExtractor) {
    return launch(
        mode,
        target,
        triggerContext,
        action,
        executionIdExtractor,
        resourceNameExtractor,
        Function.identity());
  }

  private <T> T launch(
      String mode,
      String target,
      WorkflowTriggerContext triggerContext,
      Supplier<T> action,
      Function<T, String> executionIdExtractor,
      Function<T, String> resourceNameExtractor,
      Function<T, T> afterRecord) {
    Objects.requireNonNull(triggerContext, "workflow trigger context");
    WorkflowExecutionAuditBridge.LaunchAudit audit =
        auditBridge == null ? null : auditBridge.beginLaunch(mode, target, triggerContext);
    log.info(
        "[workflow-launch] start mode={}, target={}, triggerType={}, triggerId={}, scheduleId={}, backfillId={}, plannedFireTime={}",
        mode,
        target,
        triggerContext.triggerType(),
        triggerContext.triggerId(),
        triggerContext.scheduleId(),
        triggerContext.backfillId(),
        triggerContext.plannedFireTime());
    try {
      T prepared =
          auditBridge == null ? action.get() : auditBridge.call(audit, action);
      String executionId = prepared == null ? null : executionIdExtractor.apply(prepared);
      String resourceName = prepared == null ? null : resourceNameExtractor.apply(prepared);
      if (auditBridge != null) auditBridge.attachLaunch(audit, executionId, resourceName);
      triggerRecorder.record(executionId, triggerContext);
      log.info(
          "[workflow-launch] created mode={}, target={}, triggerType={}, triggerId={}, execution={}",
          mode,
          target,
          triggerContext.triggerType(),
          triggerContext.triggerId(),
          executionId);
      return prepared == null ? null : afterRecord.apply(prepared);
    } catch (RuntimeException exception) {
      if (auditBridge != null) auditBridge.failLaunch(audit, exception);
      log.warn(
          "[workflow-launch] failed mode={}, target={}, triggerType={}, triggerId={}, message={}",
          mode,
          target,
          triggerContext.triggerType(),
          triggerContext.triggerId(),
          exception.getMessage());
      throw exception;
    }
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
