package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.tool.Tool;
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

  @Tool(
      name = "list_datasets",
      description =
          "列出当前可查询的全部数据集（含名称与业务描述）。回答数据分析问题前必须先调用本工具确定目标数据集。")
  public String listDatasets() {
    return viewFormatter.formatSummaries(catalogGateway.listOnlineDatasets());
  }
}
