package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.yak.ops.business.agent.catalog.QuerySpecParser;
import io.yak.ops.business.agent.domain.DatasetQuerySpec;
import io.yak.ops.business.agent.gateway.DatasetQueryGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 结构化取数工具：模型产出扁平 mini 语法参数 -> 解析为规格 -> 白名单校验 -> 网关执行。
 * 校验/执行失败以精确错误文本返回，供模型在同一轮推理中自纠重试。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class RunDatasetQueryTool implements AgentToolBox {

  private final QuerySpecParser specParser;
  private final DatasetQueryGateway queryGateway;
  private final AgentToolExecution execution;

  @Tool(
      name = "run_dataset_query",
      description =
          """
          在指定数据集上执行结构化聚合查询并返回表格结果。只支持维度分组、指标聚合、过滤与排序，\
          不能执行任意 SQL。字段引用必须来自 get_dataset_fields 返回的 fieldId，否则会被拒绝。\
          """)
  public String runDatasetQuery(
      RuntimeContext context,
      @ToolParam(name = "dataset_id", description = "目标数据集ID") Long datasetId,
      @ToolParam(
              name = "dimensions",
              description = "分组维度 fieldId 列表，可空",
              required = false)
          List<String> dimensions,
      @ToolParam(
              name = "metrics",
              description =
                  "指标列表，每项格式 fieldId:AGG，AGG 取值 SUM/AVG/COUNT/COUNT_DISTINCT/MAX/MIN，可空",
              required = false)
          List<String> metrics,
      @ToolParam(
              name = "filters",
              description =
                  "过滤条件列表，格式 fieldId:OP:value；OP 取值 EQ/NE/GT/GTE/LT/LTE/IN/NOT_IN/LIKE/"
                      + "NOT_LIKE/BETWEEN/IS_NULL/IS_NOT_NULL；多值用 | 分隔（IN/BETWEEN），可空",
              required = false)
          List<String> filters,
      @ToolParam(
              name = "sorts",
              description = "排序列表，每项格式 fieldId:ASC 或 fieldId:DESC，可空",
              required = false)
          List<String> sorts,
      @ToolParam(name = "limit", description = "返回行数上限，不传使用默认值", required = false)
      Integer limit) {

    // 会话身份由框架注入的运行时上下文提供，绝不采信模型输出
    String sessionId = context == null ? null : context.getSessionId();
    if (sessionId == null || sessionId.isBlank()) {
      throw new IllegalStateException("工具执行上下文缺少会话标识");
    }

    List<DatasetQuerySpec.Metric> parsedMetrics = specParser.parseMetrics(metrics);
    List<DatasetQuerySpec.Filter> parsedFilters = specParser.parseFilters(filters);
    List<DatasetQuerySpec.Sort> parsedSorts = specParser.parseSorts(sorts);

    DatasetQuerySpec spec =
        new DatasetQuerySpec(datasetId, dimensions, parsedMetrics, parsedFilters, parsedSorts, limit);
    // 白名单校验与查询证据留痕统一在 DatasetQueryGateway 执行边界完成（工具保持薄壳）；
    // 步骤记录由 runtime 的 ToolAuditMiddleware 零侵入落账。
    var discovery = AgentToolExecution.state(context).discovery(datasetId);
    var evidence = execution.call(context, () -> queryGateway.execute(sessionId, spec, discovery));

    var reference = AgentToolExecution.state(context).evidence().register("DATASET",
        "dataset=" + datasetId + "/version=" + discovery.versionNo() + "/query=" + evidence.queryId(),
        "OK", null, "/dataset/" + datasetId);
    StringBuilder sb = new StringBuilder("只读查询证据 [").append(reference.id()).append("]\n查询成功 queryId=").append(evidence.queryId())
        .append("，返回 ").append(evidence.returnedRows()).append(" 行");
    if (evidence.truncated()) {
      sb.append("（已截断）");
    }
    sb.append("，耗时 ").append(evidence.elapsedMillis()).append("ms\n");
    sb.append(String.join(" | ", evidence.columns())).append('\n');
    int displayed = 0;
    for (List<Object> row : evidence.rows()) {
      if (sb.length() >= 12000) break;
      String text = row.toString();
      sb.append(text.length() > 1500 ? text.substring(0, 1500) + "[行内容已截断]" : text).append('\n');
      displayed++;
    }
    if (displayed < evidence.rows().size()) sb.append("[展示已截断，未展示的行不能视为已核验]\n");
    sb.append("versionNo=").append(discovery.versionNo()).append('\n');
    return sb.toString();
  }
}
