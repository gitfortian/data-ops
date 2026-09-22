package io.yak.ops.business.agent.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.yak.ops.business.agent.runtime.AgentObservationCollector;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * O3 Guard 观测可证伪验收：守卫拒绝标记（工具错误输出或 flux 错误预览）必须额外落
 * GUARD span（含 toolCallId join 键与工具入参），同时既有 TOOL_CALL 记账不受影响。
 */
class GuardObservationTest {

  private final AgentObservationCollector collector = mock(AgentObservationCollector.class);
  private final TurnCorrelation correlation = sessionId -> "t1";

  private void run(ToolUseBlock call, Flux<AgentEvent> upstream) throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    new ToolAuditMiddleware(collector, correlation)
        .onActing(
            null,
            RuntimeContext.builder().userId("42").sessionId("s1").build(),
            new ActingInput(List.of(call)),
            input -> upstream)
        .subscribe(e -> {}, e -> done.countDown(), done::countDown);
    org.junit.jupiter.api.Assertions.assertTrue(done.await(10, TimeUnit.SECONDS));
  }

  @Test
  void guardMarkerInToolOutputEmitsGuardSpan() throws Exception {
    run(ToolUseBlock.builder().id("call_1").name("run_dataset_query").build(),
        Flux.just(
            new ToolResultTextDeltaEvent("r1", "call_1", "run_dataset_query",
                "{\"success\":false,\"error\":\"[GUARD_REJECTED] SCHEMA_VIOLATION: 字段不在白名单\"}"),
            new ToolResultEndEvent("r1", "call_1", "run_dataset_query", ToolResultState.ERROR)));

    verify(collector, timeout(3000)).guardRejected(
        eq("s1"), eq("t1"), eq("call_1"), eq("run_dataset_query"), any(),
        contains("[GUARD_REJECTED]"));
    // 既有 TOOL_CALL 记账不受影响
    verify(collector, timeout(3000)).toolCall(
        eq("s1"), eq("t1"), eq("call_1"), eq("run_dataset_query"), eq(false),
        anyLong(), anyLong(), any(), any(), any(), any());
  }

  @Test
  void noGuardMarkerEmitsNoGuardSpan() throws Exception {
    run(ToolUseBlock.builder().id("call_1").name("list_datasets").build(),
        Flux.just(
            new ToolResultTextDeltaEvent("r1", "call_1", "list_datasets", "{\"rows\":[1]}"),
            new ToolResultEndEvent("r1", "call_1", "list_datasets", ToolResultState.SUCCESS)));

    verify(collector, timeout(3000)).toolCall(
        eq("s1"), eq("t1"), eq("call_1"), eq("list_datasets"), eq(true),
        anyLong(), anyLong(), any(), any(), any(), any());
    verify(collector, never()).guardRejected(
        any(), any(), any(), any(), any(), any());
  }
}
