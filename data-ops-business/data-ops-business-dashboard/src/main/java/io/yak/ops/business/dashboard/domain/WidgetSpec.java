package io.yak.ops.business.dashboard.domain;

public record WidgetSpec(
    String widgetKey,
    Long analysisId,
    String title,
    Object inlineAnalysis,
    int x,
    int y,
    int w,
    int h,
    Integer minW,
    Integer minH) {
}
