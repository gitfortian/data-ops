package io.yak.ops.business.approval.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.framework.common.PageData;
import io.yak.ops.business.approval.dao.mapper.ApprovalFlowMapper;
import io.yak.ops.business.approval.dao.mapper.ApprovalInstanceMapper;
import io.yak.ops.business.approval.domain.FlowStepConfig;
import io.yak.ops.business.approval.domain.FlowStepsCodec;
import io.yak.ops.business.approval.exception.ApprovalException;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.approval.dao.model.ApprovalFlowPO;
import io.yak.ops.business.approval.dao.model.ApprovalInstancePO;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import io.yak.ops.common.enums.approval.ApprovalInstanceStatus;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 流程定义管理(ticket 102):CRUD + 启停,配置集中由管理员维护。 */
@Service
@RequiredArgsConstructor
public class FlowAdminService {

  private final CurrentProject currentProject;
  private final ApprovalFlowMapper flowMapper;
  private final ApprovalInstanceMapper instanceMapper;
  private final BusinessAuditService auditService;

  public record FlowView(
      Long id, String flowCode, String flowName, String description,
      List<FlowStepConfig> steps, boolean enabled,
      LocalDateTime createTime, LocalDateTime updateTime) {}

  public PageData<FlowView> list(String keyword, int pageNo, int pageSize) {
    Long projectId = currentProject.requireProjectId();
    Page<ApprovalFlowPO> page = flowMapper.selectPage(new Page<>(pageNo, pageSize),
        new LambdaQueryWrapper<ApprovalFlowPO>()
            .eq(ApprovalFlowPO::getProjectId, projectId)
            .eq(ApprovalFlowPO::getDeleted, false)
            .and(StringUtils.hasText(keyword), w -> w
                .like(ApprovalFlowPO::getFlowName, keyword)
                .or().like(ApprovalFlowPO::getFlowCode, keyword))
            .orderByDesc(ApprovalFlowPO::getId));
    return new PageData<>(page.getRecords().stream().map(this::toView).toList(),
        page.getTotal(), page.getPages(), (int) page.getCurrent(), (int) page.getSize());
  }

  public FlowView get(Long id) {
    return toView(requireFlow(currentProject.requireProjectId(), id));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public FlowView create(String flowCode, String flowName, String description,
      List<List<String>> levels, String operator) {
    Long projectId = currentProject.requireProjectId();
    String code = flowCode == null ? null : flowCode.trim();
    if (!StringUtils.hasText(code)) {
      throw new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT, "流程编码不能为空");
    }
    String stepsJson = FlowStepsCodec.serialize(levels);
    if (flowMapper.selectCount(new LambdaQueryWrapper<ApprovalFlowPO>()
        .eq(ApprovalFlowPO::getProjectId, projectId)
        .eq(ApprovalFlowPO::getFlowCode, code)
        .eq(ApprovalFlowPO::getDeleted, false)) > 0) {
      throw new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT, "流程编码已存在:" + code);
    }
    LocalDateTime now = LocalDateTime.now();
    ApprovalFlowPO po = new ApprovalFlowPO();
    po.setProjectId(projectId);
    po.setFlowCode(code);
    po.setFlowName(flowName.trim());
    po.setDescription(description);
    po.setStepsJson(stepsJson);
    po.setEnabled(true);
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setCreateTime(now);
    po.setUpdateTime(now);
    po.setDeleted(false);
    flowMapper.insert(po);
    audit("APPROVAL_FLOW_UPSERT", "新增审批流程", po.getId(), po.getFlowName(), operator);
    return toView(po);
  }

  /** flowCode 一旦被实例引用即不可变,只允许改名/描述/审批人配置。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public FlowView update(Long id, String flowName, String description,
      List<List<String>> levels, String operator) {
    ApprovalFlowPO po = requireFlow(currentProject.requireProjectId(), id);
    if (!StringUtils.hasText(flowName)) {
      throw new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT, "流程名称不能为空");
    }
    ApprovalFlowPO patch = patchFor(po, operator);
    patch.setFlowName(flowName.trim());
    patch.setDescription(description);
    patch.setStepsJson(FlowStepsCodec.serialize(levels));

    flowMapper.updateById(patch);
    audit("APPROVAL_FLOW_UPSERT", "编辑审批流程", po.getId(), patch.getFlowName(), operator);
    return toView(flowMapper.selectById(po.getId()));
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public FlowView toggle(Long id, String operator) {
    ApprovalFlowPO po = requireFlow(currentProject.requireProjectId(), id);
    ApprovalFlowPO patch = patchFor(po, operator);
    patch.setEnabled(!Boolean.TRUE.equals(po.getEnabled()));

    flowMapper.updateById(patch);
    audit("APPROVAL_FLOW_TOGGLE",
        Boolean.TRUE.equals(patch.getEnabled()) ? "启用审批流程" : "停用审批流程",
        po.getId(), po.getFlowName(), operator);
    return toView(flowMapper.selectById(po.getId()));
  }

  /** 软删 + 释放编码(改写为 {code}#del#{id},uk 不含 deleted,避免多条软删行互撞)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id, String operator) {
    ApprovalFlowPO po = requireFlow(currentProject.requireProjectId(), id);
    if (instanceMapper.selectCount(new LambdaQueryWrapper<ApprovalInstancePO>()
        .eq(ApprovalInstancePO::getProjectId, po.getProjectId())
        .eq(ApprovalInstancePO::getFlowCode, po.getFlowCode())
        .eq(ApprovalInstancePO::getStatus, ApprovalInstanceStatus.PENDING.name())) > 0) {
      throw new ApprovalException(ApprovalErrorCode.INVALID_ARGUMENT,
          "该流程存在在途审批单,不可删除:" + po.getFlowCode());
    }
    ApprovalFlowPO patch = patchFor(po, operator);
    // Flow codes cannot contain '#'; the global row id makes this tombstone unique and bounded.
    patch.setFlowCode("#del#" + po.getId());
    patch.setEnabled(false);
    patch.setDeleted(true);

    flowMapper.updateById(patch);
    audit("APPROVAL_FLOW_DELETE", "删除审批流程", po.getId(), po.getFlowName(), operator);
  }

  /** Common patch fields for editing, toggling and deleting a flow definition. */
  private static ApprovalFlowPO patchFor(ApprovalFlowPO po, String operator) {
    ApprovalFlowPO patch = new ApprovalFlowPO();
    patch.setId(po.getId());
    patch.setUpdatedBy(operator);
    patch.setUpdateTime(LocalDateTime.now());
    return patch;
  }

  private ApprovalFlowPO requireFlow(Long projectId, Long id) {
    ApprovalFlowPO po = flowMapper.selectOne(new LambdaQueryWrapper<ApprovalFlowPO>()
        .eq(ApprovalFlowPO::getId, id)
        .eq(ApprovalFlowPO::getProjectId, projectId)
        .eq(ApprovalFlowPO::getDeleted, false));
    if (po == null) {
      throw new ApprovalException(ApprovalErrorCode.FLOW_NOT_FOUND);
    }
    return po;
  }

  private FlowView toView(ApprovalFlowPO po) {
    return new FlowView(po.getId(), po.getFlowCode(), po.getFlowName(), po.getDescription(),
        FlowStepsCodec.parse(po.getStepsJson()), Boolean.TRUE.equals(po.getEnabled()),
        po.getCreateTime(), po.getUpdateTime());
  }

  private void audit(String operationType, String action, Long id, String name, String operator) {
    AuditOperationHandle handle = auditService.start(new AuditOperationRequest(
        operationType, action, "APPROVAL_FLOW",
        id == null ? null : String.valueOf(id), name, "APPLICATION",
        Map.of("operator", String.valueOf(operator))));
    AuditTransactions.completeOnCommit(handle, AuditEventType.RESOURCE_UPDATED,
        action + ":" + name, Map.of("flowId", String.valueOf(id)), null);
  }
}
