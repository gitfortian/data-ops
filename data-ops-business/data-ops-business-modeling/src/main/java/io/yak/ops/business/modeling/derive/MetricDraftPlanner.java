package io.yak.ops.business.modeling.derive;

import io.yak.ops.common.api.metric.MetricQueryView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Plans metric-derived dimensions and measures without writes or domain lookups. */
final class MetricDraftPlanner {
  private MetricDraftPlanner() {}
  private static final Set<String> AGGREGATE_FUNCS = Set.of("SUM", "COUNT", "COUNT_DISTINCT", "MAX", "MIN", "AVG");
  record Measure(String sourceColumn, String aggregateFunc, String landingName) {}
  record Plan(String statPeriod, List<Measure> measures, List<String> dimensions) {}

  static Plan plan(List<MetricQueryView> metrics) {
    String period = normalizeStatPeriod(metrics.stream().map(MetricQueryView::statPeriod)
        .filter(StringUtils::hasText).findFirst().orElse(null));
    List<Measure> measures = new ArrayList<>();
    Set<String> dimensions = new LinkedHashSet<>();
    for (MetricQueryView metric : metrics) {
      Measure measure = parseMeasure(metric);
      if (measure != null) measures.add(measure);
      dimensions.addAll(parseJsonStringArray(metric.statDimensions()));
    }
    return new Plan(period, List.copyOf(measures), List.copyOf(dimensions));
  }

  /** 指标统计周期(DAY/WEEK/MONTH)→ 建模周期约定(1d/1w/1m);无法识别时交回用户选择。 */
  private static String normalizeStatPeriod(String metricPeriod) {
    if (!StringUtils.hasText(metricPeriod)) {
      return null;
    }
    return switch (metricPeriod.trim().toUpperCase(Locale.ROOT)) {
      case "HOUR", "1h" -> "1h";
      case "DAY", "1d" -> "1d";
      case "WEEK", "1w" -> "1w";
      case "MONTH", "1m" -> "1m";
      default -> null;
    };
  }

  /** 度量表达式解析:SUM(order_amount) / COUNT(DISTINCT order_id) / AVG(x) 等。 */
  private static Measure parseMeasure(MetricQueryView metric) {
    if (!StringUtils.hasText(metric.measureExpr())) {
      return null;
    }
    Matcher matcher = MEASURE_PATTERN.matcher(metric.measureExpr().trim());
    if (!matcher.matches()) {
      return null;
    }
    String rawFunc = matcher.group(1).toUpperCase(Locale.ROOT);
    boolean distinct = matcher.group(2) != null;
    String field = matcher.group(3);
    String func = rawFunc.equals("COUNT") && distinct ? "COUNT_DISTINCT" : rawFunc;
    if (!AGGREGATE_FUNCS.contains(func)) {
      func = "SUM";
    }
    return new Measure(field, func, func.toLowerCase(Locale.ROOT) + "_" + field.toLowerCase(Locale.ROOT));
  }

  /** JSON 字符串数组(如 ["order_date","order_city"]) → 元素列表;非 JSON 原样按逗号拆。 */
  private static List<String> parseJsonStringArray(String raw) {
    if (!StringUtils.hasText(raw)) {
      return List.of();
    }
    Matcher matcher = JSON_STRING_ARRAY_PATTERN.matcher(raw.trim());
    List<String> values = new ArrayList<>();
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
    if (!values.isEmpty()) {
      return values;
    }
    return List.of(raw.split(",")).stream().map(String::trim).filter(StringUtils::hasText).toList();
  }

  private static final Pattern MEASURE_PATTERN =
      Pattern.compile("^([A-Za-z_]+)\\s*\\(\\s*(DISTINCT\\s+)?([A-Za-z0-9_.]+)\\s*\\)\\s*$");

  private static final Pattern JSON_STRING_ARRAY_PATTERN = Pattern.compile("\"([^\"]*)\"");

}
