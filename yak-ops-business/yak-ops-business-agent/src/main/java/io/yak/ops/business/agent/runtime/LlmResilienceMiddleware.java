package io.yak.ops.business.agent.runtime;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.yak.ops.business.agent.repository.AgentDynamicConfigService;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

/**
 * 模型调用韧性管道（onModelCall 拦截点，Agent 运行时唯一模型 seam）：
 * 单次尝试硬超时——TimeoutError 不重试；401/403/429 用户侧错误直接终态；
 * 其余 HTTP 错误按状态判定受限重试。每次尝试经 {@link TurnCorrelation} 关联轮次，
 * 恰好落一条 KIND_LLM_CALL 步骤记录，失败也记账。
 *
 * <p>O1 内容观测（G5）：每次尝试把 {@link ModelCallInput#messages()} 交由
 * Collector 走 SUMMARY_HASH 档落库（消息条数/末条用户消息预览/总字符/sha256，
 * 原文不落库）；attempt/span 起止升列（V9）。</p>
 *
 * <p>由 {@code AgentRuntime} 装配（非 Spring bean）：依赖收敛在 runtime 子系统内。</p>
 */
public class LlmResilienceMiddleware implements MiddlewareBase {

  /** 分类错误码（与 step 表注释保持一致；实现收敛于 MiddlewareErrorSupport）。 */
  public static final String CODE_TIMEOUT = MiddlewareErrorSupport.CODE_TIMEOUT;
  public static final String CODE_USER_ERROR = MiddlewareErrorSupport.CODE_USER_ERROR;
  public static final String CODE_PROVIDER_ERROR = MiddlewareErrorSupport.CODE_PROVIDER_ERROR;

  /** 兜底超时（动态键 yak.agent.llm.timeout 缺省/非法时使用）。 */
  private final Duration callTimeout;
  private final int maxRetries;
  private final String modelName;
  private final AgentObservationCollector collector;
  private final TurnCorrelation turnCorrelation;
  private final AgentDynamicConfigService dynamicConfig;

  public LlmResilienceMiddleware(
      int callTimeoutSeconds,
      int maxRetries,
      String modelName,
      AgentObservationCollector collector,
      TurnCorrelation turnCorrelation,
      AgentDynamicConfigService dynamicConfig) {
    this.callTimeout =
        callTimeoutSeconds > 0 ? Duration.ofSeconds(callTimeoutSeconds) : Duration.ofSeconds(120);
    this.maxRetries = Math.max(0, maxRetries);
    this.modelName = modelName == null || modelName.isBlank() ? "llm" : modelName;
    this.collector = collector;
    this.turnCorrelation = turnCorrelation;
    this.dynamicConfig = dynamicConfig;
  }

  /**
   * 单次尝试超时：动态键 yak.agent.llm.timeout 每次尝试现读（热更即时生效），
   * 缺省/非法回落构造时种子值。
   */
  private Duration attemptTimeout() {
    int seconds = dynamicConfig.lookupInt(
        AgentDynamicConfigService.KEY_LLM_TIMEOUT_SECONDS,
        (int) callTimeout.toSeconds());
    return seconds > 0 ? Duration.ofSeconds(seconds) : callTimeout;
  }

  @Override
  public Flux<AgentEvent> onModelCall(
      io.agentscope.core.agent.Agent agent,
      RuntimeContext context,
      ModelCallInput input,
      Function<ModelCallInput, Flux<AgentEvent>> next) {
    String sessionId = context.getSessionId();
    String turnId = turnCorrelation.turnIdOf(sessionId);
    // O1 内容观测提取（runtime 侧持框架类型，telemetry 只收 neutral 字符串）
    int messageCount = input.messages() == null ? 0 : input.messages().size();
    String requestText = llmRequestText(input.messages());
    String lastUserPreview = lastUserText(input.messages());
    AtomicInteger attempts = new AtomicInteger();
    return Flux.<AgentEvent>defer(
            () -> {
              int attempt = attempts.incrementAndGet();
              long startNanos = System.nanoTime();
              long startedAtEpochMillis = System.currentTimeMillis();
              int[] usage = new int[] {0, 0};
              return next.apply(input)
                  .timeout(attemptTimeout())
                  .doOnNext(
                      event -> {
                        if (event instanceof ModelCallEndEvent end && end.getUsage() != null) {
                          usage[0] = end.getUsage().getInputTokens();
                          usage[1] = end.getUsage().getOutputTokens();
                        }
                      })
                  .doOnComplete(
                      () ->
                          collector.llmCallAttempt(
                              sessionId, turnId, modelName, true, MiddlewareErrorSupport.elapsed(startNanos),
                              startedAtEpochMillis,
                              usage[0], usage[1], attempt, null, null,
                              messageCount, requestText, lastUserPreview))
                  .doOnError(
                      error ->
                          collector.llmCallAttempt(
                              sessionId, turnId, modelName, false, MiddlewareErrorSupport.elapsed(startNanos),
                              startedAtEpochMillis,
                              0, 0, attempt,
                              MiddlewareErrorSupport.classify(error),
                              MiddlewareErrorSupport.preview(error),
                              messageCount, requestText, lastUserPreview));
            })
        // transientErrors：仅错误态计数重试，成功即重置
        .retryWhen(Retry.max(maxRetries).transientErrors(true).filter(this::retryable))
        .onErrorMap(exhausted -> translate(exhausted, attempts.get()));
  }

  /** 重试分类：超时绝不重试；401/403/429 是用户侧配置问题不重试；
   * 其余带可重试 HTTP 状态的按状态判定；无状态信息视为网络类受限重试。 */
  private boolean retryable(Throwable error) {
    if (MiddlewareErrorSupport.isTimeout(error)) {
      return false;
    }
    return MiddlewareErrorSupport.isRetryableStatus(
        MiddlewareErrorSupport.statusCodeOf(error));
  }

  /** 请求内容拼接（指纹原料）：role + 文本内容逐条成行；框架类型依赖止步于本类。 */
  private static String llmRequestText(java.util.List<io.agentscope.core.message.Msg> messages) {
    if (messages == null || messages.isEmpty()) {
      return null;
    }
    StringBuilder all = new StringBuilder();
    for (io.agentscope.core.message.Msg message : messages) {
      if (message == null) {
        continue;
      }
      all.append(message.getRole()).append(':').append(textOrEmpty(message)).append('\n');
    }
    return all.toString();
  }

  /** 末条用户消息文本（SUMMARY_HASH 档的排障预览原料）。 */
  private static String lastUserText(java.util.List<io.agentscope.core.message.Msg> messages) {
    if (messages == null) {
      return null;
    }
    String last = null;
    for (io.agentscope.core.message.Msg message : messages) {
      if (message != null
          && message.getRole() == io.agentscope.core.message.MsgRole.USER) {
        last = textOrEmpty(message);
      }
    }
    return last;
  }

  private static String textOrEmpty(io.agentscope.core.message.Msg message) {
    try {
      String text = message.getTextContent();
      return text == null ? "" : text;
    } catch (Exception e) {
      return "";
    }
  }

  /** 终态翻译：把重试耗尽包装/原始异常转为带结构化错误码的可读异常。 */
  private Throwable translate(Throwable error, int attempts) {
    Throwable root = MiddlewareErrorSupport.rootCause(error);
    if (root instanceof TimeoutException timeout) {
      return new IllegalStateException(
          "[LLM_CALL_TIMEOUT] 单次模型调用超过 "
              + callTimeout.toSeconds()
              + "s 未完成（已执行 "
              + attempts
              + " 次尝试）；solution=缩小问题范围后重试",
          timeout);
    }
    Integer status = MiddlewareErrorSupport.statusCodeOf(error);
    if (MiddlewareErrorSupport.isUserSideStatus(status)) {
      return new IllegalArgumentException(
          "[LLM_USER_ERROR] 模型服务返回 "
              + status
              + "：请检查模型配置（密钥/额度/限流）",
          root);
    }
    return new IllegalStateException(
        "[LLM_PROVIDER_ERROR] 模型调用在 "
            + attempts
            + " 次尝试后仍失败："
            + MiddlewareErrorSupport.preview(error),
        root);
  }
}
