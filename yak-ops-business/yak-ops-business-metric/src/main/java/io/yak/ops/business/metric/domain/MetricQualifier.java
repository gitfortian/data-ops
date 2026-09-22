package io.yak.ops.business.metric.domain;

import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.util.List;
import java.util.Set;

/**
 * 派生指标的结构化限定条件(修饰词受控模型,02 裁决 R-11):字段 + 运算符 + 值,
 * 由 {@link DerivedMetricAssembler} 编译为标准 SQL 谓词——组装产物即 03 试算的消费契约,
 * 不引入私有语法。
 */
public record MetricQualifier(String field, String op, String value) {

  public static final Set<String> OPS = Set.of("=", "!=", ">", ">=", "<", "<=", "IN", "LIKE", "BETWEEN");
  private static final Set<String> NO_VALUE_OPS = Set.of("IS NULL", "IS NOT NULL");

  /** 编译为单个 SQL 谓词;非法输入抛 INVALID_COMPOSITION。 */
  public static String compile(MetricQualifier q) {
    String field = q.field() == null ? "" : q.field().trim();
    if (field.isEmpty() || !field.matches("[A-Za-z_][A-Za-z0-9_.]*")) {
      throw new MetricException(MetricErrorCode.INVALID_COMPOSITION, "限定条件字段名不合法: " + q.field());
    }
    String op = q.op() == null ? "" : q.op().trim().toUpperCase();
    if (NO_VALUE_OPS.contains(op)) {
      return field + " " + op;
    }
    if (!OPS.contains(op)) {
      throw new MetricException(MetricErrorCode.INVALID_COMPOSITION, "限定条件运算符不支持: " + q.op());
    }
    String value = q.value() == null ? "" : q.value().trim();
    if (value.isEmpty()) {
      throw new MetricException(MetricErrorCode.INVALID_COMPOSITION, "限定条件值不能为空: " + field);
    }
    return switch (op) {
      case "IN" -> field + " IN (" + quotedList(value) + ")";
      case "LIKE" -> field + " LIKE '" + escape(value) + "'";
      case "BETWEEN" -> {
        List<String> bounds = List.of(value.split("\\s+AND\\s+|\\s*,\\s*", 2));
        if (bounds.size() != 2 || bounds.get(0).isBlank() || bounds.get(1).isBlank()) {
          throw new MetricException(MetricErrorCode.INVALID_COMPOSITION,
              "BETWEEN 值需两个边界,如 100 AND 200 或 100,200: " + field);
        }
        yield field + " BETWEEN " + literal(bounds.get(0).trim()) + " AND " + literal(bounds.get(1).trim());
      }
      default -> field + " " + op + " " + literal(value);
    };
  }

  /** 编译并按 AND 连接为一条谓词串;空列表返回 null。 */
  public static String compileAll(List<MetricQualifier> qualifiers) {
    if (qualifiers == null || qualifiers.isEmpty()) {
      return null;
    }
    return qualifiers.stream().map(MetricQualifier::compile).reduce((a, b) -> a + " AND " + b).orElse(null);
  }

  private static String literal(String value) {
    return isNumber(value) ? value : "'" + escape(value) + "'";
  }

  private static String quotedList(String value) {
    return String.join(", ", java.util.Arrays.stream(value.split("[,，]"))
        .map(String::trim).filter(s -> !s.isEmpty()).map(MetricQualifier::literal).toList());
  }

  private static boolean isNumber(String value) {
    return value.matches("-?\\d+(\\.\\d+)?");
  }

  private static String escape(String value) {
    return value.replace("'", "''");
  }
}
