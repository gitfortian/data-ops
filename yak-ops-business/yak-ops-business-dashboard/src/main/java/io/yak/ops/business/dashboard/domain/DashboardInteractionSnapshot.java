package io.yak.ops.business.dashboard.domain;

/** Dashboard 联动规则版本快照。 */
public record DashboardInteractionSnapshot(
    String interactionKey,
    DashboardInteractionEvent event,
    String sourceWidgetKey,
    String sourceFieldId,
    String targetFilterKey,
    int sortOrder) {
}
