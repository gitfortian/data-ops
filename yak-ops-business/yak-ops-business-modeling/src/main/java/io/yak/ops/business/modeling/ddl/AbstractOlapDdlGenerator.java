package io.yak.ops.business.modeling.ddl;

import io.yak.ops.business.modeling.domain.ColumnDefinition;
import io.yak.ops.business.modeling.domain.IndexDefinition;
import java.util.ArrayList;
import java.util.List;

/**
 * Doris / StarRocks 共用的 OLAP 模板骨架（ticket 10）。两者语法几乎一致，只有键模型名与
 * 二级索引的可用性不同，差异由 {@link #keyClauseName()} 等钩子承担。
 *
 * <p>装配顺序固定为 {@code 列定义) 键模型 → 表注释 → 分区 → 分桶}，这是两族共同接受的写法。
 * 分桶数与副本数属于「模型里没有、必须由用户按集群决定」的信息，一律注释提示，不编默认值。
 */
abstract class AbstractOlapDdlGenerator extends AbstractDdlGenerator {

  /** 主键对应的键模型写法：Doris 用 UNIQUE KEY，StarRocks 用 PRIMARY KEY。 */
  protected abstract String keyClauseName();

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
    StringBuilder script = new StringBuilder();
    script.append("CREATE TABLE ").append(quote(model.tableName())).append(" (\n");
    for (int index = 0; index < columns.size(); index++) {
      script.append("  ").append(renderColumn(columns.get(index), hints));
      script.append(index < columns.size() - 1 ? ",\n" : "\n");
    }
    script.append(")\n");
    if (!model.primaryKey().isEmpty()) {
      script.append(keyClauseName()).append("(").append(joinQuoted(model.primaryKey()))
          .append(")\n");
    }
    if (isText(model.tableComment())) {
      script.append("COMMENT '").append(escape(model.tableComment().trim())).append("'\n");
    }
    script.append(renderPartition(model, hints));
    script.append(renderDistribution(model, hints));
    script.append(";\n");
    renderIndexes(model, hints);
    notePropertiesIgnored(model, hints);
    notePrimaryKeyOrder(model, hints);
    return script.append(hintsBlock(hints)).toString();
  }

  private String renderPartition(DdlModel model, List<String> hints) {
    String type = partitionTypeOf(model);
    if (type.isEmpty()) {
      return "";
    }
    if ("HASH".equals(type) || "RANDOM".equals(type)) {
      hints.add("按 " + type + " 的哈希分列在 " + dialect() + " 里由 DISTRIBUTED BY 表达，未重复生成");
      return "";
    }
    if (!"RANGE".equals(type) && !"LIST".equals(type)) {
      hints.add("分区类型 " + type + " 在 " + dialect() + " 模板中未支持，已跳过");
      return "";
    }
    if (model.partitionColumns().isEmpty() && !isText(model.partitionExpression())) {
      hints.add("分区类型 " + type + " 缺少分区列或分区表达式，已跳过");
      return "";
    }
    hints.add("分区表需在执行前补充分区定义（PARTITION p1 VALUES "
        + ("RANGE".equals(type) ? "LESS THAN (...)" : "(...)") + "）或改用动态分区属性");
    return "PARTITION BY " + type + " (" + partitionSource(model) + ")\n";
  }

  /** 分桶列取主键首列，无主键时取首列；桶数交集群自动分桶，只在提示里点出。 */
  private String renderDistribution(DdlModel model, List<String> hints) {
    String bucketColumn = !model.primaryKey().isEmpty()
        ? model.primaryKey().get(0)
        : model.columns().isEmpty() ? null : model.columns().get(0).columnName();
    if (bucketColumn == null) {
      hints.add("缺少列定义，未能生成分桶子句");
      return "";
    }
    hints.add("分桶数与副本数请按集群补充：DISTRIBUTED BY HASH(" + bucketColumn
        + ") BUCKETS n / PROPERTIES(\"replication_num\" = \"n\")");
    return "DISTRIBUTED BY HASH(" + quote(bucketColumn) + ")\n";
  }

  private void renderIndexes(DdlModel model, List<String> hints) {
    if (model.indexes().isEmpty()) {
      return;
    }
    List<String> names = new ArrayList<>();
    for (IndexDefinition index : model.indexes()) {
      names.add(index.indexName());
    }
    hints.add("二级索引 " + String.join("、", names) + " 在 " + dialect()
        + " 建表语句内无对应写法，已跳过（可按需手工补 inverted index / bitmap index）");
  }

  /** KEY 列必须排在列定义最前，模板不擅自调序，只提示。 */
  private void notePrimaryKeyOrder(DdlModel model, List<String> hints) {
    List<String> primaryKey = model.primaryKey();
    if (primaryKey.isEmpty()) {
      return;
    }
    for (int index = 0; index < primaryKey.size(); index++) {
      boolean aligned = index < model.columns().size()
          && primaryKey.get(index).equalsIgnoreCase(model.columns().get(index).columnName());
      if (!aligned) {
        hints.add(keyClauseName() + " 要求主键列位于列定义最前，请按 "
            + String.join("、", primaryKey) + " 的顺序调整列位置（模板不自动改序）");
        return;
      }
    }
  }
}
