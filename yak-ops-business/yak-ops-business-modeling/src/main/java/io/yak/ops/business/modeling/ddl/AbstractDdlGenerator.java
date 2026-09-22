package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import io.yak.ops.business.modeling.domain.ModelDialect;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 各方言 DDL 模板共用的渲染件（ticket 09/10）。
 *
 * <p>只抽公共零件（标识符引号、列定义、类型解析、默认值、转义、提示汇总），不抽整套骨架——
 * 建表语句的装配顺序差异太大（Doris 的 KEY/COMMENT/PARTITION/DISTRIBUTED 各有固定先后，
 * ClickHouse 引擎子句在表尾，PG 把索引与注释拆到表后独立语句），强行模板化只会变成一堆钩子。
 */
abstract class AbstractDdlGenerator implements DdlGenerator {

  protected static final Pattern NUMERIC_DEFAULT = Pattern.compile("^-?\\d+(\\.\\d+)?$");
  protected static final Pattern RAW_DEFAULT = Pattern.compile("(?i)^CURRENT_TIMESTAMP(\\(\\d*\\))?$");

  protected ModelDialect dialectEnum() {
    return ModelDialect.valueOf(dialect().toUpperCase(Locale.ROOT));
  }

  /** 标识符左引号：MySQL/Doris/StarRocks/ClickHouse 用反引号，PG/Oracle 用双引号。 */
  protected abstract String identifierOpen();

  /** 标识符右引号。 */
  protected abstract String identifierClose();

  /** 列注释能否内联在列定义里；PG 只能拆成表后 COMMENT ON COLUMN。 */
  protected boolean supportsInlineColumnComment() {
    return true;
  }

  protected String quote(String name) {
    return identifierOpen() + name + identifierClose();
  }

  protected String joinQuoted(List<String> names) {
    StringBuilder joined = new StringBuilder();
    for (int index = 0; index < names.size(); index++) {
      if (index > 0) {
        joined.append(", ");
      }
      joined.append(quote(names.get(index)));
    }
    return joined.toString();
  }

  /** 渲染一列（不含行尾逗号）；类型解析产生的提示累积到 hints。 */
  protected String renderColumn(ColumnDefinition column, List<String> hints) {
    StringBuilder rendered = new StringBuilder();
    rendered.append(quote(column.columnName())).append(' ').append(renderType(column, hints));
    if (Boolean.FALSE.equals(column.nullable())) {
      rendered.append(" NOT NULL");
    }
    if (column.defaultValue() != null && !column.defaultValue().isBlank()) {
      rendered.append(" DEFAULT ").append(renderDefault(column.defaultValue()));
    }
    if (supportsInlineColumnComment() && isText(column.comment())) {
      rendered.append(" COMMENT '").append(escape(column.comment())).append("'");
    }
    return rendered.toString();
  }

  /** 类型写法：跨方言解析后按「类型(长度[,小数位])」渲染。 */
  protected String renderType(ColumnDefinition column, List<String> hints) {
    DdlTypeResolver.Resolved resolved = DdlTypeResolver.resolve(dialectEnum(), column);
    if (resolved.note() != null) {
      hints.add(resolved.note());
    }
    if (resolved.length() == null) {
      return resolved.dataType();
    }
    return resolved.scale() == null
        ? resolved.dataType() + "(" + resolved.length() + ")"
        : resolved.dataType() + "(" + resolved.length() + "," + resolved.scale() + ")";
  }

  /** 内联列注释的方言（PG 等）需要单独把列注释取出来。 */
  protected String columnCommentOf(ColumnDefinition column) {
    return supportsInlineColumnComment() || !isText(column.comment()) ? null : column.comment().trim();
  }

  /** 数字与 CURRENT_TIMESTAMP 裸写，其余按字符串字面量加引号转义。 */
  protected String renderDefault(String defaultValue) {
    String value = defaultValue.trim();
    if (NUMERIC_DEFAULT.matcher(value).matches() || RAW_DEFAULT.matcher(value).matches()) {
      return value;
    }
    return "'" + escape(value) + "'";
  }

  /** 二级索引的表内写法；不支持内联的方言返回 null 并由调用方降级。 */
  protected String renderIndex(IndexDefinition index) {
    return null;
  }

  /** 累积的提示统一落到脚本尾部，不混进可执行语句里。 */
  protected String hintsBlock(List<String> hints) {
    if (hints.isEmpty()) {
      return "";
    }
    StringBuilder block = new StringBuilder("\n-- 生成提示：\n");
    hints.stream().distinct().forEach(hint -> block.append("--   ").append(hint).append('\n'));
    return block.toString();
  }

  /** 表属性在该方言没有对应写法时汇总成提示，不静默丢弃。 */
  protected void notePropertiesIgnored(DdlModel model, List<String> hints) {
    Map<String, String> properties = model.tableProperties();
    if (properties != null && !properties.isEmpty()) {
      hints.add("表属性 " + String.join("、", properties.keySet()) + " 在 " + dialect()
          + " 无对应写法，已忽略，请按目标库补充");
    }
  }

  /** 分区类型（大写）；未设置时为空串。 */
  protected String partitionTypeOf(DdlModel model) {
    return model.partitionType() == null ? "" : model.partitionType().trim().toUpperCase(Locale.ROOT);
  }

  /** 分区取值：优先用户填写的表达式，否则按引号包好的分区列拼接。 */
  protected String partitionSource(DdlModel model) {
    String expression = model.partitionExpression();
    return expression == null || expression.isBlank()
        ? joinQuoted(model.partitionColumns())
        : expression.trim();
  }

  /** 表后独立的 CREATE INDEX 语句（PG/Oracle）；USING 位置由 indexUsing 钩子决定。 */
  protected String createIndexStatement(String tableName, IndexDefinition index) {
    StringBuilder statement = new StringBuilder("CREATE ");
    if (Boolean.TRUE.equals(index.uniqueIndex())) {
      statement.append("UNIQUE ");
    }
    statement.append("INDEX ").append(quote(index.indexName())).append(" ON ").append(quote(tableName))
        .append(indexUsing(index)).append(" (").append(joinQuoted(index.columns())).append(");\n");
    return statement.toString();
  }

  /** 索引访问方法子句；仅 PostgreSQL 需要放在表名之后、列之前。 */
  protected String indexUsing(IndexDefinition index) {
    return "";
  }

  protected boolean isText(String value) {
    return value != null && !value.isBlank();
  }

  protected String escape(String value) {
    return value.replace("\\", "\\\\").replace("'", "''");
  }
}
