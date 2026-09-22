package io.yak.ops.business.dashboard.domain;

/** Dashboard 组件版本快照。 */
public record DashboardWidgetSnapshot(
    long id,
    long dashboardVersionId,
    String widgetKey,
    Long analysisId,
    String title,
    Object inlineAnalysis,
    int x,
    int y,
    int w,
    int h,
    Integer minW,
    Integer minH,
    int sortOrder) {
}
