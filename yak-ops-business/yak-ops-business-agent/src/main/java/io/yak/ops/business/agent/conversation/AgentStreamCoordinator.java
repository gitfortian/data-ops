package io.yak.ops.business.agent.conversation;

import io.agentscope.core.agui.encoder.AguiEventEncoder;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.domain.JournaledTurnEvent;
import io.yak.ops.business.agent.runtime.ChatTurnToAguiMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 传输生命周期协调：建立、心跳、发布（官方 AG-UI 编码，官方化 v2 协议路径）、完成与断连取消。
 * SSE 是传输通道不是状态存储：断连仅触发上游取消，不回滚已发生事实。
 *
 * <p>官方化 v2：线上帧 = 官方 {@code AguiEvent}（{@code data} 内 {@code "type"} 为标准事件名）
 * + yak-ops 扩展头 {@code id}（Last-Event-ID 游标）；实时（executor 回调）与重放（tailer 从
 * 投递日志拉取）走同一 {@link ChatTurnToAguiMapper}，保证断线续播字节一致。</p>
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class AgentStreamCoordinator {

  private final ChatTurnToAguiMapper chatTurnToAguiMapper;
  private final AguiEventEncoder aguiEventEncoder;

  private static final long EMITTER_TIMEOUT_MILLIS = 15 * 60 * 1000L;
  /** 短心跳兼作链路活性探针：若前端连 :ping 都攒批收到，即可断定缓冲在传输层。 */
  private static final long HEARTBEAT_INTERVAL_SECONDS = 3;

  private final ScheduledExecutorService heartbeatExecutor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "yak-agent-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
          });

  /**
   * 建立 SSE 通道。{@code onCancel} 在完成/超时/错误时触发一次，用于终止上游推理。
   */
  public SseEmitter create(Runnable onCancel) {
    SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
    var future =
        heartbeatExecutor.scheduleAtFixedRate(
            () -> sendHeartbeat(emitter),
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS);
    emitter.onCompletion(
        () -> {
          future.cancel(false);
          onCancel.run();
        });
    emitter.onTimeout(
        () -> {
          future.cancel(false);
          onCancel.run();
        });
    emitter.onError(
        error -> {
          future.cancel(false);
          onCancel.run();
        });
    return emitter;
  }

  /**
   * 发布带投递序号的事件帧为官方 AG-UI 帧。帧头 {@code id} 写入投递序号（eventId），
   * 供客户端 Last-Event-ID 断线续播；一个领域帧可能映射为多个 AG-UI 帧（TOOL_RESULT→END+RESULT）。
   */
  public void publish(
      SseEmitter emitter, ChatTurnToAguiMapper.AguiContext ctx, JournaledTurnEvent journaled) {
    try {
      for (io.agentscope.core.agui.event.AguiEvent agui : chatTurnToAguiMapper.map(ctx, journaled.event())) {
        // encodeToJson 返回前导空格 JSON（适配 Spring data: 前缀）；无 event: 名，类型在 JSON 内
        String json = aguiEventEncoder.encodeToJson(agui);
        emitter.send(
            SseEmitter.event()
                .id(Long.toString(journaled.eventId()))
                .data(json));
      }
    } catch (IOException | IllegalStateException e) {
      log.debug("sse publish skipped (client likely disconnected): {}", e.getMessage());
      throw new SseDisconnectedException(e);
    }
  }

  public void complete(SseEmitter emitter) {
    try {
      emitter.complete();
    } catch (Exception e) {
      log.debug("sse complete ignored error: {}", e.getMessage());
    }
  }

  private void sendHeartbeat(SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event().comment("ping"));
    } catch (Exception e) {
      // 心跳失败说明连接已断，静默等待取消回调收尾
      log.trace("sse heartbeat failed: {}", e.getMessage());
    }
  }

  @PreDestroy
  void shutdown() {
    heartbeatExecutor.shutdownNow();
  }

  /** 断连信号：用于触发上游 dispose。 */
  public static class SseDisconnectedException extends RuntimeException {
    SseDisconnectedException(Throwable cause) {
      super(cause);
    }
  }
}
