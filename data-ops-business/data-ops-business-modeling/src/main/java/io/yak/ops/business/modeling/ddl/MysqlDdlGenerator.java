package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** MySQL dialect template (ticket 09)。分区子句在语句结束后另起一段，索引内联在建表语句里。 */
@Component
public class MysqlDdlGenerator extends AbstractDdlGenerator {

  @Override
  public String dialect() {
    return "MYSQL";
  }

  @Override
  protected String identifierOpen() {
    return "`";
  }

  @Override
  protected String identifierClose() {
    return "`";
  }

  @Override
  public String generate(DdlModel model) {
    Map<String, String> properties =
        model.tableProperties() == null ? Map.of() : model.tableProperties();
    List<String> hints = new ArrayList<>();
    StringBuilder script = new StringBuilder();
    script.append("CREATE TABLE ").append(quote(model.tableName())).append(" (\n");
    List<ColumnDefinition> columns = model.columns();
    for (int index = 0; index < columns.size(); index++) {
      script.append("  ").append(renderColumn(columns.get(index), hints));
      script.append(index < columns.size() - 1
              || !model.primaryKey().isEmpty()
              || !model.indexes().isEmpty()
          ? ",\n"
          : "\n");
    }
    if (!model.primaryKey().isEmpty()) {
      script.append("  PRIMARY KEY (")
          .append(joinQuoted(model.primaryKey()))
          .append(")")
          .append(model.indexes().isEmpty() ? "" : ",")
          .append("\n");
    }
    for (int index = 0; index < model.indexes().size(); index++) {
      script.append("  ").append(renderIndex(model.indexes().get(index)));
      script.append(index < model.indexes().size() - 1 ? ",\n" : "\n");
    }
    script.append(") ");
    script.append(String.join(" ", tableOptions(model, properties, hints)));
    script.append(";\n");
    script.append(renderPartition(model, hints));
    return script.append(hintsBlock(hints)).toString();
  }

  private List<String> tableOptions(
      DdlModel model, Map<String, String> properties, List<String> hints) {
    List<String> options = new ArrayList<>();
    String engine = properties.get("ENGINE");
    if (engine != null) {
      options.add("ENGINE=" + engine);
    }
    String charset = properties.get("CHARSET");
    if (charset != null) {
      options.add("DEFAULT CHARSET=" + charset);
    }
    if (isText(model.tableComment())) {
      options.add("COMMENT='" + escape(model.tableComment()) + "'");
    }
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String key = entry.getKey().toUpperCase();
      if (!"ENGINE".equals(key) && !"CHARSET".equals(key)) {
        options.add(entry.getKey() + "=" + entry.getValue());
      }
    }
    return options;
  }

  /** RANGE/LIST 需要平台尚未建模的分区定义，给注释提示而非编造分区。 */
  private String renderPartition(DdlModel model, List<String> hints) {
    String type = model.partitionType();
    if (type == null || type.isBlank()) {
      return "";
    }
    String upper = type.trim().toUpperCase();
    String source = !model.partitionExpression().isBlank()
        ? model.partitionExpression()
        : joinQuoted(model.partitionColumns());
    if ("HASH".equals(upper) || "KEY".equals(upper)) {
      return "\nPARTITION BY " + upper + " (" + source + ");\n";
    }
    if ("RANGE".equals(upper) || "LIST".equals(upper)) {
      return "\nPARTITION BY " + upper + " (" + source + ")\n"
          + "-- 请在执行前补充分区定义（PARTITION p0 VALUES ...）；\n";
    }
    hints.add("分区类型 " + type + " 在 MySQL 模板中未支持，已跳过");
    return "";
  }

  @Override
  protected String renderIndex(IndexDefinition index) {
    StringBuilder rendered = new StringBuilder();
    if (Boolean.TRUE.equals(index.uniqueIndex())) {
      rendered.append("UNIQUE ");
    }
    rendered.append("INDEX ").append(quote(index.indexName())).append(" (")
        .append(joinQuoted(index.columns()))
        .append(")");
    if (index.indexType() != null && !index.indexType().isBlank()) {
      rendered.append(" USING ").append(index.indexType().trim().toUpperCase());
    }
    return rendered.toString();
  }
}
