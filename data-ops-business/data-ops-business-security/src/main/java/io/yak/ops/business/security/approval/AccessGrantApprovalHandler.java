package io.yak.ops.business.security.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.security.application.AccessPolicyService;
import io.yak.ops.business.security.application.AccessPolicyApprovalSnapshot;
import java.util.List;
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
  private static final List<String> SNAPSHOT_FIELDS = List.of(
      "policyId", "policyName", "subjectType", "subjectKey", "scopeType", "datasourceId",
      "dbName", "tableName", "columnName", "levelId", "accessType", "effect", "priority",
      "validFrom", "validTo");

  private final AccessPolicyService policyService;

  @Override
  public String flowCode() {
    return ApprovalFlowCodes.ACCESS_GRANT;
  }

  @Override
  public void onApproved(ApprovalDecision decision) {
    AccessPolicyApprovalSnapshot snapshot = readSnapshot(decision.payloadJson());
    policyService.decideApproval(snapshot, true, decision.lastApprover(), decision.comment());
    log.info("权限申请审批通过即授权: policyId={}, approver={}",
        snapshot.policyId(), decision.lastApprover());
  }

  @Override
  public void onRejected(ApprovalDecision decision) {
    AccessPolicyApprovalSnapshot snapshot = readSnapshot(decision.payloadJson());
    policyService.decideApproval(snapshot, false, decision.lastApprover(), decision.comment());
    log.info("权限申请审批拒绝即驳回: policyId={}, approver={}",
        snapshot.policyId(), decision.lastApprover());
  }

  private static AccessPolicyApprovalSnapshot readSnapshot(String payloadJson) {
    try {
      JsonNode snapshot = MAPPER.readTree(payloadJson)
          .path(AccessPolicyApprovalService.PAYLOAD_POLICY_SNAPSHOT);
      if (!snapshot.isObject()) {
        throw new IllegalStateException("payload 缺少 policySnapshot");
      }
      if (SNAPSHOT_FIELDS.stream().anyMatch(field -> !snapshot.has(field))) {
        throw new IllegalStateException("policySnapshot 字段不完整");
      }
      AccessPolicyApprovalSnapshot result = MAPPER.treeToValue(
          snapshot, AccessPolicyApprovalSnapshot.class);
      if (result.policyId() == null) {
        throw new IllegalStateException("policySnapshot 缺少 policyId");
      }
      return result;
    } catch (Exception e) {
      throw new IllegalStateException("审批送审依据解析失败:" + e.getMessage(), e);
    }
  }
}
