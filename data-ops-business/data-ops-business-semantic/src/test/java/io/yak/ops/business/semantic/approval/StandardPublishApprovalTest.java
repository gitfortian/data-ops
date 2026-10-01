package io.yak.ops.business.semantic.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.catalog.StandardCatalogService;
import io.yak.ops.business.semantic.api.StandardKind;
import io.yak.ops.business.semantic.api.StandardStatus;
import io.yak.ops.business.semantic.exception.SemanticException;
import io.yak.ops.common.enums.semantic.SemanticErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 标准生效审批接入单测(ticket 105):命令组装、批准后按发起人启用、脏 payload 可见失败。 */
class StandardPublishApprovalTest {

  private ApprovalApi approvalApi;
  private StandardCatalogService catalogService;
  private StandardPublishApprovalService service;
  private StandardPublishApprovalHandler handler;

  @BeforeEach
  void setUp() {
    approvalApi = mock(ApprovalApi.class);
    catalogService = mock(StandardCatalogService.class);
    service = new StandardPublishApprovalService(approvalApi, catalogService);
    handler = new StandardPublishApprovalHandler(catalogService);
  }

  @Test
  void submitBuildsCommandWithSnapshot() {
    when(catalogService.lockDefinition(88L)).thenReturn(standard(88L, "order_status", "订单状态"));
    when(approvalApi.submit(any())).thenReturn(new ApprovalInstanceView(
        7L, ApprovalFlowCodes.STANDARD_PUBLISH, "标准发布", "STANDARD", "88", "t", null,
        "tom", "PENDING", 1, LocalDateTime.now(), null));

    service.submit(88L, "tom");

    ArgumentCaptor<ApprovalSubmitCommand> captor =
        ArgumentCaptor.forClass(ApprovalSubmitCommand.class);
    verify(approvalApi).submit(captor.capture());
    ApprovalSubmitCommand cmd = captor.getValue();
    assertEquals(ApprovalFlowCodes.STANDARD_PUBLISH, cmd.flowCode());
    assertEquals("STANDARD", cmd.bizType());
    assertEquals("88", cmd.bizId());
    assertTrue(cmd.title().contains("订单状态"));
    assertTrue(cmd.payloadJson().contains("\"standardId\":88"));
    assertEquals("tom", cmd.applicant());
    assertTrue(cmd.payloadJson().contains("\"standardVersion\":1"));
    assertTrue(cmd.payloadJson().contains("\"definition\":"));
  }

  @Test
  void submitPropagatesNotFoundFromCatalog() {
    when(catalogService.lockDefinition(404L))
        .thenThrow(new SemanticException(SemanticErrorCode.NOT_FOUND, "404"));
    assertThrows(SemanticException.class, () -> service.submit(404L, "tom"));
    verify(approvalApi, never()).submit(any());
  }

  @Test
  void submitRejectsAlreadyEnabledStandard() {
    when(catalogService.lockDefinition(88L)).thenReturn(standard(88L, StandardStatus.ENABLED));
    SemanticException exception = assertThrows(SemanticException.class, () -> service.submit(88L, "tom"));
    assertEquals(SemanticErrorCode.PUBLISH_ALREADY_ENABLED, exception.getErrorCode());
    verify(approvalApi, never()).submit(any());
  }

  @Test
  void approvedEnablesAsOriginalApplicant() {
    when(catalogService.lockDefinition(88L)).thenReturn(standard(88L, StandardStatus.DISABLED));
    handler.onApproved(decision("{\"standardId\":88,\"standardVersion\":1}"));
    verify(catalogService).enableAfterApproval(88L, "tom");
    assertEquals(ApprovalFlowCodes.STANDARD_PUBLISH, handler.flowCode());
  }

  @Test
  void malformedPayloadFailsVisibly() {
    assertThrows(IllegalStateException.class, () -> handler.onApproved(decision("{}")));
    assertThrows(IllegalStateException.class, () -> handler.onApproved(decision("not-json")));
    verify(catalogService, never()).changeStatus(anyLong(), anyString(), anyString());
  }

  @Test
  void rejectedAndCanceledDoNotEnable() {
    handler.onRejected(decision("{\"standardId\":88}"));
    handler.onCanceled(decision("{\"standardId\":88}"));
    verify(catalogService, never()).changeStatus(any(), any(), any());
  }

  private static Standard standard(Long id, String code, String name) {
    return new Standard(id, StandardKind.UNIT, code, name, StandardStatus.DISABLED, 1, 0,
        false, null, null, "tester", null, null);
  }

  private static Standard standard(Long id, StandardStatus status) {
    return new Standard(id, StandardKind.UNIT, "order_status", "订单状态", status, 1, 0,
        false, null, null, "tester", null, null);
  }

  private static ApprovalDecision decision(String payloadJson) {
    return new ApprovalDecision(7L, ApprovalFlowCodes.STANDARD_PUBLISH, "STANDARD", "88",
        payloadJson, "tom", "carol", "同意", LocalDateTime.now());
  }
}
