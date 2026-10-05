package io.yak.ops.business.security.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.approval.api.ApprovalApi;
import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.approval.api.ApprovalInstanceView;
import io.yak.ops.business.approval.api.ApprovalSubmitCommand;
import io.yak.ops.business.security.application.AccessPolicyService;
import io.yak.ops.business.security.application.AccessPolicyApprovalSnapshot;
import io.yak.ops.business.security.exception.SecurityException;
import io.yak.ops.business.security.dao.model.DsecAccessPolicyPO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 权限申请审批接入单测(ticket 106):PENDING 分流、批准/驳回回调驱动 decideApproval、脏 payload 可见失败。 */
class AccessPolicyApprovalTest {

  private ApprovalApi approvalApi;
  private AccessPolicyService policyService;
  private AccessPolicyApprovalService service;
  private AccessGrantApprovalHandler handler;

  @BeforeEach
  void setUp() {
    approvalApi = mock(ApprovalApi.class);
    policyService = mock(AccessPolicyService.class);
    service = new AccessPolicyApprovalService(approvalApi, policyService);
    handler = new AccessGrantApprovalHandler(policyService);
  }

  @Test
  void submitBuildsCommandWithSnapshot() {
    when(policyService.get(5L)).thenReturn(policy(5L, "PENDING"));
    service.submit(5L, "tom");

    ArgumentCaptor<ApprovalSubmitCommand> captor =
        ArgumentCaptor.forClass(ApprovalSubmitCommand.class);
    verify(approvalApi).submit(captor.capture());
    ApprovalSubmitCommand cmd = captor.getValue();
    assertEquals(ApprovalFlowCodes.ACCESS_GRANT, cmd.flowCode());
    assertEquals("ACCESS_POLICY", cmd.bizType());
    assertEquals("5", cmd.bizId());
    assertTrue(cmd.title().contains("查订单表"));
    assertTrue(cmd.payloadJson().contains("\"policyId\":5"));
    assertTrue(cmd.payloadJson().contains("\"subject\":\"USER:tom\""));
    assertTrue(cmd.payloadJson().contains("\"policySnapshot\""));
    assertTrue(cmd.payloadJson().contains("order"));
    assertEquals("tom", cmd.applicant());
  }

  @Test
  void submitRejectsNonPendingPolicy() {
    when(policyService.get(5L)).thenReturn(policy(5L, "APPROVED"));
    assertThrows(SecurityException.class, () -> service.submit(5L, "tom"));
    verify(approvalApi, never()).submit(any());
  }

  @Test
  void approvedDecidesPolicyAsLastApprover() {
    handler.onApproved(decision(payloadWithSnapshot(), "同意"));
    verify(policyService).decideApproval(
        AccessPolicyApprovalSnapshot.from(policy(5L, "PENDING")), true, "carol", "同意");
    assertEquals(ApprovalFlowCodes.ACCESS_GRANT, handler.flowCode());
  }

  @Test
  void rejectedDecidesPolicyAsRejected() {
    handler.onRejected(decision(payloadWithSnapshot(), "权限过大"));
    verify(policyService).decideApproval(
        AccessPolicyApprovalSnapshot.from(policy(5L, "PENDING")), false, "carol", "权限过大");
  }

  @Test
  void canceledKeepsPolicyPending() {
    handler.onCanceled(decision("{\"policyId\":5}"));
    verify(policyService, never()).decideApproval(any(), anyBoolean(), anyString(), any());
  }

  @Test
  void malformedPayloadFailsVisibly() {
    assertThrows(IllegalStateException.class, () -> handler.onApproved(decision("{}")));
    assertThrows(IllegalStateException.class, () -> handler.onApproved(decision("not-json")));
    verify(policyService, never()).decideApproval(any(), anyBoolean(), anyString(), any());
    assertThrows(IllegalStateException.class,
        () -> handler.onApproved(decision("{\"policySnapshot\":{\"policyId\":5}}")));
  }

  private static DsecAccessPolicyPO policy(Long id, String status) {
    DsecAccessPolicyPO po = new DsecAccessPolicyPO();
    po.setId(id);
    po.setProjectId(1L);
    po.setPolicyName("查订单表");
    po.setSubjectType("USER");
    po.setSubjectKey("tom");
    po.setScopeType("TABLE");
    po.setDbName("app_db");
    po.setTableName("orders");
    po.setAccessType("READ");
    po.setEffect("ALLOW");
    po.setStatus(status);
    po.setApplicant("tom");
    return po;
  }

  private static ApprovalDecision decision(String payloadJson) {
    return decision(payloadJson, "同意");
  }

  private static ApprovalDecision decision(String payloadJson, String comment) {
    return new ApprovalDecision(9L, ApprovalFlowCodes.ACCESS_GRANT, "ACCESS_POLICY", "5",
        payloadJson, "tom", "carol", comment, LocalDateTime.now());
  }

  private static String payloadWithSnapshot() {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
          java.util.Map.of("policyId", 5L,
              "policySnapshot", AccessPolicyApprovalSnapshot.from(policy(5L, "PENDING"))));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
