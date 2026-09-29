package io.yak.ops.business.dashboard.composition;

import io.yak.ops.business.dashboard.domain.WidgetSpec;
import io.yak.ops.business.dashboard.gateway.analysis.DashboardAnalysisGateway;
import io.yak.ops.business.dashboard.gateway.dataset.DashboardDatasetGateway;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Normalizes widgets and enforces linked-Analysis XOR inline-Analysis semantics. */
@Component
public class DashboardWidgetPolicy {

  private static final int MAX_WIDGETS = 200;
  private static final int MAX_INLINE_JSON = 65535;

  private final DashboardAnalysisGateway analyses;
  private final DashboardDatasetGateway datasets;
  private final DashboardLayoutPolicy layout;
  private final DashboardJsonPolicy json;

  public DashboardWidgetPolicy(
      DashboardAnalysisGateway analyses,
      DashboardDatasetGateway datasets,
      DashboardLayoutPolicy layout,
      DashboardJsonPolicy json) {
    this.analyses = analyses;
    this.datasets = datasets;
    this.layout = layout;
    this.json = json;
  }

  public Result normalize(List<WidgetSpec> values) {
    List<WidgetSpec> source = values == null ? List.of() : values;
    if (source.size() > MAX_WIDGETS) {
      throw new IllegalArgumentException("Dashboard 组件不能超过 " + MAX_WIDGETS + " 个");
    }

    List<WidgetSpec> normalized = new ArrayList<>(source.size());
    Set<String> widgetKeys = new HashSet<>();
    for (WidgetSpec value : source) {
      if (value == null) {
        throw new IllegalArgumentException("DashboardWidget 不能为空");
      }
      String widgetKey = required(value.widgetKey(), "widgetKey", 64);
      if (!widgetKeys.add(widgetKey)) {
        throw new IllegalArgumentException("widgetKey 重复：" + widgetKey);
      }

      boolean linked = value.analysisId() != null;
      boolean inline = value.inlineAnalysis() != null;
      if (linked == inline) {
        throw new IllegalArgumentException(
            "Widget 必须且只能选择 analysisId 或 inlineAnalysis：" + widgetKey);
      }

      Object inlineAnalysis = inline
          ? json.requireObject(value.inlineAnalysis(), "inlineAnalysis：" + widgetKey, MAX_INLINE_JSON)
          : null;
      if (linked) {
        if (value.analysisId() <= 0L) {
          throw new IllegalArgumentException("analysisId 必须大于 0");
        }
        analyses.requireExists(value.analysisId());
      } else {
        requireInlineDataset(inlineAnalysis, widgetKey);
      }

      layout.validate(value, widgetKey);
      normalized.add(new WidgetSpec(
          widgetKey,
          value.analysisId(),
          optional(value.title(), "Widget 标题", 200),
          inlineAnalysis,
          value.x(),
          value.y(),
          value.w(),
          value.h(),
          value.minW(),
          value.minH()));
    }
    return new Result(List.copyOf(normalized), Set.copyOf(widgetKeys));
  }

  private void requireInlineDataset(Object inlineAnalysis, String widgetKey) {
    Long datasetId = json.optionalPositiveLongField(
        inlineAnalysis,
        "datasetId",
        "inlineAnalysis：" + widgetKey);
    if (datasetId != null) {
      datasets.requireExists(datasetId);
    }
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

  private String optional(String value, String label, int maxLength) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }

  public record Result(List<WidgetSpec> widgets, Set<String> widgetKeys) {
  }
}
