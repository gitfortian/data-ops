package io.yak.ops.business.agent.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 工具执行审计中间件（onActing 拦截点）：<b>按单个工具调用</b>落 KIND_TOOL_CALL 步骤记录——
 * 不再把一批并行调用合并成一条逗号名；每个调用独立带 toolCallId（与事件帧 JOIN 键）、
 * 入参摘要（O1 修复：ToolUseBlock 捕获后经 Collector 策略引擎落库）、输出摘要（成功时）、
 * 终态（SUCCESS/ERROR/INTERRUPTED/DENIED，来自框架 {@link ToolResultEndEvent#getState()}）
 * 与单次耗时。失败也记账；工具实现保持零感知。
 *
 * <p>时序来源是框架事件本身（ToolResultEndEvent 到达即该调用终态时刻），
 * 不依赖 onActing 整体完成——并行工具各自的耗时互不污染。</p>
 *
 * <p>由 {@code AgentRuntime} 装配（非 Spring bean）。</p>
 */
public class ToolAuditMiddleware implements MiddlewareBase {

  private final AgentObservationCollector collector;
  private final TurnCorrelation turnCorrelation;

  public ToolAuditMiddleware(AgentObservationCollector collector, TurnCorrelation turnCorrelation) {
    this.collector = collector;
    this.turnCorrelation = turnCorrelation;
  }

  @Override
  public Flux<AgentEvent> onActing(
      Agent agent,
      RuntimeContext context,
      ActingInput input,
      Function<ActingInput, Flux<AgentEvent>> next) {
    String sessionId = context.getSessionId();
    String turnId = turnCorrelation.turnIdOf(sessionId);
    Map<String, Track> tracks = new LinkedHashMap<>();
    for (ToolUseBlock call : input.toolCalls()) {
      tracks.put(
          call.getId(),
          new Track(call.getId(), call.getName(), call.getContent(), System.nanoTime()));
    }
    return next.apply(input)
        .doOnNext(event -> observe(tracks, event))
        .doOnComplete(() -> flush(tracks.values(), sessionId, turnId, null, null))
        .doOnError(
            error ->
                flush(
                    tracks.values(),
                    sessionId,
                    turnId,
                    MiddlewareErrorSupport.classify(error),
                    MiddlewareErrorSupport.preview(error)));
  }

  private static void observe(Map<String, Track> tracks, AgentEvent event) {
    if (event instanceof ToolResultTextDeltaEvent delta) {
      Track track = tracks.get(delta.getToolCallId());
      if (track != null && delta.getDelta() != null) {
        track.buffer.append(delta.getDelta());
      }
    } else if (event instanceof ToolResultEndEvent end) {
      Track track = tracks.get(end.getToolCallId());
      if (track != null) {
        track.finishedNanos = System.nanoTime();
        track.state = end.getState();
      }
    }
  }

  /** 逐个调用落账：串行翻账不感知并行执行的时序。 */
  private void flush(
      Collection<Track> tracks, String sessionId, String turnId,
      String fluxErrorCode, String fluxErrorPreview) {
    for (Track track : tracks) {
      boolean fluxFailed = fluxErrorCode != null;
      boolean callFailed =
          !fluxFailed && track.state != null && track.state != ToolResultState.SUCCESS;
      String errorCode =
          fluxFailed ? fluxErrorCode
              : callFailed ? track.state.name() : null;
      String errorPreview =
          fluxFailed ? fluxErrorPreview
              : callFailed ? "工具执行未成功（state=" + track.state.name() + "）" : null;
      String output = track.buffer.isEmpty() ? null : track.buffer.toString();
      long elapsedMs =
          track.finishedNanos != 0
              ? (track.finishedNanos - track.startedNanos) / 1_000_000L
              : MiddlewareErrorSupport.elapsed(track.startedNanos);
      collector.toolCall(
          sessionId,
          turnId,
          track.toolCallId,
          track.name,
          !fluxFailed && !callFailed,
          elapsedMs,
          // span 起点：终态时刻回推耗时（nanoTime 时基与 epoch 不通，回推误差 ≤1ms 可忽略）
          System.currentTimeMillis() - Math.max(0, elapsedMs),
          track.requestContent,
          output,
          errorCode,
          errorPreview);
      // O3 Guard 观测：守卫拒绝以异常穿透工具边界，标记可能出现在工具错误输出或 flux 错误预览中；
      // 检测点收敛于此（该处已持有入参/输出全文），GUARD span 归属触发它的 LLM_CALL
      String guardDetail = guardMarkerOf(output, errorPreview);
      if (guardDetail != null) {
        collector.guardRejected(
            sessionId, turnId, track.toolCallId, track.name, track.requestContent, guardDetail);
      }
    }
  }

  /** 守卫拒绝标记识别（标记契约：工具守卫以 [GUARD_REJECTED] 前缀抛出，如数据集白名单校验）。 */
  private static String guardMarkerOf(String output, String errorPreview) {
    for (String candidate : new String[] {output, errorPreview}) {
      if (candidate == null) {
        continue;
      }
      int idx = candidate.indexOf("[GUARD_REJECTED]");
      if (idx >= 0) {
        String detail = candidate.substring(idx);
        return detail.length() > 300 ? detail.substring(0, 300) : detail;
      }
    }
    return null;
  }

  /** 单次工具调用跟踪态（调用级事实，事件驱动串行访问）。 */
  private static final class Track {
    final String toolCallId;
    final String name;
    final String requestContent;
    final long startedNanos;
    final StringBuilder buffer = new StringBuilder();
    long finishedNanos = 0L;
    ToolResultState state = null;

    Track(String toolCallId, String name, String requestContent, long startedNanos) {
      this.toolCallId = toolCallId;
      this.name = name;
      this.requestContent = requestContent;
      this.startedNanos = startedNanos;
    }
  }
}