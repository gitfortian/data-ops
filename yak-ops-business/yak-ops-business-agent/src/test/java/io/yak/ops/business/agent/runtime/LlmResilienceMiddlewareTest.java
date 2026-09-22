package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.extensions.model.openai.exception.OpenAIException;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import io.yak.ops.business.agent.runtime.AgentObservationCollector;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * 模型调用韧性管道可证伪验收：构造失败响应 → 自动重试且每次尝试都记账；
 * 超时与用户侧错误绝不重试；重试耗尽翻译为结构化错误码。
 * O1：记账走 {@link AgentObservationCollector#llmCallAttempt}（含请求内容观测与 attempt 升列）。
 */
class LlmResilienceMiddlewareTest {

  private static final String SESSION = "s1";
  private static final String TURN = "t1";

  private final AgentObservationCollector collector = mock(AgentObservationCollector.class);
  private final AgentDynamicConfigService dynamicConfig =
      new AgentDynamicConfigService(mock(io.yak.ops.business.agent.dao.mapper.AgentConfigMapper.class));
  private final TurnCorrelation correlation = sessionId -> TURN;

  private LlmResilienceMiddleware middleware(int callTimeoutSeconds) {
    return new LlmResilienceMiddleware(
        callTimeoutSeconds, 3, "spike", collector, correlation, dynamicConfig);
  }

  /** 脚本化 next：逐次抛出状态码错误（负数视为超时流），耗尽后发射携带用量的完成事件。 */
  private Function<ModelCallInput, Flux<AgentEvent>> scriptedNext(List<Integer> script) {
    AtomicInteger index = new AtomicInteger();
    return input -> {
      int i = index.getAndIncrement();
      if (i < script.size()) {
        int status = script.get(i);
        return status < 0
            ? Flux.error(new RuntimeException("wrapped", new TimeoutException("call window")))
            : Flux.error(new RuntimeException("http", new OpenAIException("boom", status, "spike")));
      }
      ModelCallEndEvent end = mock(ModelCallEndEvent.class);
      org.mockito.Mockito.when(end.getUsage())
          .thenReturn(ChatUsage.builder().inputTokens(11).outputTokens(7).build());
      return Flux.just((AgentEvent) end);
    };
  }

  private void run(Flux<AgentEvent> flux) throws InterruptedException {
    CountDownLatch done = new CountDownLatch(1);
    flux.subscribe(e -> {}, e -> done.countDown(), done::countDown);
    assertTrue(done.await(15, TimeUnit.SECONDS), "stream must terminate");
  }

  @Test
  void providerErrorRetriesAndEveryAttemptIsRecorded() throws Exception {
    run(middleware(5).onModelCall(null, context(), input(), scriptedNext(List.of(500))));

    // 第一次尝试失败、第二次成功：两条记账，错误码与成功标志分别正确；消息列表透传给内容观测
    verify(collector, timeout(3000)).llmCallAttempt(
        eq(SESSION), eq(TURN), eq("spike"), eq(false), anyLong(),
        anyLong(), anyInt(), anyInt(), eq(1),
        eq(LlmResilienceMiddleware.CODE_PROVIDER_ERROR), any(), any(), any(), any());
    verify(collector, timeout(3000)).llmCallAttempt(
        eq(SESSION), eq(TURN), eq("spike"), eq(true), anyLong(),
        anyLong(), anyInt(), anyInt(), eq(2), any(), any(), any(), any(), any());
  }

  @Test
  void userErrorNeverRetries() throws Exception {
    run(middleware(5).onModelCall(null, context(), input(), scriptedNext(List.of(429))));

    Thread.sleep(150); // 给潜在重试留时间窗：若发生即为断言失败
    verify(collector).llmCallAttempt(
        eq(SESSION), eq(TURN), eq("spike"), eq(false), anyLong(),
        anyLong(), anyInt(), anyInt(), eq(1),
        eq(LlmResilienceMiddleware.CODE_USER_ERROR), any(), any(), any(), any());
    verify(collector, org.mockito.Mockito.never()).llmCallAttempt(
        any(), any(), any(), eq(true), anyLong(),
        anyLong(), anyInt(), anyInt(), anyInt(), any(), any(), any(), any(), any());
  }

  @Test
  void timeoutNeverRetries() throws Exception {
    run(middleware(1).onModelCall(null, context(), input(), scriptedNext(List.of(-1))));

    Thread.sleep(200); // 超时路径不允许出现第二次尝试记账
    verify(collector).llmCallAttempt(
        eq(SESSION), eq(TURN), eq("spike"), eq(false), anyLong(),
        anyLong(), anyInt(), anyInt(), eq(1),
        eq(LlmResilienceMiddleware.CODE_TIMEOUT), any(), any(), any(), any());
  }

  private RuntimeContext context() {
    return RuntimeContext.builder().userId("42").sessionId(SESSION).build();
  }

  private ModelCallInput input() {
    return new ModelCallInput(List.of(), List.of(), null, null);
  }
}
