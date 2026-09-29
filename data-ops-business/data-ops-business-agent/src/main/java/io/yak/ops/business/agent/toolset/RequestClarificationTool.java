package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * HITL 反问工具：信息不足时挂起当前轮并向用户提问（问题 + 可选选项列表）。
 * externalTool=true 使框架抛出 ToolSuspendException 挂起本轮、保留 pending 状态；
 * 工具方法体永远不会被执行，恢复由 resume 通道以匹配 toolCallId 的用户应答续跑。
 */
@ConditionalOnAgentEnabled
@Component
public class RequestClarificationTool implements AgentToolBox {

  @Tool(
      name = "request_clarification",
      description =
          """
          当且仅当缺少关键信息导致无法继续时（例如统计口径二义、时间范围不明、\
          多个数据集都可能相关），向用户发起一次反问。\
          问题必须具体、可直接回答；能用合理默认值继续时不要反问。\
          提供选项时列出全部可选值，用户也可自由输入。\
          """,
      externalTool = true)
  public String requestClarification(
      @ToolParam(name = "question", description = "面向用户的完整问题，一句话说明缺什么、为什么需要")
          String question,
      @ToolParam(
              name = "options",
              description = "候选答案列表（可空）。适合口径/范围类二选一或多选一场景",
              required = false)
          List<String> options) {
    throw new IllegalStateException("外部反问工具由平台接管，不应在框架内执行");
  }
}
