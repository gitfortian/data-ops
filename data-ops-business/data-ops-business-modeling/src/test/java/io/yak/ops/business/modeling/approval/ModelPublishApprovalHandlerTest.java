package io.yak.ops.business.modeling.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.modeling.domain.ModelVersion;
import io.yak.ops.business.modeling.version.ModelVersionService;
import io.yak.ops.business.modeling.version.ModelVersionService.PublishResult;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 回调单测(ticket 104):批准后按发起人执行发布;payload 脏数据可见失败;拒绝不动作。 */
class ModelPublishApprovalHandlerTest {

  private ModelVersionService versionService;
  private ModelPublishApprovalHandler handler;

  @BeforeEach
  void setUp() {
    versionService = mock(ModelVersionService.class);
    handler = new ModelPublishApprovalHandler(versionService);
  }

  @Test
  void approvedPublishesAsOriginalApplicant() {
    String fingerprint = "a".repeat(64);
    when(versionService.publishApproved(42L, fingerprint, "tom")).thenReturn(new PublishResult(
        new ModelVersion(9L, 42L, 3, "{}", null, 5, "sum", "tom", LocalDateTime.now()), true));

    handler.onApproved(decision("{\"modelId\":42,\"structureFingerprint\":\"" + fingerprint + "\"}"));

    verify(versionService).publishApproved(42L, fingerprint, "tom");
    assertEquals(ApprovalFlowCodes.MODEL_PUBLISH, handler.flowCode());
  }

  @Test
  void malformedPayloadFailsVisibly() {
    assertThrows(IllegalStateException.class, () -> handler.onApproved(decision("{}")));
    assertThrows(IllegalStateException.class, () -> handler.onApproved(decision("not-json")));
    verify(versionService, never()).publishApproved(anyLong(), anyString(), anyString());
  }

  @Test
  void rejectedAndCanceledDoNotPublish() {
    handler.onRejected(decision("{\"modelId\":42}"));
    handler.onCanceled(decision("{\"modelId\":42}"));
    verify(versionService, never()).publishApproved(anyLong(), anyString(), anyString());
  }

  private static ApprovalDecision decision(String payloadJson) {
    return new ApprovalDecision(7L, ApprovalFlowCodes.MODEL_PUBLISH, "MODEL", "42",
        payloadJson, "tom", "carol", "LGTM", LocalDateTime.now());
  }
}
