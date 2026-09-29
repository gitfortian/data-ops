package io.yak.ops.business.mdm.approval;

import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.mdm.application.MdmChangeEffectService;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MDM_CHANGE 终态回调(R4,onApproved = 业务生效点,与审批同事务 D5):
 * bizId 即 {@code yak_mdm_change.id},生效/落版快照/状态流转复用
 * {@link MdmChangeEffectService}(只依赖仓储,避免 bean 循环);
 * 抛错 → 49009 整体回滚,审批动作失败可见,由审批人重试(快照与状态更新均幂等)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MdmChangeApprovalHandler implements ApprovalFlowHandler {

  private final MdmChangeEffectService effectService;

  @Override
  public String flowCode() {
    return ApprovalFlowCodes.MDM_CHANGE;
  }

  @Override
  public void onApproved(ApprovalDecision decision) {
    effectService.applyApproved(
        changeId(decision), decision.lastApprover(), decision.comment(), decision.at());
  }

  @Override
  public void onRejected(ApprovalDecision decision) {
    effectService.applyRejected(
        changeId(decision), decision.lastApprover(), decision.comment(), decision.at());
  }

  @Override
  public void onCanceled(ApprovalDecision decision) {
    effectService.applyCanceled(changeId(decision), decision.applicant(), decision.comment());
  }

  private static long changeId(ApprovalDecision decision) {
    try {
      return Long.parseLong(decision.bizId());
    } catch (NumberFormatException exception) {
      throw new MdmException(
          MdmErrorCode.APPROVAL_FAILED, "审批回调 bizId 非法: " + decision.bizId());
    }
  }
}
