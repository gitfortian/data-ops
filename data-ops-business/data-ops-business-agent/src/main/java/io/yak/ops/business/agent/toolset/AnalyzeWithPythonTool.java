package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.yak.ops.business.agent.gateway.PythonRunnerGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 统计分析工具：对查询结果做相关性/回归/分布检验等 Python 计算。
 * 仅在 yak.agent.python.enabled=true 时装配；进程执行卸载到 boundedElastic，避免阻塞事件循环。
 */
@ConditionalOnAgentEnabled
@Component
@ConditionalOnProperty(prefix = "yak.agent.python", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AnalyzeWithPythonTool implements AgentToolBox {

  private final PythonRunnerGateway pythonRunnerGateway;
  private final AgentToolExecution execution;

  @Tool(
      name = "analyze_with_python",
      description =
          """
          执行 Python 代码做统计分析（可用库：pandas、numpy、scipy）。\
          查询结果已在对话中取得，须把数据以内联方式写进代码；\
          用 print() 输出分析结论。仅在 SQL 无法完成的统计计算时使用，例如相关系数、\
          线性回归、分布检验等。\
          """)
  public Mono<String> analyzeWithPython(
      io.agentscope.core.agent.RuntimeContext context,
      @ToolParam(name = "code", description = "自包含的 Python 分析代码，数据必须内联") String code) {
    var state = AgentToolExecution.state(context);
    if (state.target() != null || state.evidence().containsGovernanceEvidence()) {
      return Mono.error(new IllegalArgumentException("首版治理解读不支持代码执行"));
    }
    return Mono.fromCallable(() -> execution.call(context, () -> pythonRunnerGateway.execute(code)))
        .subscribeOn(Schedulers.boundedElastic());
  }
}
