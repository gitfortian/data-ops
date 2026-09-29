package io.yak.ops.business.approval.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.approval.api.ApprovalDecision;
import io.yak.ops.business.approval.api.ApprovalFlowHandler;
import io.yak.ops.business.approval.exception.ApprovalException;
import io.yak.ops.common.enums.approval.ApprovalErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** 注册表单测:按 flowCode 聚合、重复注册启动失败、require 缺 handler → 49007。 */
class ApprovalFlowRegistryTest {

  @SuppressWarnings("unchecked")
  private static ObjectProvider<ApprovalFlowHandler> providerOf(ApprovalFlowHandler... handlers) {
    ObjectProvider<ApprovalFlowHandler> provider = mock(ObjectProvider.class);
    when(provider.stream()).thenReturn(Stream.of(handlers));
    return provider;
  }

  private static ApprovalFlowHandler handler(String flowCode) {
    return new ApprovalFlowHandler() {
      @Override
      public String flowCode() {
        return flowCode;
      }

      @Override
      public void onApproved(ApprovalDecision decision) {}
    };
  }

  @Test
  void indexesHandlersByFlowCode() {
    ApprovalFlowRegistry registry =
        new ApprovalFlowRegistry(providerOf(handler("MODEL_PUBLISH"), handler("STANDARD_PUBLISH")));

    assertEquals("MODEL_PUBLISH", registry.require("MODEL_PUBLISH").flowCode());
    assertTrue(registry.find("UNKNOWN").isEmpty());
  }

  @Test
  void duplicateFlowCodeFailsAtStartup() {
    assertThrows(IllegalStateException.class, () -> new ApprovalFlowRegistry(
        providerOf(handler("MODEL_PUBLISH"), handler("MODEL_PUBLISH"))));
  }

  @Test
  void requireThrows49007WhenMissing() {
    ApprovalFlowRegistry registry = new ApprovalFlowRegistry(providerOf(handler("A")));

    ApprovalException ex =
        assertThrows(ApprovalException.class, () -> registry.require("B"));
    assertEquals(ApprovalErrorCode.HANDLER_NOT_REGISTERED, ex.getErrorCode());
    assertTrue(ex.getUserMessage().contains("B"));
  }
}
