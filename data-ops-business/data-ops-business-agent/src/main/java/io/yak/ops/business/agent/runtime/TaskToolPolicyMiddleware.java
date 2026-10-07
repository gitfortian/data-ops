package io.yak.ops.business.agent.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import io.agentscope.core.tool.Toolkit;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/** Model visibility is a hint; the SDK tool adapter enforces the scope again at execution. */
final class TaskToolPolicyMiddleware implements MiddlewareBase {
  private final int maxInputChars;

  TaskToolPolicyMiddleware(int maxInputChars) {
    if (maxInputChars < 1) throw new IllegalArgumentException("模型输入字符预算必须大于零");
    this.maxInputChars = maxInputChars;
  }

  static void guardTools(Toolkit toolkit) {
    synchronized (toolkit) {
      for (String name : toolkit.getToolNames()) {
        var tool = toolkit.getTool(name);
        if (tool != null && !(tool instanceof TaskScopedTool)) {
          toolkit.registerAgentTool(new TaskScopedTool(tool));
        }
      }
    }
  }

  @Override public Flux<AgentEvent> onActing(Agent agent, RuntimeContext context, ActingInput input,
      Function<ActingInput, Flux<AgentEvent>> next) {
    // DynamicSkillMiddleware can register helpers after initial assembly. Adapters contain no task state.
    guardTools(agent.getToolkit());
    var execution = context.get(AgentExecutionContext.class);
    if (execution != null && execution.target() != null && (execution.target().standardMatch() != null || execution.target().modelMapping() != null)) {
      for (var call : input.toolCalls()) {
        execution.requireTool(call.getName());
        // SDK synthetic output tool does not pass through Toolkit adapters.
        if ("generate_response".equals(call.getName())) execution.reserveTool(call.getName());
      }
    }
    return next.apply(input).doOnNext(event -> {
      if (execution != null && event instanceof io.agentscope.core.event.ToolResultEndEvent end
          && input.toolCalls().stream().anyMatch(call -> "generate_response".equals(call.getName())
              && call.getId().equals(end.getToolCallId()))
          && end.getState() == io.agentscope.core.message.ToolResultState.ERROR) {
        execution.toolFailed("generate_response");
      }
    });
  }

  @Override public Flux<AgentEvent> onModelCall(Agent agent, RuntimeContext context, ModelCallInput input,
      Function<ModelCallInput, Flux<AgentEvent>> next) {
    var execution = context.get(AgentExecutionContext.class);
    if (execution == null) return Flux.error(new IllegalStateException("[TOOL_NOT_ALLOWED] 缺少任务上下文"));
    long chars = 0;
    try {
      var json = new com.fasterxml.jackson.databind.ObjectMapper();
      for (var message : input.messages()) {
        chars += json.writeValueAsString(message).length();
        if (chars > maxInputChars) break;
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
      return Flux.error(new IllegalStateException("[MODEL_INPUT_UNAVAILABLE] 无法核验模型输入预算"));
    }
    if (chars > maxInputChars) {
      return Flux.error(new IllegalStateException("[MODEL_INPUT_LIMIT] 模型输入超过本轮字符预算，请新建会话并缩小问题范围"));
    }
    var tools = input.tools().stream().filter(tool -> execution.toolPolicy().allows(tool.getName())).toList();
    return next.apply(new ModelCallInput(input.messages(), tools, input.options(), input.model()));
  }
}
