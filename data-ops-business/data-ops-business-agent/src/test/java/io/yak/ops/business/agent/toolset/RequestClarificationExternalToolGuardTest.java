package io.yak.ops.business.agent.toolset;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.tool.Tool;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * HITL 挂起机制防回归：request_clarification 必须声明 externalTool=true。
 * 一旦该标记被移除，框架会把反问工具当普通工具执行方法体（抛异常），
 * 引发模型连续重试与文字兜底回退（页面实测曾出现 4 连败）。
 */
class RequestClarificationExternalToolGuardTest {

  @Test
  void clarifyToolMustStayExternal() throws Exception {
    Method method = RequestClarificationTool.class.getMethod(
        "requestClarification", String.class, java.util.List.class,
        String.class, Long.class, java.util.List.class);
    Tool annotation = method.getAnnotation(Tool.class);
    assertTrue(annotation != null, "@Tool 注解缺失");
    assertTrue(annotation.externalTool(), "externalTool 必须保持 true：否则反问工具被框架真执行");
    assertTrue("request_clarification".equals(annotation.name()), "工具名变更会破坏前端/HITL 契约");
  }
}
