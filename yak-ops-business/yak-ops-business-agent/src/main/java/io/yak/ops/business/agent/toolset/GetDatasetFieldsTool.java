package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.yak.ops.business.agent.catalog.DatasetViewFormatter;
import io.yak.ops.business.agent.gateway.DatasetCatalogGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 数据集字段清单工具：获取维度/度量角色与业务描述，是组装查询参数前的必经步骤。 */
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class GetDatasetFieldsTool implements AgentToolBox {

  private final DatasetCatalogGateway catalogGateway;
  private final DatasetViewFormatter viewFormatter;

  @Tool(
      name = "get_dataset_fields",
      description =
          "获取指定数据集的字段清单（fieldId、显示名、类型、维度/度量角色、业务描述）。"
              + "组装 run_dataset_query 参数前必须先调用本工具确认合法 fieldId。")
  public String getDatasetFields(
      @ToolParam(name = "dataset_id", description = "目标数据集ID") Long datasetId) {
    return viewFormatter.formatFields(catalogGateway.datasetOverview(datasetId));
  }
}
