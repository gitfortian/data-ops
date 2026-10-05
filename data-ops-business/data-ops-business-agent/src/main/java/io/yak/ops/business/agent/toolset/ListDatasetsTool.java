package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.catalog.DatasetViewFormatter;
import io.yak.ops.business.agent.gateway.DatasetCatalogGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据集发现工具：列出全部 ONLINE 数据集供模型推理选择。 */
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class ListDatasetsTool implements AgentToolBox {

  private final DatasetCatalogGateway catalogGateway;
  private final DatasetViewFormatter viewFormatter;
  private final AgentToolExecution execution;

  @Tool(
      name = "list_datasets",
      description =
          "列出当前项目上线的数据集（最多50个，含名称与业务描述）。回答数据分析问题前先发现对象；上线不代表已有数据查询权限。")
  public String listDatasets(RuntimeContext context) {
    return execution.call(context, "list_datasets", () -> viewFormatter.formatSummaries(catalogGateway.listOnlineDatasets()));
  }
}
