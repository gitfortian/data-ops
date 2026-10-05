package io.yak.ops.business.agent.catalog;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.domain.DatasetSummary;
import java.util.List;
import org.springframework.stereotype.Component;

/** 面向 LLM 的数据集目录文本化：稳定、紧凑、可截断。 */
@ConditionalOnAgentEnabled
@Component
public class DatasetViewFormatter {

  public String formatSummaries(List<DatasetSummary> summaries) {
    if (summaries.isEmpty()) {
      return "当前没有可用的 ONLINE 数据集。";
    }
    StringBuilder sb = new StringBuilder("可用数据集（共 ").append(summaries.size()).append(" 个）：\n");
    for (DatasetSummary summary : summaries) {
      sb.append("- datasetId=").append(summary.id())
          .append(" | name=").append(bounded(summary.name()));
      if (summary.description() != null && !summary.description().isBlank()) {
        sb.append(" | description=").append(bounded(summary.description()));
      }
      sb.append('\n');
    }
    sb.append("\n取数前请先用 get_dataset_fields 查看字段清单。");
    return sb.toString();
  }

  public String formatFields(DatasetSummary.DatasetFields fields) {
    StringBuilder sb =
        new StringBuilder("数据集 ").append(fields.name())
            .append("(datasetId=").append(fields.datasetId()).append(", versionNo=")
            .append(fields.versionNo()).append(") 字段清单：\n");
    if (fields.truncated()) sb.append("[字段已截断，仅展示前200项，可查询字段以此清单为准]\n");
    for (DatasetSummary.FieldView field : fields.fields()) {
      sb.append("- fieldId=").append(field.fieldId())
          .append(" | displayName=").append(bounded(field.displayName()))
          .append(" | type=").append(field.dataType())
          .append(" | role=").append(field.role());
      if (field.description() != null && !field.description().isBlank()) {
        sb.append(" | description=").append(bounded(field.description()));
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private static String bounded(String value) {
    return value == null ? "" : value.length() > 512 ? value.substring(0, 512) + "[截断]" : value;
  }
}
