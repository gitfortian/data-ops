package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.runtime.TurnSubscription;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 运行中轮次登记表（turnId 维度）。停止生成是显式命令：dispose 上游推理并触发终态收尾回调；
 * SSE 断连不经过本表——订阅与执行在提交/执行分离后互不影响。
 */
@ConditionalOnAgentEnabled
@Component
public class AgentTurnRegistry {

  /**
   * @param subscription 取消上游推理的句柄
   * @param cancelFinalizer 取消后的终态收尾（CAS CANCELLED、补帧、消息树落笔、释放单飞）
   */
  private record Handle(TurnSubscription subscription, Runnable cancelFinalizer) {}

  private final Map<String, Handle> running = new ConcurrentHashMap<>();
  private final Map<String, String> sessionIndex = new ConcurrentHashMap<>();

  public void register(
      String sessionId, String turnId, TurnSubscription subscription, Runnable cancelFinalizer) {
    running.put(turnId, new Handle(subscription, cancelFinalizer));
    sessionIndex.put(sessionId, turnId);
  }

  /** 正常终态清理：收尾责任在 Executor，这里只移除登记。 */
  public void unregister(String turnId) {
    Handle handle = running.remove(turnId);
    if (handle != null) {
      sessionIndex.values().removeIf(turnId::equals);
    }
  }

  public Optional<String> runningTurnId(String sessionId) {
    return Optional.ofNullable(sessionIndex.get(sessionId));
  }

  /** 停止生成：dispose 上游 + 执行取消收尾。无登记时静默（排队轮次走存储侧取消）。 */
  public void cancelBySession(String sessionId) {
    String turnId = sessionIndex.remove(sessionId);
    if (turnId == null) {
      return;
    }
    Handle handle = running.remove(turnId);
    if (handle != null) {
      try {
        handle.subscription().dispose();
      } finally {
        handle.cancelFinalizer().run();
      }
    }
  }
}
