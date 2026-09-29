package io.yak.ops.business.dashboard.domain;

public record InteractionSpec(
    String interactionKey,
    DashboardInteractionEvent event,
    String sourceWidgetKey,
    String sourceFieldId,
    String targetFilterKey) {
}
