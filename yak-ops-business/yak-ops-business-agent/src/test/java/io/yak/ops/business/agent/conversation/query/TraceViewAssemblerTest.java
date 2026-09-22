package io.yak.ops.business.agent.conversation.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.agent.domain.AgentStepRecord;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.KindAggregate;
import io.yak.ops.business.agent.domain.SpanNode;
import io.yak.ops.business.agent.domain.StepPayload;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.domain.TurnTraceView;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * trace v2 组装器可证伪验收：parent 链树组装、断链显式化（I9）、kind 聚合数学、
 * 失败语义归一（§7.4）、存量裸文本载荷兼容。
 */
class TraceViewAssemblerTest {

  private final TraceViewAssembler assembler = new TraceViewAssembler();

  private static final LocalDateTime T0 = LocalDateTime.of(2026, 8, 29, 10, 0, 0);

  private static AgentTurnRecord turn() {
    // AgentTurnRecord(turnId, sessionId, userId, projectId, kind, payloadJson, status, errorCode, errorMessage, createTime, startTime, endTime)
    return new AgentTurnRecord("t1", "s1", 7L, 1L, io.yak.ops.business.agent.domain.TurnKind.START,
        null, TurnStatus.COMPLETED, null, null, T0, T0, T0.plusSeconds(30));
  }

  private static AgentStepRecord step(long id, String kind, String name, Long parentId,
      String toolCallId, String status, String request, String response, String stats,
      LocalDateTime startedAt, LocalDateTime endedAt, Integer attempt) {
    return new AgentStepRecord(id, "t1", parentId, kind, name, toolCallId, status,
        request, response, null, null, stats, startedAt, endedAt, attempt, T0.plusSeconds(1));
  }

  @Test
  void parentChainAssemblesTreeWithToolUnderLlm() {
    AgentStepRecord llm = step(1, "LLM_CALL", "spike", null, null, "COMPLETED",
        null, null, "{\"promptTokens\":11,\"completionTokens\":7,\"latencyMs\":120}", null, null, 1);
    AgentStepRecord tool = step(2, "TOOL_CALL", "run_dataset_query", 1L, "call_1", "COMPLETED",
        null, null, "{\"latencyMs\":88}", null, null, null);
    AgentStepRecord summary = step(3, "TURN_SUMMARY", "turn", null, null, "COMPLETED",
        null, null, "{\"totalTokens\":18}", null, null, null);

    TurnTraceView view = assembler.assembleTurn(turn(), List.of(llm, tool, summary));

    assertTrue(view.completeness().complete(), "完整链路不得报断链：" + view.completeness().reasons());
    assertEquals(2, view.tree().size(), "根节点=LLM_CALL+TURN_SUMMARY");
    SpanNode llmNode = view.tree().get(0);
    assertEquals(1, llmNode.children().size());
    assertEquals("run_dataset_query", llmNode.children().get(0).name());
    assertEquals(18L, view.totalTokens());
    assertEquals(Integer.valueOf(1), llmNode.attempt());
    assertEquals(Long.valueOf(120L), llmNode.durationMillis());
  }

  @Test
  void orphanToolCallIsExplicitIncompletenessNotSilent() {
    AgentStepRecord tool = step(9, "TOOL_CALL", "list_datasets", 999L, "call_x", "COMPLETED",
        null, null, "{\"latencyMs\":5}", null, null, null);

    TurnTraceView view = assembler.assembleTurn(turn(), List.of(tool));

    assertFalse(view.completeness().complete());
    assertTrue(view.completeness().reasons().contains("orphan_tool_call:call_x"), "断链原因必须显式");
    assertTrue(view.completeness().reasons().contains("missing_turn_summary"));
  }

  @Test
  void aggregatesMathMatchesManualComputation() {
    AgentStepRecord t1 = step(1, "TOOL_CALL", "a", null, "c1", "COMPLETED",
        null, null, "{\"latencyMs\":100}", null, null, null);
    AgentStepRecord t2 = step(2, "TOOL_CALL", "b", null, "c2", "COMPLETED",
        null, null, "{\"latencyMs\":200}", null, null, null);
    AgentStepRecord llm = step(3, "LLM_CALL", "spike", null, null, "COMPLETED",
        null, null, "{\"promptTokens\":10,\"completionTokens\":5,\"latencyMs\":1000}", null, null, 1);

    TurnTraceView view = assembler.assembleTurn(turn(), List.of(t1, t2, llm));

    KindAggregate toolAgg = view.aggregates().stream()
        .filter(a -> "TOOL_CALL".equals(a.kind())).findFirst().orElseThrow();
    assertEquals(2, toolAgg.count());
    assertEquals(300L, toolAgg.totalMillis());
    assertEquals(200L, toolAgg.maxMillis());
    assertEquals(150L, toolAgg.avgMillis());
    assertEquals(200L, toolAgg.p95Millis(), "两样本 p95 = 最大值");
    KindAggregate llmAgg = view.aggregates().stream()
        .filter(a -> "LLM_CALL".equals(a.kind())).findFirst().orElseThrow();
    assertEquals(Long.valueOf(10L), llmAgg.promptTokens());
    assertEquals(Long.valueOf(5L), llmAgg.completionTokens());
  }

  @Test
  void v9ColumnsTakePrecedenceOverStatsJsonForDuration() {
    AgentStepRecord tool = step(1, "TOOL_CALL", "a", null, "c1", "COMPLETED",
        null, null, "{\"latencyMs\":999}",
        T0.plusSeconds(1), T0.plusSeconds(2), null);

    TurnTraceView view = assembler.assembleTurn(turn(), List.of(tool));

    assertEquals(Long.valueOf(1000L), view.spans().get(0).durationMillis(), "升列优先于 stats_json");
    assertEquals(Long.valueOf(1000L), view.timeline().get(0).offsetMillis(), "offset 以 started_at 精确计算");
  }

  @Test
  void failedToolWithBusinessErrorStructureSurfacesRealCause() {
    String response = "{\"mode\":\"INLINE\",\"truncated\":false,\"size\":52,"
        + "\"content\":\"{\\\"success\\\":false,\\\"error\\\":\\\"字段 refund_status 不在白名单\\\"}\"}";
    AgentStepRecord tool = step(1, "TOOL_CALL", "run_dataset_query", null, "c1", "FAILED",
        null, response, "{\"latencyMs\":40}", null, null, null);

    TurnTraceView view = assembler.assembleTurn(turn(), List.of(tool));

    assertEquals("字段 refund_status 不在白名单", view.spans().get(0).failureDetail(),
        "失败详情应取业务错误结构而非模板句");
  }

  @Test
  void legacyRawPayloadRowsDecodeAsInlineContent() {
    // O1 前的存量行：request/response 是裸文本（非信封 JSON）
    AgentStepRecord tool = step(1, "TOOL_CALL", "a", null, "c1", "COMPLETED",
        "{\"datasetId\":7}", "{\"rows\":[1,2]}", "{\"latencyMs\":10}", null, null, null);

    TurnTraceView view = assembler.assembleTurn(turn(), List.of(tool));

    StepPayload request = view.spans().get(0).request();
    assertEquals(StepPayload.MODE_INLINE, request.mode());
    assertEquals("{\"datasetId\":7}", request.content());
    assertNull(request.truncated(), "存量行截断事实不可考");
  }

  @Test
  void kindsManifestIsRegistryProjectionSortedByOrder() {
    TurnTraceView view = assembler.assembleTurn(turn(), List.of());

    assertTrue(view.completeness().reasons().contains("empty_steps"));
    assertTrue(view.kinds().size() >= 9, "注册表全量投影");
    assertTrue(view.kinds().stream().allMatch(k -> k.title() != null && k.color() != null));
  }
}
