package io.yak.ops.business.workflow.domain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * 调度启动调用栈内的 Trigger 绑定作用域。
 *
 * <p>只用于把 engine.start 刚持久化的 WorkflowExecution ID 在同一事务里提前写回
 * Trigger Ledger。使用栈支持“超快实例终态 -> 立即推进下一条 SERIAL_WAIT”的嵌套启动。</p>
 */
public final class WorkflowScheduleLaunchBindingScope {
  private static final ThreadLocal<Deque<String>> TRIGGER_IDS =
      ThreadLocal.withInitial(ArrayDeque::new);

  private WorkflowScheduleLaunchBindingScope() {
  }

  public static Scope open(String triggerId) {
    if (triggerId == null || triggerId.isBlank()) {
      throw new IllegalArgumentException("调度 triggerId 不能为空");
    }
    String normalized = triggerId.trim();
    TRIGGER_IDS.get().push(normalized);
    return () -> close(normalized);
  }

  public static String currentTriggerId() {
    Deque<String> values = TRIGGER_IDS.get();
    String current = values.peek();
    if (values.isEmpty()) TRIGGER_IDS.remove();
    return current;
  }

  private static void close(String expected) {
    Deque<String> values = TRIGGER_IDS.get();
    String actual = values.poll();
    if (values.isEmpty()) TRIGGER_IDS.remove();
    if (!Objects.equals(expected, actual)) {
      throw new IllegalStateException(
          "工作流调度启动绑定作用域关闭顺序异常：expected=" + expected + ", actual=" + actual);
    }
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
