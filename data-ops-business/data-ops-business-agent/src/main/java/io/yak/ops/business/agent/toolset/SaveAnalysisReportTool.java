package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.yak.ops.business.agent.report.AgentReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 报告沉淀工具：走声明的 toolset -> report corridor 落库，会话身份取自运行时上下文。 */
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class SaveAnalysisReportTool implements AgentToolBox {

  private final AgentReportService reportService;
  private final AgentToolExecution execution;

  @Tool(
      name = "save_analysis_report",
      description =
          """
          将本次分析沉淀为报告并保存。仅当前面的查询与分析步骤均成功完成后才可调用。\
          正文使用 Markdown：包含结论摘要、分析过程、数据结果与建议；\
          图表使用 ```echarts 代码块，块内为纯 JSON 的 ECharts Option 配置。\
          保存成功后无需向用户复述全文，告知"报告已生成"即可。\
          """)
  public String saveAnalysisReport(
      RuntimeContext context,
      @ToolParam(name = "title", description = "报告标题，简明概括分析主题与核心结论") String title,
      @ToolParam(name = "markdown_text", description = "完整 Markdown 格式报告正文")
          String markdownText) {
    if (context == null || context.getSessionId() == null) {
      throw new IllegalStateException("工具执行上下文缺少会话标识");
    }
    var state = AgentToolExecution.state(context);
    if (state.target() != null || state.evidence().containsGovernanceEvidence()) {
      throw new IllegalArgumentException("首版治理报告需先完成证据校验，请在对话结果中查看解读与回链");
    }
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("报告标题不能为空");
    }
    if (title.length() > 200) {
      throw new IllegalArgumentException("报告标题过长（" + title.length() + " 字符），请压缩到 200 字符以内");
    }
    if (markdownText == null || markdownText.isBlank()) {
      throw new IllegalArgumentException("报告正文不能为空");
    }
    if (!state.evidence().entries().isEmpty() && !state.evidence().hasValidCitations(markdownText)) {
      throw new IllegalArgumentException("报告需引用本轮真实查询证据后才能保存");
    }
    String content = state.evidence().entries().isEmpty() ? markdownText : state.evidence().validateAnswer(markdownText);
    long reportId = execution.call(context, () -> reportService.saveFromTool(context.getSessionId(), title, content));
    String references = state.evidence().entries().stream().map(e -> "[" + e.id() + "]")
        .collect(java.util.stream.Collectors.joining(" "));
    return "SUCCESS: 报告已保存，reportId=" + reportId + "。向用户确认时附带查询证据引用 " + references;
  }
}
