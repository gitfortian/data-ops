package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.JournaledTurnEvent;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.support.TurnInputCodec;
import io.yak.ops.business.agent.runtime.ChatTurnToAguiMapper;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 事件流尾随器：SSE 订阅端只按游标轮询投递日志补帧——先落库再读取，
 * 天然获得断线续播与崩溃一致性，订阅端绝不反向影响执行事实。
 * 终止条件二选一：读到终态帧（TURN_FINISHED/ERROR/TURN_CANCELLED）或
 * 连续空读且轮次行已进入终态（兜底崩溃间隙）。
 *
 * <p>官方化 v2：发布走 {@link AgentStreamCoordinator} 的官方 AG-UI 编码；
 * AG-UI 上下文（sessionId/turnId/assistantMessageId）在 watch 时由轮次记录构建一次，
 * 实时与重放共用同一映射，保证线上帧字节一致。</p>
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class AgentEventStreamTailer {

  private final AgentTurnEventRepository eventRepository;
  private final AgentTurnRepository turnRepository;
  private final AgentStreamCoordinator streamCoordinator;
  private final AgentProperties properties;

  private final ScheduledExecutorService scheduler =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "yak-agent-sse-tail");
            thread.setDaemon(true);
            return thread;
          });

  /**
   * 建立尾随任务。无游标的晚加入从当前最新帧起步（不重放历史，避免已渲染内容重复）；
   * 携带 Last-Event-ID 的重连从该游标增量补发。
   */
  public void watch(SseEmitter emitter, AgentTurnRecord record, long cursor) {
    ChatTurnToAguiMapper.AguiContext ctx = contextOf(record);
    AtomicLong position = new AtomicLong(cursor);
    var future =
        scheduler.scheduleAtFixedRate(
            () -> drainOnce(emitter, ctx, record.turnId(), position), properties.getTurn().getSseTailPollMillis(),
            properties.getTurn().getSseTailPollMillis(), TimeUnit.MILLISECONDS);
    // 订阅生命周期与执行解耦：emitter 收尾只停尾随，不影响后台推理
    emitter.onCompletion(() -> future.cancel(false));
    emitter.onTimeout(() -> future.cancel(false));
    emitter.onError(error -> future.cancel(false));
  }

  /** 由轮次记录构建 AG-UI 上下文：assistantMessageId 从输入投影解码（损坏时回退 turnId）。 */
  private static ChatTurnToAguiMapper.AguiContext contextOf(AgentTurnRecord record) {
    String assistantMessageId = null;
    try {
      assistantMessageId =
          TurnInputCodec.decode(record.payloadJson()).assistantMessageId();
    } catch (RuntimeException e) {
      // 输入投影损坏：messageId 由 mapper 兜底为 turnId（不影响续播）
      log.debug("turn payload decode failed, messageId falls back to turnId: turnId={}",
          record.turnId());
    }
    return new ChatTurnToAguiMapper.AguiContext(
        record.sessionId(), record.turnId(), assistantMessageId);
  }

  private void drainOnce(
      SseEmitter emitter, ChatTurnToAguiMapper.AguiContext ctx, String turnId, AtomicLong position) {
    try {
      List<JournaledTurnEvent> frames =
          eventRepository.listAfter(
              turnId, position.get(), properties.getTurn().getReplayBatchSize());
      if (frames.isEmpty()) {
        checkRowTerminal(emitter, turnId);
        return;
      }
      for (JournaledTurnEvent frame : frames) {
        streamCoordinator.publish(emitter, ctx, frame);
        position.set(frame.eventId());
        if (frame.terminal()) {
          streamCoordinator.complete(emitter);
          return;
        }
      }
    } catch (AgentStreamCoordinator.SseDisconnectedException disconnected) {
      log.debug("sse tail stopped (client disconnected): turnId={}", turnId);
    } catch (Exception e) {
      log.warn("sse tail tick failed: turnId={}", turnId, e);
    }
  }

  /** 空读时的兜底：执行侧终态 CAS 已生效但终帧尚未被本轮读到时，避免订阅端悬挂。 */
  private void checkRowTerminal(SseEmitter emitter, String turnId) {
    turnRepository
        .findByTurnId(turnId)
        .filter(record -> record.status().terminal())
        .ifPresent(record -> streamCoordinator.complete(emitter));
  }

  @PreDestroy
  void shutdown() {
    scheduler.shutdownNow();
  }
}
