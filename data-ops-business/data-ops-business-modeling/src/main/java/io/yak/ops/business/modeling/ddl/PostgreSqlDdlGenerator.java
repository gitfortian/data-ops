package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 模板（ticket 10）。标识符用双引号；列注释、表注释、二级索引都拆成建表语句之后的
 * 独立语句（{@code COMMENT ON …} / {@code CREATE INDEX … ON …}），分区策略写在右括号之后。
 */
@Component
public class PostgreSqlDdlGenerator extends AbstractDdlGenerator {

  @Override
  public String dialect() {
    return "POSTGRESQL";
  }

  @Override
  protected String identifierOpen() {
    return "\"";
  }

  @Override
  protected String identifierClose() {
    return "\"";
  }

  @Override
  protected boolean supportsInlineColumnComment() {
    return false;
  }

  @Override
  protected String indexUsing(IndexDefinition index) {
    return isText(index.indexType())
        ? " USING " + index.indexType().trim().toLowerCase(Locale.ROOT)
        : "";
  }

  @Override
  public String generate(DdlModel model) {
    List<String> hints = new ArrayList<>();
    List<ColumnDefinition> columns = model.columns();
    StringBuilder script = new StringBuilder();
    script.append("CREATE TABLE ").append(quote(model.tableName())).append(" (\n");
    for (int index = 0; index < columns.size(); index++) {
      script.append("  ").append(renderColumn(columns.get(index), hints));
      script.append(index < columns.size() - 1 || !model.primaryKey().isEmpty() ? ",\n" : "\n");
    }
    if (!model.primaryKey().isEmpty()) {
      script.append("  PRIMARY KEY (").append(joinQuoted(model.primaryKey())).append(")\n");
    }
    script.append(")").append(renderPartition(model, hints)).append(";\n");
    for (IndexDefinition index : model.indexes()) {
      script.append(createIndexStatement(model.tableName(), index));
    }
    if (isText(model.tableComment())) {
      script.append("COMMENT ON TABLE ").append(quote(model.tableName())).append(" IS '")
          .append(escape(model.tableComment().trim())).append("';\n");
    }
    for (ColumnDefinition column : columns) {
      String comment = columnCommentOf(column);
      if (comment != null) {
        script.append("COMMENT ON COLUMN ").append(quote(model.tableName())).append('.')
            .append(quote(column.columnName())).append(" IS '").append(escape(comment)).append("';\n");
      }
    }
    notePropertiesIgnored(model, hints);
    return script.append(hintsBlock(hints)).toString();
  }

  /** PostgreSQL 分区只有策略声明，子分区必须另建表，因此补一条提示。 */
  private String renderPartition(DdlModel model, List<String> hints) {
    String type = partitionTypeOf(model);
    if (type.isEmpty()) {
      return "";
    }
    if (!"RANGE".equals(type) && !"LIST".equals(type)) {
      hints.add("分区类型 " + type + " 在 PostgreSQL 模板中未支持，已跳过");
      return "";
    }
    if (model.partitionColumns().isEmpty() && !isText(model.partitionExpression())) {
      hints.add("分区类型 " + type + " 缺少分区列或分区表达式，已跳过");
      return "";
    }
    hints.add("分区表需在执行前补充子分区：CREATE TABLE " + model.tableName()
        + "_p1 PARTITION OF " + model.tableName() + " FOR VALUES ...");
    if (!isText(model.partitionExpression()) && !model.primaryKey().isEmpty()) {
      List<String> missing = new ArrayList<>();
      for (String column : model.partitionColumns()) {
        boolean covered = model.primaryKey().stream().anyMatch(key -> key.equalsIgnoreCase(column));
        if (!covered) {
          missing.add(column);
        }
      }
      if (!missing.isEmpty()) {
        hints.add("PostgreSQL 要求分区键包含在主键内，主键未包含 " + String.join("、", missing)
            + "，请调整主键或改建非分区表");
      }
    }
    return " PARTITION BY " + type + " (" + partitionSource(model) + ")";
  }
}
