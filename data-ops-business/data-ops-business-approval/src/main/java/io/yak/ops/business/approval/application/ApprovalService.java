package io.yak.ops.business.approval.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.approval.dao.mapper.ApprovalFlowMapper;
import io.yak.ops.business.approval.dao.mapper.ApprovalInstanceMapper;
import io.yak.ops.business.approval.dao.mapper.ApprovalStepMapper;
import io.yak.ops.business.approval.domain.ApprovalStateMachine;
import io.yak.ops.business.approval.domain.FlowStepConfig;
import io.yak.ops.business.approval.domain.FlowStepsCodec;
import io.yak.ops.business.approval.exception.ApprovalException;
import io.yak.ops.business.approval.registry.ApprovalFlowRegistry;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.approval.dao.model.ApprovalFlowPO;
import io.yak.ops.business.approval.dao.model.ApprovalInstancePO;
import io.yak.ops.business.approval.dao.model.ApprovalStepPO;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import io.yak.ops.common.enums.approval.ApprovalInstanceStatus;
import io.yak.ops.common.enums.approval.ApprovalStepStatus;
import io.yak.ops.core.project.CurrentProject;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 审批核心闭环(ticket 103):发起/待办/通过/拒绝/撤销 + 在途唯一(D6) + 乐观并发。
 * 终态回调与操作同事务(D5):handler 抛错 → 49009 + 整体回滚,审批动作失败可见。
 */
@Service
@RequiredArgsConstructor
public class ApprovalService implements ApprovalApi {

  static final int MAX_PAYLOAD_BYTES = 64 * 1024;
  private static final String ACTIVE = "Y";

  private final CurrentProject currentProject;
  private final ApprovalFlowMapper flowMapper;
  private final ApprovalInstanceMapper instanceMapper;
  private final ApprovalStepMapper stepMapper;
  private final ApprovalFlowRegistry registry;
  private final BusinessAuditService auditService;

  public record StepView(Long id, Integer levelNo, String approver, String status,
      String comment, LocalDateTime handledTime) {}

  public record ApprovalDetailView(
      ApprovalInstanceView instance, List<StepView> steps, String cancelReason) {}

  public record TodoView(Long stepId, Integer levelNo, ApprovalInstanceView instance) {}

  public record HandledView(Long stepId, Integer levelNo, String stepStatus, String comment,
      LocalDateTime handledTime, ApprovalInstanceView instance) {}

  // ---------- 发起 ----------

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ApprovalInstanceView submit(ApprovalSubmitCommand cmd) {
    Long projectId = currentProject.requireProjectId();
    requireText(cmd.flowCode(), "flowCode");
    requireText(cmd.bizType(), "bizType");
    requireText(cmd.bizId(), "bizId");
    requireText(cmd.title(), "title");
    requireText(cmd.applicant(), "applicant");
    if (cmd.payloadJson() != null
        && cmd.payloadJson().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
      throw new ApprovalException(ApprovalErrorCode.PAYLOAD_TOO_LARGE, cmd.bizId());
    }
    ApprovalFlowPO flow = flowMapper.selectOne(new LambdaQueryWrapper<ApprovalFlowPO>()
        .eq(ApprovalFlowPO::getProjectId, projectId)
        .eq(ApprovalFlowPO::getFlowCode, cmd.flowCode())
        .eq(ApprovalFlowPO::getDeleted, false));
    if (flow == null) {
      throw new ApprovalException(ApprovalErrorCode.FLOW_NOT_FOUND, cmd.flowCode());
    }
    if (!Boolean.TRUE.equals(flow.getEnabled())) {
      throw new ApprovalException(ApprovalErrorCode.FLOW_DISABLED, cmd.flowCode());
    }
    registry.require(cmd.flowCode());
    List<FlowStepConfig> levels = FlowStepsCodec.parse(flow.getStepsJson());
    if (instanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstancePO>()
        .eq(ApprovalInstancePO::getProjectId, projectId)
        .eq(ApprovalInstancePO::getFlowCode, cmd.flowCode())
        .eq(ApprovalInstancePO::getBizType, cmd.bizType())
        .eq(ApprovalInstancePO::getBizId, cmd.bizId())
        .eq(ApprovalInstancePO::getStatus, ApprovalInstanceStatus.PENDING.name())) > 0) {
      throw new ApprovalException(ApprovalErrorCode.DUPLICATE_IN_FLIGHT,
          cmd.flowCode() + "/" + cmd.bizType() + "/" + cmd.bizId());
    }

    LocalDateTime now = LocalDateTime.now();
    ApprovalInstancePO instance = new ApprovalInstancePO();
    instance.setProjectId(projectId);
    instance.setFlowCode(flow.getFlowCode());
    instance.setFlowName(flow.getFlowName());
    instance.setBizType(cmd.bizType());
    instance.setBizId(cmd.bizId());
    instance.setTitle(cmd.title());
    instance.setPayloadJson(cmd.payloadJson());
    instance.setApplicant(cmd.applicant());
    instance.setStatus(ApprovalInstanceStatus.PENDING.name());
    instance.setCurrentLevel(levels.get(0).level());
    instance.setActiveFlag(ACTIVE);
    instance.setCreatedBy(cmd.applicant());
    instance.setUpdatedBy(cmd.applicant());
    instance.setCreateTime(now);
    instance.setUpdateTime(now);
    instance.setDeleted(false);
    try {
      instanceMapper.insert(instance);
    } catch (DuplicateKeyException e) {
      // 预检后仍可能有并发窗口,uk(project,flow,biz,active_flag) 兜底 → 49003
      throw new ApprovalException(ApprovalErrorCode.DUPLICATE_IN_FLIGHT,
          cmd.flowCode() + "/" + cmd.bizType() + "/" + cmd.bizId(), e);
    }

    for (FlowStepConfig level : levels) {
      for (String approver : level.approvers()) {
        ApprovalStepPO step = new ApprovalStepPO();
        step.setProjectId(projectId);
        step.setInstanceId(instance.getId());
        step.setLevelNo(level.level());
        step.setApprover(approver);
        step.setStatus(level.level() == instance.getCurrentLevel()
            ? ApprovalStepStatus.PENDING.name() : ApprovalStepStatus.WAITING.name());
        step.setCreatedBy(cmd.applicant());
        step.setUpdatedBy(cmd.applicant());
        step.setCreateTime(now);
        step.setUpdateTime(now);
        step.setDeleted(false);
        stepMapper.insert(step);
      }
    }
    audit("APPROVAL_SUBMIT", "发起审批", instance, cmd.applicant(),
        Map.of("flowCode", cmd.flowCode(), "biz", cmd.bizType() + ":" + cmd.bizId()));
    return toView(instance);
  }

  // ---------- 查询 ----------

  @Override
  public ApprovalInstanceView find(String flowCode, String bizType, String bizId) {
    Long projectId = currentProject.requireProjectId();
    List<ApprovalInstancePO> inFlight = instanceMapper.selectList(
        new LambdaQueryWrapper<ApprovalInstancePO>()
            .eq(ApprovalInstancePO::getProjectId, projectId)
            .eq(ApprovalInstancePO::getFlowCode, flowCode)
            .eq(ApprovalInstancePO::getBizType, bizType)
            .eq(ApprovalInstancePO::getBizId, bizId)
            .eq(ApprovalInstancePO::getStatus, ApprovalInstanceStatus.PENDING.name())
            .orderByDesc(ApprovalInstancePO::getId).last("LIMIT 1"));
    if (!inFlight.isEmpty()) {
      return toView(inFlight.get(0));
    }
    List<ApprovalInstancePO> latest = instanceMapper.selectList(
        new LambdaQueryWrapper<ApprovalInstancePO>()
            .eq(ApprovalInstancePO::getProjectId, projectId)
            .eq(ApprovalInstancePO::getFlowCode, flowCode)
            .eq(ApprovalInstancePO::getBizType, bizType)
            .eq(ApprovalInstancePO::getBizId, bizId)
            .orderByDesc(ApprovalInstancePO::getId).last("LIMIT 1"));
    return latest.isEmpty() ? null : toView(latest.get(0));
  }

  /** REST projection: the internal business lookup does not grant read access to its caller. */
  public ApprovalInstanceView findVisible(
      String flowCode, String bizType, String bizId, String operator, boolean manage) {
    ApprovalInstanceView instance = find(flowCode, bizType, bizId);
    if (instance == null || manage || instance.applicant().equals(operator)) {
      return instance;
    }
    long involvementCount = stepMapper.selectCount(new LambdaQueryWrapper<ApprovalStepPO>()
        .eq(ApprovalStepPO::getProjectId, currentProject.requireProjectId())
        .eq(ApprovalStepPO::getInstanceId, instance.id())
        .eq(ApprovalStepPO::getApprover, operator)
        .eq(ApprovalStepPO::getDeleted, false));
    if (involvementCount == 0) {
      throw new ApprovalException(ApprovalErrorCode.NOT_INVOLVED);
    }
    return instance;
  }

  @Override
  public boolean isFlowEnabled(String flowCode) {
    ApprovalFlowPO flow = flowMapper.selectOne(new LambdaQueryWrapper<ApprovalFlowPO>()
        .eq(ApprovalFlowPO::getProjectId, currentProject.requireProjectId())
        .eq(ApprovalFlowPO::getFlowCode, flowCode)
        .eq(ApprovalFlowPO::getDeleted, false));
    return flow != null && Boolean.TRUE.equals(flow.getEnabled());
  }

  public ApprovalDetailView detail(Long instanceId, String operator, boolean manage) {
    ApprovalInstancePO instance = requireInstance(currentProject.requireProjectId(), instanceId);
    List<ApprovalStepPO> steps = stepMapper.selectList(new LambdaQueryWrapper<ApprovalStepPO>()
        .eq(ApprovalStepPO::getInstanceId, instanceId)
        .eq(ApprovalStepPO::getDeleted, false)
        .orderByAsc(ApprovalStepPO::getLevelNo).orderByAsc(ApprovalStepPO::getId));
    if (!manage && !isInvolved(instance, steps, operator)) {
      throw new ApprovalException(ApprovalErrorCode.NOT_INVOLVED);
    }
    return new ApprovalDetailView(toView(instance),
        steps.stream().map(s -> new StepView(s.getId(), s.getLevelNo(), s.getApprover(),
            s.getStatus(), s.getComment(), s.getHandledTime())).toList(), instance.getCancelReason());
  }

  public PageData<TodoView> todo(String operator, int pageNo, int pageSize) {
    Long projectId = currentProject.requireProjectId();
    // 终态清理不变式:step PENDING ⇔ 在途且轮到该行,只打 step 表即可。
    Page<ApprovalStepPO> page = stepMapper.selectPage(new Page<>(pageNo, pageSize),
        new LambdaQueryWrapper<ApprovalStepPO>()
            .eq(ApprovalStepPO::getProjectId, projectId)
            .eq(ApprovalStepPO::getApprover, operator)
            .eq(ApprovalStepPO::getStatus, ApprovalStepStatus.PENDING.name())
            .eq(ApprovalStepPO::getDeleted, false)
            .orderByDesc(ApprovalStepPO::getId));
    return new PageData<>(toTodoViews(page.getRecords()), page.getTotal(), page.getPages(),
        (int) page.getCurrent(), (int) page.getSize());
  }

  public long todoCount(String operator) {
    return stepMapper.selectCount(new LambdaQueryWrapper<ApprovalStepPO>()
        .eq(ApprovalStepPO::getProjectId, currentProject.requireProjectId())
        .eq(ApprovalStepPO::getApprover, operator)
        .eq(ApprovalStepPO::getStatus, ApprovalStepStatus.PENDING.name())
        .eq(ApprovalStepPO::getDeleted, false));
  }

  public PageData<ApprovalInstanceView> mine(String operator, String status,
      int pageNo, int pageSize) {
    Long projectId = currentProject.requireProjectId();
    Page<ApprovalInstancePO> page = instanceMapper.selectPage(new Page<>(pageNo, pageSize),
        new LambdaQueryWrapper<ApprovalInstancePO>()
            .eq(ApprovalInstancePO::getProjectId, projectId)
            .eq(ApprovalInstancePO::getApplicant, operator)
            .eq(StringUtils.hasText(status), ApprovalInstancePO::getStatus, status)
            .eq(ApprovalInstancePO::getDeleted, false)
            .orderByDesc(ApprovalInstancePO::getId));
    return new PageData<>(page.getRecords().stream().map(this::toView).toList(),
        page.getTotal(), page.getPages(), (int) page.getCurrent(), (int) page.getSize());
  }

  public PageData<HandledView> handled(String operator, int pageNo, int pageSize) {
    Long projectId = currentProject.requireProjectId();
    Page<ApprovalStepPO> page = stepMapper.selectPage(new Page<>(pageNo, pageSize),
        new LambdaQueryWrapper<ApprovalStepPO>()
            .eq(ApprovalStepPO::getProjectId, projectId)
            .eq(ApprovalStepPO::getApprover, operator)
            .in(ApprovalStepPO::getStatus, List.of(ApprovalStepStatus.APPROVED.name(),
                ApprovalStepStatus.REJECTED.name()))
            .eq(ApprovalStepPO::getDeleted, false)
            .orderByDesc(ApprovalStepPO::getHandledTime).orderByDesc(ApprovalStepPO::getId));
    return new PageData<>(toHandledViews(page.getRecords()), page.getTotal(), page.getPages(),
        (int) page.getCurrent(), (int) page.getSize());
  }

  // ---------- 审批动作 ----------

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ApprovalInstanceView approve(Long instanceId, String comment, String operator) {
    return decide(instanceId, true, comment, operator);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public ApprovalInstanceView reject(Long instanceId, String comment, String operator) {
    if (!StringUtils.hasText(comment)) {
      throw new ApprovalException(ApprovalErrorCode.REJECT_COMMENT_REQUIRED);
    }
    return decide(instanceId, false, comment.trim(), operator);
  }

  @Override
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void cancel(Long instanceId, String operator, String reason) {
    ApprovalInstancePO instance = requireInstance(currentProject.requireProjectId(), instanceId);
    if (!ApprovalInstanceStatus.PENDING.name().equals(instance.getStatus())) {
      throw new ApprovalException(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
          "单据已是终态:" + instance.getStatus());
    }
    if (!instance.getApplicant().equals(operator)) {
      throw new ApprovalException(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR, "仅发起人可撤销");
    }
    String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : null;
    if (normalizedReason != null && normalizedReason.length() > 512) {
      throw new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT, "撤销原因最多 512 个字符");
    }
    AuditOperationHandle operation = startAudit(
        "APPROVAL_CANCEL", "撤销审批", instance, operator);
    try {
      registry.require(instance.getFlowCode());
      LocalDateTime now = LocalDateTime.now();
      int moved = instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstancePO>()
          .eq(ApprovalInstancePO::getId, instanceId)
          .eq(ApprovalInstancePO::getStatus, ApprovalInstanceStatus.PENDING.name())
          .set(ApprovalInstancePO::getStatus, ApprovalInstanceStatus.CANCELED.name())
          .set(ApprovalInstancePO::getActiveFlag, null)
          .set(ApprovalInstancePO::getFinishTime, now)
          .set(ApprovalInstancePO::getCancelReason, normalizedReason)
          .set(ApprovalInstancePO::getUpdatedBy, operator)
          .set(ApprovalInstancePO::getUpdateTime, now));
      if (moved == 0) {
        throw new ApprovalException(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR, "并发冲突,请刷新");
      }
      skipOpenSteps(instanceId, operator, now);
      instance.setStatus(ApprovalInstanceStatus.CANCELED.name());
      instance.setCancelReason(normalizedReason);
      terminalCallback(instance, null, normalizedReason, "onCanceled");
      completeAudit(operation, "撤销审批:" + instance.getTitle(),
          Map.of("reason", String.valueOf(normalizedReason)));
    } catch (RuntimeException exception) {
      operation.failure("APPROVAL_CANCEL_FAILED", exception);
      throw exception;
    }
  }

  private ApprovalInstanceView decide(Long instanceId, boolean approved, String comment,
      String operator) {
    Long projectId = currentProject.requireProjectId();
    ApprovalInstancePO instance = requireInstance(projectId, instanceId);
    if (!ApprovalInstanceStatus.PENDING.name().equals(instance.getStatus())) {
      throw new ApprovalException(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
          "单据已是终态:" + instance.getStatus());
    }
    ApprovalStepPO mineStep = stepMapper.selectOne(new LambdaQueryWrapper<ApprovalStepPO>()
        .eq(ApprovalStepPO::getInstanceId, instanceId)
        .eq(ApprovalStepPO::getApprover, operator)
        .eq(ApprovalStepPO::getStatus, ApprovalStepStatus.PENDING.name())
        .eq(ApprovalStepPO::getDeleted, false));
    if (mineStep == null) {
      throw new ApprovalException(ApprovalErrorCode.NOT_CURRENT_APPROVER);
    }
    String operationCode = approved ? "APPROVAL_APPROVE" : "APPROVAL_REJECT";
    String operationName = approved ? "通过审批" : "拒绝审批";
    AuditOperationHandle operation = startAudit(operationCode, operationName, instance, operator);
    try {
      int maxLevel = stepsOf(instanceId).stream().mapToInt(ApprovalStepPO::getLevelNo).max()
          .orElse(mineStep.getLevelNo());
      if (!approved || mineStep.getLevelNo() >= maxLevel) {
        registry.require(instance.getFlowCode());
      }
      LocalDateTime now = LocalDateTime.now();
      int flipped = stepMapper.update(null, new LambdaUpdateWrapper<ApprovalStepPO>()
          .eq(ApprovalStepPO::getId, mineStep.getId())
          .eq(ApprovalStepPO::getStatus, ApprovalStepStatus.PENDING.name())
          .set(ApprovalStepPO::getStatus,
              approved ? ApprovalStepStatus.APPROVED.name() : ApprovalStepStatus.REJECTED.name())
          .set(ApprovalStepPO::getComment, StringUtils.hasText(comment) ? comment.trim() : null)
          .set(ApprovalStepPO::getHandledTime, now)
          .set(ApprovalStepPO::getUpdatedBy, operator)
          .set(ApprovalStepPO::getUpdateTime, now));
      if (flipped == 0) {
        throw new ApprovalException(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR, "并发冲突,请刷新");
      }

      if (!approved) {
        finish(instance, ApprovalInstanceStatus.REJECTED, operator, now);
        skipOpenSteps(instanceId, operator, now);
        terminalCallback(instance, operator, comment, "onRejected");
        completeAudit(operation, operationName + ":" + instance.getTitle(),
            Map.of("level", mineStep.getLevelNo(), "comment", String.valueOf(comment)));
        return toView(instance);
      }

      ApprovalStateMachine.OnApprove next =
          ApprovalStateMachine.onApprove(mineStep.getLevelNo(), maxLevel);
      skipOpenStepsAtLevel(instanceId, mineStep.getLevelNo(), operator, now);
      if (next.finished()) {
        finish(instance, ApprovalInstanceStatus.APPROVED, operator, now);
        skipOpenSteps(instanceId, operator, now);
        terminalCallback(instance, operator, comment, "onApproved");
      } else {
        int moved = instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstancePO>()
            .eq(ApprovalInstancePO::getId, instanceId)
            .eq(ApprovalInstancePO::getStatus, ApprovalInstanceStatus.PENDING.name())
            .set(ApprovalInstancePO::getCurrentLevel, next.nextLevelNo())
            .set(ApprovalInstancePO::getUpdatedBy, operator)
            .set(ApprovalInstancePO::getUpdateTime, now));
        if (moved == 0) {
          throw new ApprovalException(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
              "并发冲突,请刷新");
        }
        stepMapper.update(null, new LambdaUpdateWrapper<ApprovalStepPO>()
            .eq(ApprovalStepPO::getInstanceId, instanceId)
            .eq(ApprovalStepPO::getLevelNo, next.nextLevelNo())
            .eq(ApprovalStepPO::getStatus, ApprovalStepStatus.WAITING.name())
            .set(ApprovalStepPO::getStatus, ApprovalStepStatus.PENDING.name())
            .set(ApprovalStepPO::getUpdateTime, now));
        instance.setCurrentLevel(next.nextLevelNo());
      }
      completeAudit(operation, operationName + ":" + instance.getTitle(),
          Map.of("level", mineStep.getLevelNo()));
      return toView(instance);
    } catch (RuntimeException exception) {
      operation.failure(approved ? "APPROVAL_APPROVE_FAILED" : "APPROVAL_REJECT_FAILED", exception);
      throw exception;
    }
  }

  // ---------- 内部 ----------

  private void finish(ApprovalInstancePO instance, ApprovalInstanceStatus target,
      String operator, LocalDateTime now) {
    int moved = instanceMapper.update(null, new LambdaUpdateWrapper<ApprovalInstancePO>()
        .eq(ApprovalInstancePO::getId, instance.getId())
        .eq(ApprovalInstancePO::getStatus, ApprovalInstanceStatus.PENDING.name())
        .set(ApprovalInstancePO::getStatus, target.name())
        .set(ApprovalInstancePO::getActiveFlag, null)
        .set(ApprovalInstancePO::getFinishTime, now)
        .set(ApprovalInstancePO::getUpdatedBy, operator)
        .set(ApprovalInstancePO::getUpdateTime, now));
    if (moved == 0) {
      throw new ApprovalException(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR, "并发冲突,请刷新");
    }
    instance.setStatus(target.name());
    instance.setActiveFlag(null);
    instance.setFinishTime(now);
  }

  /** 终态清理:剩余 WAITING/PENDING 一律 SKIPPED(不变式,勿改)。 */
  private void skipOpenSteps(Long instanceId, String operator, LocalDateTime now) {
    stepMapper.update(null, new LambdaUpdateWrapper<ApprovalStepPO>()
        .eq(ApprovalStepPO::getInstanceId, instanceId)
        .in(ApprovalStepPO::getStatus, ApprovalStateMachine.OPEN_STEP_STATUSES)
        .set(ApprovalStepPO::getStatus, ApprovalStepStatus.SKIPPED.name())
        .set(ApprovalStepPO::getUpdatedBy, operator)
        .set(ApprovalStepPO::getUpdateTime, now));
  }

  /** 同级定级后同侪 SKIPPED(ANY 语义)。 */
  private void skipOpenStepsAtLevel(Long instanceId, int levelNo, String operator,
      LocalDateTime now) {
    stepMapper.update(null, new LambdaUpdateWrapper<ApprovalStepPO>()
        .eq(ApprovalStepPO::getInstanceId, instanceId)
        .eq(ApprovalStepPO::getLevelNo, levelNo)
        .eq(ApprovalStepPO::getStatus, ApprovalStepStatus.PENDING.name())
        .set(ApprovalStepPO::getStatus, ApprovalStepStatus.SKIPPED.name())
        .set(ApprovalStepPO::getUpdatedBy, operator)
        .set(ApprovalStepPO::getUpdateTime, now));
  }

  /** 终态回调,同事务(D5):handler 缺失时操作失败,抛错 → 49009 整体回滚。 */
  private void terminalCallback(ApprovalInstancePO instance, String lastApprover, String comment,
      String action) {
    var handler = registry.require(instance.getFlowCode());
    ApprovalDecision decision = new ApprovalDecision(instance.getId(), instance.getFlowCode(),
        instance.getBizType(), instance.getBizId(), instance.getPayloadJson(),
        instance.getApplicant(), lastApprover, StringUtils.hasText(comment) ? comment : null,
        instance.getFinishTime() == null ? LocalDateTime.now() : instance.getFinishTime());
    try {
      switch (action) {
        case "onApproved" -> handler.onApproved(decision);
        case "onRejected" -> handler.onRejected(decision);
        default -> handler.onCanceled(decision);
      }
    } catch (ApprovalException e) {
      throw e;
    } catch (Exception e) {
      throw new ApprovalException(ApprovalErrorCode.CALLBACK_FAILED,
          instance.getFlowCode() + " " + action + " " + e.getMessage(), e);
    }
  }

  private List<ApprovalStepPO> stepsOf(Long instanceId) {
    return stepMapper.selectList(new LambdaQueryWrapper<ApprovalStepPO>()
        .eq(ApprovalStepPO::getInstanceId, instanceId)
        .eq(ApprovalStepPO::getDeleted, false));
  }

  private ApprovalInstancePO requireInstance(Long projectId, Long instanceId) {
    ApprovalInstancePO instance = instanceMapper.selectById(instanceId);
    if (instance == null || !instance.getProjectId().equals(projectId)
        || Boolean.TRUE.equals(instance.getDeleted())) {
      throw new ApprovalException(ApprovalErrorCode.ILLEGAL_STATE_OR_OPERATOR,
          "审批单不存在:" + instanceId);
    }
    return instance;
  }

  private boolean isInvolved(ApprovalInstancePO instance, List<ApprovalStepPO> steps,
      String operator) {
    return instance.getApplicant().equals(operator)
        || steps.stream().anyMatch(s -> s.getApprover().equals(operator));
  }

  private List<TodoView> toTodoViews(List<ApprovalStepPO> steps) {
    if (steps.isEmpty()) {
      return List.of();
    }
    Map<Long, ApprovalInstancePO> instances = instanceMapper
        .selectBatchIds(steps.stream().map(ApprovalStepPO::getInstanceId).distinct().toList())
        .stream().collect(java.util.stream.Collectors.toMap(ApprovalInstancePO::getId, i -> i));
    return steps.stream()
        .map(s -> new TodoView(s.getId(), s.getLevelNo(),
            instances.get(s.getInstanceId()) == null ? null
                : toView(instances.get(s.getInstanceId()))))
        .filter(v -> v.instance() != null)
        .toList();
  }

  private List<HandledView> toHandledViews(List<ApprovalStepPO> steps) {
    if (steps.isEmpty()) {
      return List.of();
    }
    Map<Long, ApprovalInstancePO> instances = instanceMapper
        .selectBatchIds(steps.stream().map(ApprovalStepPO::getInstanceId).distinct().toList())
        .stream().collect(java.util.stream.Collectors.toMap(ApprovalInstancePO::getId, i -> i));
    return steps.stream()
        .map(s -> new HandledView(s.getId(), s.getLevelNo(), s.getStatus(), s.getComment(),
            s.getHandledTime(), instances.get(s.getInstanceId()) == null ? null
                : toView(instances.get(s.getInstanceId()))))
        .filter(v -> v.instance() != null)
        .toList();
  }

  private ApprovalInstanceView toView(ApprovalInstancePO po) {
    return new ApprovalInstanceView(po.getId(), po.getFlowCode(), po.getFlowName(),
        po.getBizType(), po.getBizId(), po.getTitle(), po.getPayloadJson(), po.getApplicant(),
        po.getStatus(), po.getCurrentLevel(), po.getCreateTime(), po.getFinishTime());
  }

  private static void requireText(String value, String name) {
    if (!StringUtils.hasText(value)) {
      throw new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT, name + " 不能为空");
    }
  }

  private void audit(String code, String action, ApprovalInstancePO instance, String operator,
      Map<String, ?> detail) {
    completeAudit(startAudit(code, action, instance, operator),
        action + ":" + instance.getTitle(), detail);
  }

  private AuditOperationHandle startAudit(
      String code, String action, ApprovalInstancePO instance, String operator) {
    return auditService.start(new AuditOperationRequest(
        code, action, "APPROVAL_INSTANCE", String.valueOf(instance.getId()),
        instance.getTitle(), "APPLICATION",
        Map.of("operator", String.valueOf(operator), "flowCode", instance.getFlowCode())));
  }

  private void completeAudit(
      AuditOperationHandle handle, String message, Map<String, ?> detail) {
    AuditTransactions.completeOnCommit(
        handle, AuditEventType.RESOURCE_UPDATED, message, detail, null);
  }
}
