package io.yak.ops.business.dataset.query;

import io.yak.ops.business.dataset.DatasetAggregation;
import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFilter;
import io.yak.ops.business.dataset.DatasetMetricBinding;
import io.yak.ops.business.dataset.DatasetQueryColumnBinding;
import io.yak.ops.business.dataset.DatasetQueryRequest;
import io.yak.ops.business.dataset.DatasetSort;
import io.yak.ops.business.dataset.DatasetSortDirection;
import io.yak.ops.business.dataset.DatasetSqlSafety;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Compiles semantic Dataset bindings into a read-only query over one immutable source snapshot. */
@Component
public class DatasetQueryCompiler {

  static final int DEFAULT_LIMIT = 200;
  static final int MAX_LIMIT = 1000;
  private static final int MAX_FILTERS = 50;
  private static final int MAX_FILTER_VALUES = 100;
  private static final int MAX_VALUE_LENGTH = 4000;
  private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

  public CompiledQuery compile(
      String baseSql, List<DatasetField> fields, DatasetQueryRequest request) {
    String safeBaseSql = DatasetSqlSafety.requireReadOnlyQuery(baseSql);
    DatasetQueryRequest normalized =
        request == null
            ? new DatasetQueryRequest(null, List.of(), List.of(), List.of(), List.of(), null, null)
            : request;

    List<String> dimensions = copy(normalized.dimensions());
    List<DatasetMetricBinding> metrics = copy(normalized.metrics());
    List<DatasetFilter> filters = copy(normalized.filters());
    List<DatasetSort> sorts = copy(normalized.sorts());
    int limit = normalizeLimit(normalized.limit());
    int fetchRows = Math.min(MAX_LIMIT + 1, limit + 1);
    if (filters.size() > MAX_FILTERS) {
      throw new IllegalArgumentException("Dataset 查询过滤条件不能超过 50 个");
    }

    Map<String, DatasetField> fieldMap = new LinkedHashMap<>();
    for (DatasetField field : fields == null ? List.<DatasetField>of() : fields) {
      if (field == null || field.fieldId() == null || field.fieldId().isBlank()) {
        continue;
      }
      validateIdentifier(field.physicalName());
      fieldMap.put(field.fieldId(), field);
    }

    boolean rawMode = dimensions.isEmpty() && metrics.isEmpty();
    if (fieldMap.isEmpty() && (!filters.isEmpty() || !sorts.isEmpty() || !rawMode)) {
      throw new IllegalArgumentException("当前 DatasetVersion 尚未定义字段 schema，暂不能使用语义查询条件");
    }

    List<DatasetQueryColumnBinding> bindings = new ArrayList<>();
    List<String> projections = new ArrayList<>();
    List<String> groupBy = new ArrayList<>();
    Map<String, String> dimensionAliases = new HashMap<>();
    Map<String, String> metricAliases = new HashMap<>();
    Set<String> seenDimensions = new HashSet<>();
    Set<String> seenMetrics = new HashSet<>();

    if (rawMode) {
      projections.add("yak_dataset_source.*");
    } else {
      for (String fieldId : dimensions) {
        DatasetField field = requireField(fieldMap, fieldId);
        if (!seenDimensions.add(field.fieldId())) {
          throw new IllegalArgumentException("维度字段重复：" + field.fieldId());
        }
        String alias = "d" + bindings.size();
        String expression = ref(field);
        projections.add(expression + " AS " + alias);
        groupBy.add(expression);
        dimensionAliases.put(field.fieldId(), alias);
        bindings.add(
            new DatasetQueryColumnBinding(
                alias, field.fieldId(), field.displayName(), field.dataType(), null));
      }

      for (DatasetMetricBinding metric : metrics) {
        if (metric == null || metric.aggregation() == null) {
          throw new IllegalArgumentException("指标聚合方式不能为空");
        }
        DatasetField field = requireField(fieldMap, metric.fieldId());
        String metricKey = metricKey(field.fieldId(), metric.aggregation());
        if (!seenMetrics.add(metricKey)) {
          throw new IllegalArgumentException(
              "指标重复：" + field.fieldId() + "/" + metric.aggregation());
        }
        String alias = "m" + bindings.size();
        projections.add(metricExpression(field, metric.aggregation()) + " AS " + alias);
        metricAliases.put(metricKey, alias);
        bindings.add(
            new DatasetQueryColumnBinding(
                alias,
                field.fieldId(),
                field.displayName(),
                metricResultType(field, metric.aggregation()),
                metric.aggregation()));
      }
    }

    StringBuilder sql =
        new StringBuilder("SELECT ")
            .append(String.join(", ", projections))
            .append(" FROM (")
            .append(safeBaseSql)
            .append(") yak_dataset_source");

    List<String> where = filters.stream().map(filter -> compileFilter(fieldMap, filter)).toList();
    if (!where.isEmpty()) {
      sql.append(" WHERE ").append(String.join(" AND ", where));
    }
    if (!groupBy.isEmpty()) {
      sql.append(" GROUP BY ").append(String.join(", ", groupBy));
    }

    if (!sorts.isEmpty()) {
      List<String> orderBy = new ArrayList<>();
      for (DatasetSort sort : sorts) {
        if (sort == null) {
          throw new IllegalArgumentException("排序条件不能为空");
        }
        DatasetField field = requireField(fieldMap, sort.fieldId());
        DatasetSortDirection direction =
            sort.direction() == null ? DatasetSortDirection.ASC : sort.direction();
        String expression;
        if (rawMode) {
          expression = ref(field);
        } else if (sort.aggregation() != null) {
          expression = metricAliases.get(metricKey(field.fieldId(), sort.aggregation()));
          if (expression == null) {
            throw new IllegalArgumentException("排序指标必须先出现在 metrics 中：" + field.fieldId());
          }
        } else {
          expression = dimensionAliases.get(field.fieldId());
          if (expression == null) {
            throw new IllegalArgumentException("排序维度必须先出现在 dimensions 中：" + field.fieldId());
          }
        }
        orderBy.add(expression + " " + direction.name());
      }
      sql.append(" ORDER BY ").append(String.join(", ", orderBy));
    }

    sql.append(" LIMIT ").append(fetchRows);
    return new CompiledQuery(sql.toString(), List.copyOf(bindings), limit, fetchRows);
  }

  private String compileFilter(Map<String, DatasetField> fieldMap, DatasetFilter filter) {
    if (filter == null || filter.operator() == null) {
      throw new IllegalArgumentException("过滤条件和操作符不能为空");
    }
    DatasetField field = requireField(fieldMap, filter.fieldId());
    String expression = ref(field);
    return switch (filter.operator()) {
      case IS_NULL -> expression + " IS NULL";
      case IS_NOT_NULL -> expression + " IS NOT NULL";
      case EQ ->
          filter.value() == null
              ? expression + " IS NULL"
              : expression + " = " + literal(filter.value());
      case NE ->
          filter.value() == null
              ? expression + " IS NOT NULL"
              : expression + " <> " + literal(filter.value());
      case GT -> expression + " > " + literalRequired(filter.value());
      case GTE -> expression + " >= " + literalRequired(filter.value());
      case LT -> expression + " < " + literalRequired(filter.value());
      case LTE -> expression + " <= " + literalRequired(filter.value());
      case LIKE -> expression + " LIKE " + literalRequired(filter.value());
      case NOT_LIKE -> expression + " NOT LIKE " + literalRequired(filter.value());
      case IN -> expression + " IN (" + literals(filter.values()) + ")";
      case NOT_IN -> expression + " NOT IN (" + literals(filter.values()) + ")";
      case BETWEEN -> {
        List<Object> values = filter.values() == null ? List.of() : filter.values();
        if (values.size() != 2) {
          throw new IllegalArgumentException("BETWEEN 必须提供两个 values");
        }
        yield expression
            + " BETWEEN "
            + literalRequired(values.get(0))
            + " AND "
            + literalRequired(values.get(1));
      }
    };
  }

  private String literals(List<Object> values) {
    List<Object> normalized = values == null ? List.of() : values;
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("IN / NOT_IN 的 values 不能为空");
    }
    if (normalized.size() > MAX_FILTER_VALUES) {
      throw new IllegalArgumentException("单个 IN 条件最多支持 100 个值");
    }
    return normalized.stream()
        .map(this::literalRequired)
        .reduce((left, right) -> left + ", " + right)
        .orElseThrow();
  }

  private String literalRequired(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("当前过滤操作符不接受 null");
    }
    return literal(value);
  }

  private String literal(Object value) {
    if (value == null) {
      return "NULL";
    }
    if (value instanceof Boolean bool) {
      return bool ? "TRUE" : "FALSE";
    }
    if (value instanceof Number number) {
      if (number instanceof Double decimal && !Double.isFinite(decimal)) {
        throw new IllegalArgumentException("过滤数值非法");
      }
      if (number instanceof Float decimal && !Float.isFinite(decimal)) {
        throw new IllegalArgumentException("过滤数值非法");
      }
      try {
        return new BigDecimal(number.toString()).toPlainString();
      } catch (NumberFormatException exception) {
        throw new IllegalArgumentException("过滤数值非法", exception);
      }
    }
    if (value instanceof String text) {
      if (text.length() > MAX_VALUE_LENGTH) {
        throw new IllegalArgumentException("单个过滤值不能超过 4000 个字符");
      }
      return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'";
    }
    throw new IllegalArgumentException("过滤值仅支持 string / number / boolean / null");
  }

  private String metricExpression(DatasetField field, DatasetAggregation aggregation) {
    String value = ref(field);
    return switch (aggregation) {
      case SUM -> "SUM(" + value + ")";
      case AVG -> "AVG(" + value + ")";
      case COUNT -> "COUNT(" + value + ")";
      case COUNT_DISTINCT -> "COUNT(DISTINCT " + value + ")";
      case MAX -> "MAX(" + value + ")";
      case MIN -> "MIN(" + value + ")";
    };
  }

  private DatasetFieldDataType metricResultType(
      DatasetField field, DatasetAggregation aggregation) {
    return switch (aggregation) {
      case SUM, AVG, COUNT, COUNT_DISTINCT -> DatasetFieldDataType.NUMBER;
      case MAX, MIN -> field.dataType();
    };
  }

  private DatasetField requireField(Map<String, DatasetField> fieldMap, String fieldId) {
    if (fieldId == null || fieldId.isBlank()) {
      throw new IllegalArgumentException("fieldId 不能为空");
    }
    DatasetField field = fieldMap.get(fieldId.trim());
    if (field == null) {
      throw new IllegalArgumentException("Dataset 字段不存在：" + fieldId);
    }
    return field;
  }

  private String ref(DatasetField field) {
    validateIdentifier(field.physicalName());
    return "yak_dataset_source." + field.physicalName();
  }

  private void validateIdentifier(String value) {
    if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Dataset 语义查询仅支持安全的简单物理字段名 [A-Za-z_][A-Za-z0-9_$]*，当前=" + value);
    }
  }

  private String metricKey(String fieldId, DatasetAggregation aggregation) {
    return fieldId + "|" + aggregation.name().toUpperCase(Locale.ROOT);
  }

  private int normalizeLimit(Integer value) {
    if (value == null) {
      return DEFAULT_LIMIT;
    }
    if (value < 1 || value > MAX_LIMIT) {
      throw new IllegalArgumentException("limit 必须在 1~1000 之间");
    }
    return value;
  }

  private static <T> List<T> copy(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  public record CompiledQuery(
      String sql, List<DatasetQueryColumnBinding> bindings, int limit, int fetchRows) {}
}
