package io.yak.ops.business.security.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.security.application.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ACCESS_GRANT 终态回调:批准/驳回即执行既有 decideApproval(同事务 D5)。
 * 撤销不回调生效:策略保持 PENDING,申请人可修改后重新提交审批。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessGrantApprovalHandler implements ApprovalFlowHandler {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final AccessPolicyService policyService;

  @Override
  public String flowCode() {
    return ApprovalFlowCodes.ACCESS_GRANT;
  }

  @Override
  public void onApproved(ApprovalDecision decision) {
    long policyId = readPolicyId(decision.payloadJson());
    policyService.decideApproval(policyId, true, decision.lastApprover(), decision.comment());
    log.info("权限申请审批通过即授权: policyId={}, approver={}", policyId, decision.lastApprover());
  }

  @Override
  public void onRejected(ApprovalDecision decision) {
    long policyId = readPolicyId(decision.payloadJson());
    policyService.decideApproval(policyId, false, decision.lastApprover(), decision.comment());
    log.info("权限申请审批拒绝即驳回: policyId={}, approver={}", policyId, decision.lastApprover());
  }

  private static long readPolicyId(String payloadJson) {
    try {
      JsonNode node = MAPPER.readTree(payloadJson)
          .path(AccessPolicyApprovalService.PAYLOAD_POLICY_ID);
      if (!node.canConvertToLong()) {
        throw new IllegalStateException("payload 缺少 policyId: " + payloadJson);
      }
      return node.asLong();
    } catch (Exception e) {
      throw new IllegalStateException("审批 payload 解析失败:" + e.getMessage(), e);
    }
  }
}
