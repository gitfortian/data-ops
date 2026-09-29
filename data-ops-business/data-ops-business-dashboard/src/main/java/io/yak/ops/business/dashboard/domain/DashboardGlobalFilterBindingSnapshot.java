package io.yak.ops.business.dashboard.domain;

public record DashboardGlobalFilterBindingSnapshot(
    String widgetKey,
    String fieldId,
    int sortOrder) {
}
