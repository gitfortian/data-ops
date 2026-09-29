package io.yak.ops.business.workflow.domain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次 Launch 调用栈内的 Workflow input 覆盖层。
 *
 * <p>调度/Backfill 参数只影响本次 engine.start，不修改不可变 WorkflowVersion。</p>
 */
public final class WorkflowRunInputScope {
  private static final ThreadLocal<Deque<Map<String, Object>>> STACK =
      ThreadLocal.withInitial(ArrayDeque::new);

  private WorkflowRunInputScope() {
  }

  public static Scope open(Map<String, Object> overrides) {
    Map<String, Object> value = overrides == null
        ? Map.of()
        : Map.copyOf(new LinkedHashMap<>(overrides));
    Deque<Map<String, Object>> stack = STACK.get();
    stack.push(value);
    return () -> {
      Deque<Map<String, Object>> current = STACK.get();
      if (!current.isEmpty()) current.pop();
      if (current.isEmpty()) STACK.remove();
    };
  }

  public static Map<String, Object> current() {
    Deque<Map<String, Object>> stack = STACK.get();
    return stack.isEmpty() ? Map.of() : stack.peek();
  }

  @FunctionalInterface
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }
}
