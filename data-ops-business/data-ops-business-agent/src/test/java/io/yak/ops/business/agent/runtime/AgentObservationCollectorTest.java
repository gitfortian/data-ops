package io.yak.ops.business.agent.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.telemetry.AgentStepRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 采集统一入口可证伪验收：摘要指纹档、工具入参落库、治理开关（O4）与排障升档。 */
class AgentObservationCollectorTest {

  private final AgentStepRecorder recorder = mock(AgentStepRecorder.class);
  private final AgentProperties properties = new AgentProperties();
  private final io.yak.ops.business.agent.repository.AgentDynamicConfigService dynamicConfig =
      new io.yak.ops.business.agent.repository.AgentDynamicConfigService(
          mock(io.yak.ops.business.agent.dao.mapper.AgentConfigMapper.class));
  private final AgentObservationCollector collector =
      new AgentObservationCollector(recorder, properties, dynamicConfig);

  @Test
  void llmAttemptCarriesSummaryHashRequestEnvelope() {
    collector.llmCallAttempt("s1", "t1", "spike", true, 120L, 1000L,
        11, 7, 1, null, null, 3, "USER:你好\nASSISTANT:在", "你好");

    ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
    verify(recorder).recordLlmCallAttempt(
        eq("s1"), eq("t1"), eq("spike"), eq(true), eq(120L), eq(1000L),
        eq(11), eq(7), eq(1), any(), any(), request.capture());
    String stored = request.getValue();
    org.junit.jupiter.api.Assertions.assertTrue(
        stored.contains("SUMMARY_HASH"), "请求载荷必须是摘要指纹档：" + stored);
    org.junit.jupiter.api.Assertions.assertFalse(
        stored.contains("ASSISTANT"), "原文不得落库：" + stored);
    org.junit.jupiter.api.Assertions.assertTrue(stored.contains("你好"), "末条用户消息预览保留");
  }

  @Test
  void toolCallPersistsRequestPayloadThatUsedToBeDropped() {
    collector.toolCall("s1", "t1", "call_1", "run_dataset_query", true, 88L, 2000L,
        "{\"datasetId\":7}", "{\"rows\":[]}", null, null);

    ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
    verify(recorder).recordToolCallSpan(
        eq("s1"), eq("t1"), eq("call_1"), eq("run_dataset_query"), eq(true), eq(88L),
        eq(2000L), request.capture(), response.capture(), any(), any());
    org.junit.jupiter.api.Assertions.assertTrue(
        request.getValue().contains("datasetId"), "入参必须落库（O1 修复死字段）：" + request.getValue());
    org.junit.jupiter.api.Assertions.assertTrue(
        response.getValue().contains("rows"), "输出必须落库");
  }

  @Test
  void unregisteredKindIsRejectedQuietly() {
    collector.completedEvent("s1", "t1", "MYSTERY", "x", null, null);
    verify(recorder, never()).recordCompleted(
        anyString(), anyString(), contains("MYSTERY"), anyString(), anyString(), anyString());
  }

  @Test
  void globalSwitchOffMeansZeroWrites() {
    properties.getObservability().setEnabled(false);

    collector.llmCallAttempt("s1", "t1", "spike", true, 1L, 1L, 0, 0, 1, null, null, 1, "q", "q");
    collector.toolCall("s1", "t1", "c1", "tool", true, 1L, 1L, "{}", "{}", null, null);
    collector.completedEvent("s1", "t1", "TURN_SUMMARY", "turn", null, "{}");
    collector.event("s1", "t1", "HITL", "clarify", "c1", "COMPLETED", "问", null, null, null);

    verifyNoInteractions(recorder);
  }

  @Test
  void perKindSwitchDisablesOnlyThatKind() {
    properties.getObservability().getKindEnabled().put("TOOL_CALL", false);

    collector.toolCall("s1", "t1", "c1", "tool", true, 1L, 1L, "{}", "{}", null, null);
    collector.completedEvent("s1", "t1", "TURN_SUMMARY", "turn", null, "{}");

    verify(recorder, never()).recordToolCallSpan(
        anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
        anyLong(), anyLong(), any(), any(), any(), any());
    verify(recorder).recordCompleted(
        eq("s1"), eq("t1"), eq("TURN_SUMMARY"), eq("turn"), any(), any());
  }

  @Test
  void inlineDebugUpgradeStoresRawRequestWithinBudget() {
    properties.getObservability().setLlmRequestInlineDebug(true);

    collector.llmCallAttempt("s1", "t1", "spike", true, 1L, 1L, 0, 0, 1, null, null,
        1, "USER:完整原文问题", "完整原文问题");

    ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
    verify(recorder).recordLlmCallAttempt(
        any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), anyLong(), anyLong(),
        anyInt(), anyInt(), anyInt(), any(), any(), request.capture());
    org.junit.jupiter.api.Assertions.assertTrue(
        request.getValue().contains("\"mode\":\"INLINE\""), "debug 升档走 INLINE 档");
    org.junit.jupiter.api.Assertions.assertTrue(
        request.getValue().contains("完整原文问题"), "升档期间原文可见");
  }

  @Test
  void maxInlineCharsIsHonoredDynamically() {
    properties.getObservability().setMaxInlineChars(64);
    String bigOutput = "{\"rows\":\"" + "x".repeat(500) + "\"}";

    collector.toolCall("s1", "t1", "c1", "tool", true, 1L, 1L, null, bigOutput, null, null);

    ArgumentCaptor<String> response = ArgumentCaptor.forClass(String.class);
    verify(recorder).recordToolCallSpan(
        any(), any(), eq("c1"), eq("tool"), eq(true), anyLong(), anyLong(),
        any(), response.capture(), any(), any());
    org.junit.jupiter.api.Assertions.assertTrue(
        response.getValue().contains("\"truncated\":true"), "超限必须标记截断");
    org.junit.jupiter.api.Assertions.assertTrue(
        response.getValue().length() < 512, "按治理上限收缩");
  }
}
