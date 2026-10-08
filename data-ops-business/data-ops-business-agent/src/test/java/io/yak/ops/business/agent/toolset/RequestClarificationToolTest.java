package io.yak.ops.business.agent.toolset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.tool.Tool;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 反问工具契约测试：externalTool 挂起声明 + 方法体不可执行守卫。 */
class RequestClarificationToolTest {

  private final RequestClarificationTool tool = new RequestClarificationTool();

  @Test
  void isDeclaredAsExternalSuspendTool() throws NoSuchMethodException {
    assertTrue(tool instanceof AgentToolBox, "必须实现标记接口以被 runtime 收集");

    Method method = tool.getClass().getMethod("requestClarification", String.class, List.class, String.class, Long.class, List.class);
    Tool annotation = method.getAnnotation(Tool.class);
    assertEquals("request_clarification", annotation.name());
    assertTrue(annotation.externalTool(), "必须声明 externalTool=true 才能挂起本轮");
  }

  @Test
  void bodyIsGuardedAgainstFrameworkExecution() {
    assertThrows(
        IllegalStateException.class, () -> tool.requestClarification("q", List.of("a", "b"), null, null, null));
  }
}
