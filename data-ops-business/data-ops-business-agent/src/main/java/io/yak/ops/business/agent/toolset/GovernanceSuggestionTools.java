package io.yak.ops.business.agent.toolset;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.gateway.GovernanceSuggestionGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class GovernanceSuggestionTools implements AgentToolBox, AgentSystemPromptContributor {
  private final AgentToolExecution execution;
  private final GovernanceSuggestionGateway suggestions;

  @Tool(name = "get_quality_monitor_evidence", description = "只读当前注册物理表的质量监控字段和内置模板；不读取数据行、SQL、连接配置。")
  public String context(RuntimeContext context,
      @ToolParam(name = "monitor_id", description = "质量监控ID") Long monitorId) {
    if (monitorId == null || monitorId <= 0) throw new IllegalArgumentException("监控编号无效");
    return execution.call(context, () -> suggestions.qualityContext(monitorId, AgentToolExecution.state(context)));
  }

  @Tool(name = "propose_quality_rules", description = "在规则建议轮次校验并输出候选，不保存、不运行。rules_json为最多5项JSON数组，字段为templateId,name,columnName,operator,threshold,thresholdEnd,enumValues；禁止SQL。")
  public String rules(RuntimeContext context,
      @ToolParam(name = "rules_json", description = "候选规则JSON数组；阈值按模板单位，比例为0到100") String json) {
    return execution.call(context, () -> suggestions.qualityRules(AgentToolExecution.state(context), json));
  }

  @Tool(name = "propose_asset_description", description = "在资产描述建议轮次输出1到1024字台账描述候选，不写业务。仅依据本轮可读证据，不编造业务用途、合规或SLA。")
  public String description(RuntimeContext context,
      @ToolParam(name = "description", description = "台账描述候选；无用途依据时明确待确认") String value) {
    return execution.call(context, () -> suggestions.description(AgentToolExecution.state(context), value));
  }

  @Override
  public String contribute() {
    return """
        治理建议仅在用户选择对应辅助任务时生成，禁止业务写入。
        QUALITY_RULES：先获取选定监控的当前字段和模板，只用内置模板提出最多5条候选。
        不读取数据样本，不修改过滤条件、负责人、调度或通知；比例单位是百分比0到100。
        缺少允许空值、唯一性、范围、枚举、阈值等业务条件时用request_clarification反问；
        不从字段名推断业务约束，不用历史结果编造当前画像；随后调用propose_quality_rules校验。
        ASSET_DESCRIPTION：读取本轮资产及适用分区证据，调用propose_asset_description输出候选。
        缺用途证据写用途待确认；不得声称健康或合规，不改源表注释。
        候选仍需用户核对并在原页面人工保存；保存不是运行，启用规则可能影响后续自动运行。
        """;
  }
}
