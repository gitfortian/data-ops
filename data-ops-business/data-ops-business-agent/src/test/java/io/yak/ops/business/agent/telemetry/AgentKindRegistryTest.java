package io.yak.ops.business.agent.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 观测类型注册表可证伪验收：注册即准入、未注册拒绝、记忆线 kind 已预留接入。 */
class AgentKindRegistryTest {

  @Test
  void firstBatchKindsAreRegistered() {
    for (String kind : new String[] {
        AgentKindRegistry.KIND_LLM_CALL,
        AgentKindRegistry.KIND_TOOL_CALL,
        AgentKindRegistry.KIND_GUARD,
        AgentKindRegistry.KIND_HITL,
        AgentKindRegistry.KIND_COMPACTION,
        AgentKindRegistry.KIND_TURN_SUMMARY,
        AgentKindRegistry.KIND_MEMORY_FLUSH,
        AgentKindRegistry.KIND_MEMORY_RECALL,
        AgentKindRegistry.KIND_MEMORY_CONSOLIDATE}) {
      assertTrue(AgentKindRegistry.isRegistered(kind), "应已注册：" + kind);
    }
  }

  @Test
  void unregisteredKindIsRejectedBeforePersistence() {
    assertThrows(IllegalArgumentException.class, () -> AgentKindRegistry.require("MYSTERY_KIND"));
    assertFalse(AgentKindRegistry.isRegistered("MYSTERY_KIND"));
    assertFalse(AgentKindRegistry.isRegistered(null));
  }

  @Test
  void toolCallFollowsLastLlmParentRuleWithInlinePayload() {
    KindSpec spec = AgentKindRegistry.require(AgentKindRegistry.KIND_TOOL_CALL);
    assertEquals(KindSpec.ParentRule.LAST_LLM, spec.parentRule());
    assertEquals(KindSpec.PayloadMode.INLINE, spec.requestMode());
    assertEquals(KindSpec.PayloadMode.INLINE, spec.responseMode());
  }

  @Test
  void llmCallRequestIsSummaryHashWithoutRawContent() {
    KindSpec spec = AgentKindRegistry.require(AgentKindRegistry.KIND_LLM_CALL);
    assertEquals(KindSpec.PayloadMode.SUMMARY_HASH, spec.requestMode());
    assertEquals(KindSpec.PayloadMode.NONE, spec.responseMode());
  }
}
