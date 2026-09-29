package io.yak.ops.business.mdm.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.mdm.application.MdmChangeEffectService;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** MDM_CHANGE 终态回调薄测试:flowCode 注册 + bizId 解析转生效服务。 */
class MdmChangeApprovalHandlerTest {

  private MdmChangeEffectService effectService;
  private MdmChangeApprovalHandler handler;

  @BeforeEach
  void setUp() {
    effectService = mock(MdmChangeEffectService.class);
    handler = new MdmChangeApprovalHandler(effectService);
  }

  private static ApprovalDecision decision(String bizId) {
    return new ApprovalDecision(
        77L, ApprovalFlowCodes.MDM_CHANGE, "MDM_CHANGE", bizId,
        "{\"changeId\":10}", "tester", "admin", "同意",
        LocalDateTime.of(2026, 9, 20, 10, 0));
  }

  @Test
  void registersMdmChangeFlowCode() {
    assertEquals(ApprovalFlowCodes.MDM_CHANGE, handler.flowCode());
  }

  @Test
  void onApprovedDelegatesWithDecisionContext() {
    handler.onApproved(decision("10"));

    verify(effectService).applyApproved(
        10L, "admin", "同意", LocalDateTime.of(2026, 9, 20, 10, 0));
  }

  @Test
  void onRejectedDelegatesToService() {
    handler.onRejected(decision("10"));

    verify(effectService).applyRejected(
        10L, "admin", "同意", LocalDateTime.of(2026, 9, 20, 10, 0));
  }

  @Test
  void onCanceledDelegatesWithApplicantAsOperator() {
    handler.onCanceled(decision("10"));

    verify(effectService).applyCanceled(10L, "tester", "同意");
  }

  @Test
  void illegalBizIdFailsCallback() {
    MdmException exception =
        assertThrows(MdmException.class, () -> handler.onApproved(decision("not-a-number")));
    assertEquals(MdmErrorCode.APPROVAL_FAILED, exception.getErrorCode());
  }
}
