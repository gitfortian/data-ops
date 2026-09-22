package io.yak.ops.business.asset.approval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowCodes;
import io.yak.ops.business.asset.application.AssetLifecycleService;
import io.yak.ops.business.asset.application.PrecheckTokenService;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 回调单测(M2-5):批准=服务端自签 token 走既有 publish(不代认风险);脏 payload 可见失败;拒绝不动作。 */
class AssetPublishApprovalHandlerTest {

  private AssetLifecycleService lifecycleService;
  private AssetPublishApprovalHandler handler;

  @BeforeEach
  void setUp() {
    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(1L);
    PrecheckTokenService tokenService = mock(PrecheckTokenService.class);
    when(tokenService.issue(any(), anyList())).thenReturn("issued-token");
    lifecycleService = mock(AssetLifecycleService.class);
    when(lifecycleService.publish(anyList(), anyString(), anyBoolean(), anyString()))
        .thenReturn(1);
    handler = new AssetPublishApprovalHandler(currentProject, tokenService, lifecycleService);
  }

  @Test
  void approvedPublishesAsApplicantWithoutRiskAcceptance() {
    handler.onApproved(decision("{\"assetId\":42,\"assetName\":\"订单表\"}"));

    verify(lifecycleService).publish(List.of(42L), "issued-token", false, "tom");
    assertEquals(ApprovalFlowCodes.ASSET_PUBLISH, handler.flowCode());
  }

  @Test
  void malformedPayloadFailsVisibly() {
    assertThrows(IllegalStateException.class, () -> handler.onApproved(decision("{}")));
    assertThrows(IllegalStateException.class, () -> handler.onApproved(decision("not-json")));
    verify(lifecycleService, never()).publish(any(), any(), anyBoolean(), any());
  }

  @Test
  void rejectedAndCanceledDoNotPublish() {
    handler.onRejected(decision("{\"assetId\":42}"));
    handler.onCanceled(decision("{\"assetId\":42}"));
    verify(lifecycleService, never()).publish(any(), any(), anyBoolean(), any());
  }

  private static ApprovalDecision decision(String payloadJson) {
    return new ApprovalDecision(7L, ApprovalFlowCodes.ASSET_PUBLISH, "ASSET", "42",
        payloadJson, "tom", "carol", "LGTM", LocalDateTime.now());
  }
}
