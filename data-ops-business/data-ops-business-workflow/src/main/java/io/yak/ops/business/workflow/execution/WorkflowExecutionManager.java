package io.yak.ops.business.workflow.execution;

import io.yak.ops.business.workflow.runtime.WorkflowRuntime;

import io.yak.framework.workflow.engine.definition.EdgeDefinition;
import io.yak.framework.workflow.engine.spi.WorkflowDefinitionRepository;
import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository.RuntimeMetadataRecord;
import io.yak.ops.common.bean.dto.workflow.WorkflowBatchRetryDTO;
import io.yak.ops.common.bean.dto.workflow.WorkflowBusinessDateRerunDTO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBackfillVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBatchRetryVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowBatchRetryVO.ItemVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceOperationsVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceOperationsVO.EdgeVO;
import io.yak.ops.common.bean.vo.workflow.WorkflowInstanceVO;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 工作流实例运维编排：查询血缘、批量失败恢复和指定 businessDate 补跑。 */
@Service
public class WorkflowExecutionManager {
  private static final int MAX_BATCH_RETRY = 100;

  private final WorkflowRuntime runtime;
  private final WorkflowExecutionReactivator reactivation;
  private final ObjectProvider<WorkflowRuntimeRepository> runtimePersistence;
  private final ObjectProvider<WorkflowDefinitionRepository> definitionRepository;
  private final ObjectProvider<WorkflowScheduleTriggerDao> triggerDao;
  private final ObjectProvider<WorkflowBusinessDateRerunGateway> businessDateRerun;

  public WorkflowExecutionManager(
      WorkflowRuntime runtime,
      WorkflowExecutionReactivator reactivation,
      ObjectProvider<WorkflowRuntimeRepository> runtimePersistence,
      ObjectProvider<WorkflowDefinitionRepository> definitionRepository,
      ObjectProvider<WorkflowScheduleTriggerDao> triggerDao,
      ObjectProvider<WorkflowBusinessDateRerunGateway> businessDateRerun) {
    this.runtime = runtime;
    this.reactivation = reactivation;
    this.runtimePersistence = runtimePersistence;
    this.definitionRepository = definitionRepository;
    this.triggerDao = triggerDao;
    this.businessDateRerun = businessDateRerun;
  }

  public WorkflowInstanceOperationsVO describe(String executionId) {
    WorkflowInstanceVO instance = runtime.getInstance(required(executionId, "实例 ID 不能为空"));
    Map<String, Object> input = instance.input() == null ? Map.of() : instance.input();
    RuntimeMetadataRecord metadata = metadata(instance.id());

    String workflowId = workflowId(instance.id());
    // Launch metadata 描述“本次为什么启动”；input 保留原调度血缘，两者不能混淆。
    String triggerType = first(metadata == null ? null : metadata.triggerType(), text(input.get("triggerType")));
    String triggerId = first(metadata == null ? null : metadata.triggerId(), text(input.get("triggerId")));
    String scheduleId = first(text(input.get("scheduleId")), metadata == null ? null : metadata.scheduleId());
    String backfillId = text(input.get("backfillId"));
    String operationType = text(input.get("operationType"));
    String sourceExecutionId = first(text(input.get("sourceExecutionId")), instance.sourceExecutionId());
    String scheduleTimezone = text(input.get("scheduleTimezone"));
    String scheduleTime = text(input.get("scheduleTime"));
    String cronExpression = text(input.get("cronExpression"));
    LocalDate businessDate = date(input.get("businessDate"));
    Instant plannedFireTime = instant(input.get("plannedFireTime"));
    if (plannedFireTime == null && metadata != null) plannedFireTime = metadata.plannedFireTime();

    String unavailable = businessDateRerunUnavailableReason(
        instance, workflowId, scheduleId, cronExpression, scheduleTimezone);
    return new WorkflowInstanceOperationsVO(
        instance.id(),
        workflowId,
        triggerType,
        triggerId,
        scheduleId,
        backfillId,
        operationType,
        sourceExecutionId,
        businessDate,
        scheduleTime,
        scheduleTimezone,
        plannedFireTime,
        cronExpression,
        unavailable == null,
        unavailable,
        edges(instance.definitionId()));
  }

  public WorkflowBackfillVO rerunBusinessDate(
      String executionId,
      WorkflowBusinessDateRerunDTO request) {
    String id = required(executionId, "实例 ID 不能为空");
    WorkflowInstanceVO instance = runtime.getInstance(id);
    WorkflowInstanceOperationsVO operations = describe(id);
    if (!operations.businessDateRerunSupported()) {
      throw new IllegalStateException(operations.businessDateRerunUnavailableReason());
    }
    WorkflowBusinessDateRerunGateway gateway = businessDateRerun.getIfAvailable();
    if (gateway == null) throw new IllegalStateException("当前运行模式不支持 durable businessDate 补跑");
    return gateway.createBusinessDateRerun(id, instance, request);
  }

  /** 每个实例独立处理；其中一条失败不会回滚已经成功恢复的其它实例。 */
  public WorkflowBatchRetryVO batchRetryFailed(WorkflowBatchRetryDTO request) {
    if (request == null || request.executionIds() == null || request.executionIds().isEmpty()) {
      throw new IllegalArgumentException("请选择需要重试的失败实例");
    }
    LinkedHashSet<String> ids = new LinkedHashSet<>();
    for (String value : request.executionIds()) {
      if (value != null && !value.isBlank()) ids.add(value.trim());
    }
    if (ids.isEmpty()) throw new IllegalArgumentException("请选择需要重试的失败实例");
    if (ids.size() > MAX_BATCH_RETRY) {
      throw new IllegalArgumentException("单次最多批量重试 " + MAX_BATCH_RETRY + " 个实例");
    }

    List<ItemVO> items = new ArrayList<>();
    int accepted = 0;
    for (String id : ids) {
      try {
        WorkflowInstanceVO result = reactivation.retryFailedNodes(id);
        accepted++;
        items.add(new ItemVO(id, true, result.status(), "已重新调度失败/阻断节点"));
      } catch (RuntimeException exception) {
        items.add(new ItemVO(id, false, null, safeMessage(exception)));
      }
    }
    return new WorkflowBatchRetryVO(ids.size(), accepted, ids.size() - accepted, items);
  }

  private String businessDateRerunUnavailableReason(
      WorkflowInstanceVO instance,
      String workflowId,
      String scheduleId,
      String cronExpression,
      String scheduleTimezone) {
    if (!terminal(instance.status())) return "仅终态实例支持指定 businessDate 重跑";
    if (instance.workflowVersionId() == null || instance.workflowVersionNo() == null) {
      return "实例没有不可变发布版本，测试/临时 DAG 不能按 businessDate 重跑";
    }
    if (workflowId == null) return "无法解析实例所属工作流";
    if (scheduleId == null) return "实例没有 Schedule 调度血缘";
    if (cronExpression == null) return "实例缺少创建时 Cron 快照";
    if (scheduleTimezone == null) return "实例缺少创建时调度时区";
    if (businessDateRerun.getIfAvailable() == null) return "当前运行模式未启用 durable Backfill";
    return null;
  }

  private List<EdgeVO> edges(String definitionId) {
    WorkflowDefinitionRepository repository = definitionRepository.getIfAvailable();
    if (repository == null || definitionId == null) return List.of();
    return repository.findById(definitionId)
        .map(definition -> definition.edges().stream().map(this::edge).toList())
        .orElse(List.of());
  }

  private EdgeVO edge(EdgeDefinition edge) {
    return new EdgeVO(edge.fromNodeId(), edge.toNodeId());
  }

  private RuntimeMetadataRecord metadata(String executionId) {
    WorkflowRuntimeRepository persistence = runtimePersistence.getIfAvailable();
    return persistence == null ? null : persistence.findMetadata(executionId).orElse(null);
  }

  private String workflowId(String executionId) {
    WorkflowScheduleTriggerDao dao = triggerDao.getIfAvailable();
    if (dao == null) return null;
    return dao.selectWorkflowIdByExecution(executionId);
  }

  private boolean terminal(String status) {
    return "SUCCESS".equals(status)
        || "SUCCESS_WITH_WARNINGS".equals(status)
        || "FAILED".equals(status)
        || "WARNING".equals(status)
        || "CANCELED".equals(status)
        || "TIMED_OUT".equals(status);
  }

  private LocalDate date(Object value) {
    String text = text(value);
    if (text == null) return null;
    try {
      return LocalDate.parse(text);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private Instant instant(Object value) {
    String text = text(value);
    if (text == null) return null;
    try {
      return Instant.parse(text);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String text(Object value) {
    if (value == null) return null;
    String result = String.valueOf(value).trim();
    return result.isEmpty() ? null : result;
  }

  private String first(String preferred, String fallback) {
    return preferred == null ? fallback : preferred;
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    return value.trim();
  }

  private String safeMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) current = current.getCause();
    String message = current.getMessage();
    String value = message == null || message.isBlank()
        ? current.getClass().getSimpleName()
        : message;
    return value.length() <= 1000 ? value : value.substring(0, 1000);
  }
}
