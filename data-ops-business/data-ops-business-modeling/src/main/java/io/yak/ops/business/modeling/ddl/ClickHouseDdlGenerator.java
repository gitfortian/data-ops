package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ClickHouse 模板（ticket 10）。与关系库差别最大的一处：可空性由类型包装 {@code Nullable(T)} 表达，
 * 没有 {@code NOT NULL} 约束；索引、分区定义、表属性也都不能直接落到建表语句里，一律注释提示。
 *
 * <p>表引擎固定 {@code MergeTree}（模型里没有引擎信息，也不替用户挑），排序键取主键，无主键时取首列；
 * 主键/排序键列不允许 {@code Nullable}，遇到可空主键时按非空输出并提示。
 */
@Component
public class ClickHouseDdlGenerator extends AbstractDdlGenerator {

  @Override
  public String dialect() {
    return "CLICKHOUSE";
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
    List<String> hints = new ArrayList<>();
    List<ColumnDefinition> columns = model.columns();
    Set<String> keyColumns = keyColumns(model);
    StringBuilder script = new StringBuilder();
    script.append("CREATE TABLE ").append(quote(model.tableName())).append(" (\n");
    for (int index = 0; index < columns.size(); index++) {
      script.append("  ").append(renderColumn(columns.get(index), hints, keyColumns));
      script.append(index < columns.size() - 1 ? ",\n" : "\n");
    }
    script.append(")\nENGINE = MergeTree\n").append(renderPartition(model, hints));
    script.append("ORDER BY ").append(renderOrderBy(model)).append('\n');
    if (isText(model.tableComment())) {
      script.append("COMMENT '").append(escape(model.tableComment().trim())).append("'\n");
    }
    script.append(";\n");
    noteIndexes(model, hints);
    notePropertiesIgnored(model, hints);
    return script.append(hintsBlock(hints)).toString();
  }

  /** 一列的 ClickHouse 写法：可空性走 {@code Nullable}，不输出 {@code NOT NULL}。 */
  private String renderColumn(ColumnDefinition column, List<String> hints, Set<String> keyColumns) {
    String type = renderType(column, hints);
    if (Boolean.TRUE.equals(column.nullable())) {
      if (keyColumns.contains(columnName(column))) {
        hints.add("主键/排序键列 " + column.columnName()
            + " 在 ClickHouse 不能为 Nullable，已按非空输出，请确认该列数据无空值");
      } else {
        type = "Nullable(" + type + ")";
      }
    }
    StringBuilder rendered = new StringBuilder();
    rendered.append(quote(column.columnName())).append(' ').append(type);
    if (isText(column.defaultValue())) {
      rendered.append(" DEFAULT ").append(renderDefault(column.defaultValue()));
    }
    if (isText(column.comment())) {
      rendered.append(" COMMENT '").append(escape(column.comment().trim())).append("'");
    }
    return rendered.toString();
  }

  /** 排序键：有主键用主键，无主键用首列，空表用 tuple() 占位。 */
  private String renderOrderBy(DdlModel model) {
    if (!model.primaryKey().isEmpty()) {
      return "(" + joinQuoted(model.primaryKey()) + ")";
    }
    return model.columns().isEmpty()
        ? "tuple()"
        : "(" + quote(model.columns().get(0).columnName()) + ")";
  }

  private Set<String> keyColumns(DdlModel model) {
    Set<String> keys = new HashSet<>();
    model.primaryKey().forEach(name -> keys.add(name.toUpperCase(Locale.ROOT)));
    if (keys.isEmpty() && !model.columns().isEmpty()) {
      keys.add(columnName(model.columns().get(0)));
    }
    return keys;
  }

  private String columnName(ColumnDefinition column) {
    return column.columnName() == null ? "" : column.columnName().toUpperCase(Locale.ROOT);
  }

  private String renderPartition(DdlModel model, List<String> hints) {
    String type = partitionTypeOf(model);
    if (type.isEmpty()) {
      return "";
    }
    if (isText(model.partitionExpression())) {
      return "PARTITION BY " + model.partitionExpression().trim() + "\n";
    }
    if (!model.partitionColumns().isEmpty()) {
      hints.add("ClickHouse 分区键建议写成表达式（如 toYYYYMM(dt)），已按分区列直出，请确认");
      noteNullablePartitionKey(model, hints);
      String columns = joinQuoted(model.partitionColumns());
      return "PARTITION BY "
          + (model.partitionColumns().size() > 1 ? "(" + columns + ")" : columns) + "\n";
    }
    hints.add("分区类型 " + type + " 缺少分区列或分区表达式，已跳过");
    return "";
  }

  /** 分区键不接受 Nullable 列：点名可空分区列，改列还是改表达式由用户决定。 */
  private void noteNullablePartitionKey(DdlModel model, List<String> hints) {
    List<String> nullable = new ArrayList<>();
    for (String name : model.partitionColumns()) {
      boolean isNullable = model.columns().stream()
          .anyMatch(column -> columnName(column).equals(name.toUpperCase(Locale.ROOT))
              && Boolean.TRUE.equals(column.nullable()));
      if (isNullable) {
        nullable.add(name);
      }
    }
    if (!nullable.isEmpty()) {
      hints.add("分区列 " + String.join("、", nullable)
          + " 为可空列，ClickHouse 分区键不接受 Nullable，请改为非空列或改用表达式分区");
    }
  }

  private void noteIndexes(DdlModel model, List<String> hints) {
    if (model.indexes().isEmpty()) {
      return;
    }
    List<String> names = new ArrayList<>();
    for (IndexDefinition index : model.indexes()) {
      names.add(index.indexName());
    }
    hints.add("二级索引 " + String.join("、", names)
        + " 需要 data-skipping index 表达式（INDEX ... TYPE ... GRANULARITY n），模板已跳过");
  }
}
