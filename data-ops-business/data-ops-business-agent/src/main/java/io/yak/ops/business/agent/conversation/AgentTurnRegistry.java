package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.runtime.TurnSubscription;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
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

  /** Publish the cancellation handle atomically with QUEUED -> RUNNING. */
  synchronized boolean claimAndRegister(
      String sessionId, String turnId, TurnSubscription subscription,
      Runnable cancelFinalizer, BooleanSupplier claim) {
    if (!claim.getAsBoolean()) return false;
    register(sessionId, turnId, subscription, cancelFinalizer);
    return true;
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

  /** Process shutdown releases inference resources; persistent orphan recovery owns the outcome. */
  void detach(String turnId) {
    Handle handle = running.remove(turnId);
    sessionIndex.values().removeIf(turnId::equals);
    if (handle != null) handle.subscription().dispose();
  }

  /** 停止生成：先确认取消事实，再 dispose 上游，避免 dispose 回调抢占正常完成。 */
  public void cancelBySession(String sessionId) {
    cancelBySession(sessionId, () -> {});
  }

  /** Freeze the caller's turn identity; a late request must not select the session's new turn. */
  void cancelTurn(String turnId, Runnable cancelQueued) {
    Handle handle;
    synchronized (this) {
      handle = running.remove(turnId);
      if (handle == null) {
        // Same lock as claimAndRegister: either queued CAS wins or its running handle is present.
        cancelQueued.run();
        return;
      }
      sessionIndex.values().removeIf(turnId::equals);
    }
    try {
      handle.cancelFinalizer().run();
    } finally {
      handle.subscription().dispose();
    }
  }

  void cancelBySession(String sessionId, Runnable cancelQueued) {
    Handle handle;
    synchronized (this) {
      String turnId = sessionIndex.remove(sessionId);
      if (turnId == null) {
        cancelQueued.run();
        return;
      }
      handle = running.remove(turnId);
    }
    if (handle != null) {
      try {
        handle.cancelFinalizer().run();
      } finally {
        handle.subscription().dispose();
      }
    }
  }
}
