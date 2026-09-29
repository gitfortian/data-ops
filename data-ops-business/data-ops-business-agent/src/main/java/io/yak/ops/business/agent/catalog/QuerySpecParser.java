package io.yak.ops.business.agent.catalog;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.domain.DatasetQuerySpec;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 查询规格 mini 语法解析：把模型友好的扁平字符串解析为结构化规格。
 *
 * <pre>
 * metrics: "fieldId:AGG"            例 "amount:SUM"
 * filters: "fieldId:OP:value"       例 "status:EQ:paid"、"day:BETWEEN:2026-08-01|2026-08-07"、
 *                                   多值 "tag:IN:a|b|c"、无参 "deleted_at:IS_NULL"
 * sorts:   "fieldId:ASC" / "fieldId:DESC"
 * </pre>
 * 解析失败抛出精确原因，由工具层回喂模型自纠。
 */
@ConditionalOnAgentEnabled
@Component
public class QuerySpecParser {

  public List<DatasetQuerySpec.Metric> parseMetrics(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<DatasetQuerySpec.Metric> metrics = new ArrayList<>();
    for (String item : raw) {
      String[] parts = split(item, 2, "metric");
      String aggregation = parts[1].trim().toUpperCase();
      try {
        metrics.add(new DatasetQuerySpec.Metric(parts[0].trim(), DatasetQuerySpec.Aggregation.valueOf(aggregation)));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "非法聚合 '" + parts[1] + "'，可选值：SUM/AVG/COUNT/COUNT_DISTINCT/MAX/MIN");
      }
    }
    return metrics;
  }

  public List<DatasetQuerySpec.Filter> parseFilters(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<DatasetQuerySpec.Filter> filters = new ArrayList<>();
    for (String item : raw) {
      // limit=3 保留值中的冒号（如时间 "12:30"）不被截断
      String[] segments = item.trim().split(":", 3);
      if (segments.length < 2) {
        throw new IllegalArgumentException("非法过滤条件 '" + item + "'，格式：fieldId:OP:value");
      }
      String operatorText = segments[1].trim().toUpperCase();
      DatasetQuerySpec.Operator operator;
      try {
        operator = DatasetQuerySpec.Operator.valueOf(operatorText);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("非法过滤操作符 '" + segments[1] + "'");
      }
      if (operator.needsNoValue()) {
        filters.add(new DatasetQuerySpec.Filter(segments[0].trim(), operator, null, null));
        continue;
      }
      if (segments.length < 3) {
        throw new IllegalArgumentException("过滤条件 '" + item + "' 缺少值。多值用 | 分隔");
      }
      List<String> values = List.of(segments[2].split("\\|"));
      if (operator.needsValues() && values.size() < 2) {
        throw new IllegalArgumentException("过滤条件 '" + item + "' 需要至少两个值（| 分隔）");
      }
      filters.add(
          new DatasetQuerySpec.Filter(
              segments[0].trim(),
              operator,
              operator.needsSingleValue() ? segments[2] : null,
              operator.needsValues() ? values : null));
    }
    return filters;
  }

  public List<DatasetQuerySpec.Sort> parseSorts(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<DatasetQuerySpec.Sort> sorts = new ArrayList<>();
    for (String item : raw) {
      String[] parts = split(item, 2, "sort");
      String direction = parts[1].trim().toUpperCase();
      if (!direction.equals("ASC") && !direction.equals("DESC")) {
        throw new IllegalArgumentException("非法排序方向 '" + parts[1] + "'，只支持 ASC/DESC");
      }
      sorts.add(new DatasetQuerySpec.Sort(parts[0].trim(), DatasetQuerySpec.Sort.Direction.valueOf(direction)));
    }
    return sorts;
  }

  private static String[] split(String item, int expectedParts, String usage) {
    String[] parts = item.trim().split(":");
    if (parts.length != expectedParts) {
      throw new IllegalArgumentException("非法 " + usage + " '" + item + "'");
    }
    return parts;
  }
}
