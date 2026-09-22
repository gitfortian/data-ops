package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Oracle 模板（ticket 10）。列注释内联在列定义里，表注释与二级索引走表后独立语句；
 * 分区策略写在右括号之后，分区值必须另补。
 */
@Component
public class OracleDdlGenerator extends AbstractDdlGenerator {

  @Override
  public String dialect() {
    return "ORACLE";
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
    noteCaseFolding(model, hints);
    notePropertiesIgnored(model, hints);
    return script.append(hintsBlock(hints)).toString();
  }

  private String renderPartition(DdlModel model, List<String> hints) {
    String type = partitionTypeOf(model);
    if (type.isEmpty()) {
      return "";
    }
    if (!"RANGE".equals(type) && !"LIST".equals(type)) {
      hints.add("分区类型 " + type + " 在 Oracle 模板中未支持，已跳过");
      return "";
    }
    if (model.partitionColumns().isEmpty() && !isText(model.partitionExpression())) {
      hints.add("分区类型 " + type + " 缺少分区列或分区表达式，已跳过");
      return "";
    }
    hints.add("分区表需在执行前补充分区定义：PARTITION p1 VALUES "
        + ("RANGE".equals(type) ? "LESS THAN (...)" : "(...)"));
    return " PARTITION BY " + type + " (" + partitionSource(model) + ")";
  }

  /** 双引号会让 Oracle 保留大小写，而不带引号的 SQL 一律按大写解析，两者对不上。 */
  private void noteCaseFolding(DdlModel model, List<String> hints) {
    boolean lowerCased = hasLowerCase(model.tableName());
    for (ColumnDefinition column : model.columns()) {
      lowerCased = lowerCased || hasLowerCase(column.columnName());
    }
    if (lowerCased) {
      hints.add("Oracle 双引号标识符区分大小写：本脚本按原样大小写建名，后续 SQL 引用同名列时也必须带双引号");
    }
  }

  private boolean hasLowerCase(String name) {
    return name != null && !name.equals(name.toUpperCase(Locale.ROOT));
  }
}
