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
          问题必须具体、可直接回答；已明确的信息不重复问。不得默认会改变结果的字段、时间或统计口径。\
          普通问数可用kind=FIELD/TIME/CALIBER，须先get_dataset_fields，并提供dataset_id与1–8个真实field_ids；\
          FIELD至少两个字段，选项由服务端字段生成。TIME/CALIBER选项仅为待确认建议。\
          question最多2048字，options最多8项且各最多512字，用户也可自由输入。治理反问不传这些问数参数。\
          """,
      externalTool = true)
  public String requestClarification(
      @ToolParam(name = "question", description = "面向用户的完整问题，一句话说明缺什么、为什么需要")
          String question,
      @ToolParam(
              name = "options",
              description = "候选答案列表（可空）。适合口径/范围类二选一或多选一场景",
              required = false)
          List<String> options,
      @ToolParam(name = "kind", description = "普通问数澄清类别：FIELD/TIME/CALIBER；其他反问留空", required = false)
          String kind,
      @ToolParam(name = "dataset_id", description = "本次get_dataset_fields已发现的数据集ID；kind非空时必填", required = false)
          Long datasetId,
      @ToolParam(name = "field_ids", description = "本次发现的相关字段ID，1–8项，FIELD至少两项；kind非空时必填", required = false)
          List<String> fieldIds) {
    throw new IllegalStateException("外部反问工具由平台接管，不应在框架内执行");
  }
}
