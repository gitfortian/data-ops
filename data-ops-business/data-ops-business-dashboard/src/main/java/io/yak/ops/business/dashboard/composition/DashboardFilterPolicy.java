package io.yak.ops.business.dashboard.composition;

import io.yak.ops.business.dashboard.domain.FilterBindingSpec;
import io.yak.ops.business.dashboard.domain.GlobalFilterSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Normalizes Dashboard global filters and widget-field bindings. */
@Component
public class DashboardFilterPolicy {

  private static final int MAX_GLOBAL_FILTERS = 20;
  private static final int MAX_FILTER_BINDINGS = 200;
  private static final int MAX_DEFAULT_VALUE_JSON = 4000;

  private final DashboardJsonPolicy json;

  public DashboardFilterPolicy(DashboardJsonPolicy json) {
    this.json = json;
  }

  public Result normalize(List<GlobalFilterSpec> values, Set<String> widgetKeys) {
    List<GlobalFilterSpec> source = values == null ? List.of() : values;
    if (source.size() > MAX_GLOBAL_FILTERS) {
      throw new IllegalArgumentException(
          "Dashboard 全局筛选器不能超过 " + MAX_GLOBAL_FILTERS + " 个");
    }

    List<GlobalFilterSpec> normalized = new ArrayList<>(source.size());
    Set<String> filterKeys = new HashSet<>();
    int bindingCount = 0;
    for (GlobalFilterSpec value : source) {
      if (value == null) {
        throw new IllegalArgumentException("Dashboard 全局筛选器不能为空");
      }
      String filterKey = required(value.filterKey(), "filterKey", 64);
      if (!filterKeys.add(filterKey)) {
        throw new IllegalArgumentException("filterKey 重复：" + filterKey);
      }

      List<FilterBindingSpec> sourceBindings = value.bindings() == null ? List.of() : value.bindings();
      List<FilterBindingSpec> bindings = new ArrayList<>(sourceBindings.size());
      Set<String> boundWidgets = new HashSet<>();
      for (FilterBindingSpec binding : sourceBindings) {
        if (binding == null) {
          throw new IllegalArgumentException("筛选器绑定不能为空：" + filterKey);
        }
        String widgetKey = required(binding.widgetKey(), "筛选器 widgetKey", 64);
        if (!widgetKeys.contains(widgetKey)) {
          throw new IllegalArgumentException("筛选器绑定的 Widget 不存在：" + widgetKey);
        }
        if (!boundWidgets.add(widgetKey)) {
          throw new IllegalArgumentException(
              "同一筛选器对单个 Widget 只能绑定一个字段：" + widgetKey);
        }
        bindings.add(new FilterBindingSpec(
            widgetKey,
            required(binding.fieldId(), "筛选器 fieldId", 64)));
        bindingCount++;
        if (bindingCount > MAX_FILTER_BINDINGS) {
          throw new IllegalArgumentException(
              "Dashboard 筛选器字段映射不能超过 " + MAX_FILTER_BINDINGS + " 个");
        }
      }

      normalized.add(new GlobalFilterSpec(
          filterKey,
          required(value.name(), "筛选器名称", 200),
          Objects.requireNonNull(value.operator(), "筛选器 operator"),
          json.requireScalar(
              value.defaultValue(),
              "全局筛选器默认值：" + filterKey,
              MAX_DEFAULT_VALUE_JSON),
          List.copyOf(bindings)));
    }
    return new Result(List.copyOf(normalized), Set.copyOf(filterKeys));
  }

  private String required(String value, String label, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + "不能为空");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  public record Result(List<GlobalFilterSpec> filters, Set<String> filterKeys) {
  }
}
