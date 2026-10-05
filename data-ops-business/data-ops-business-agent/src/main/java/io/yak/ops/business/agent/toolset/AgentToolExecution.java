package io.yak.ops.business.agent.toolset;

import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.AgentPermissionCode;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.core.security.UserExecutionScope;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Executes on the actual tool thread; credentials and permissions never come from model arguments. */
@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class AgentToolExecution {
  private final UserExecutionScope users;
  private final ActionAuthorization authorization;

  public <T> T call(RuntimeContext context, String toolName, Supplier<T> action) {
    state(context).requireTool(toolName);
    if (context == null || context.getUserId() == null) throw new SecurityException("工具缺少认证上下文");
    Number project = context.get(AgentExecutionContext.PROJECT_ID);
    if (project == null || project.longValue() <= 0) throw new SecurityException("工具缺少项目上下文");
    try {
      return users.call(Long.parseLong(context.getUserId()), project.longValue(), () -> {
        authorization.requirePermission(AgentPermissionCode.CHAT_RUN);
        state(context).requireTool(toolName);
        return action.get();
      });
    } catch (io.yak.ops.core.security.ActionAccessDeniedException | SecurityException denied) {
      throw new IllegalStateException("[PERMISSION_DENIED] 当前用户无权执行此工具，请检查账号、项目和操作权限");
    } catch (RuntimeException failed) {
      String marker = failed.getMessage() == null ? "" : failed.getMessage();
      if (marker.startsWith("[DATASET_VERSION_CHANGED]")) {
        throw new IllegalArgumentException("[DATASET_VERSION_CHANGED] 请重新调用 get_dataset_fields 获取当前版本");
      }
      if (marker.startsWith("[DATASET_DISCOVERY_REQUIRED]") || marker.startsWith("[FIELD_WHITELIST_REJECTED]")) {
        throw new IllegalArgumentException("[DATASET_DISCOVERY_REQUIRED] 请重新调用 get_dataset_fields 确认合法字段");
      }
      if (marker.startsWith("[PERMISSION_DENIED]")) {
        throw new IllegalStateException("[PERMISSION_DENIED] 当前用户无权读取此数据");
      }
      throw new IllegalStateException("[TOOL_UNAVAILABLE] 工具读取失败，请到源页面检查或稍后重试");
    }
  }

  public static AgentExecutionContext state(RuntimeContext context) {
    AgentExecutionContext state = context == null ? null : context.get(AgentExecutionContext.class);
    if (state == null) throw new IllegalStateException("工具缺少轮次上下文");
    return state;
  }
}
