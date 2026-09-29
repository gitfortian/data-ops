package io.yak.ops.business.dashboard.domain;

import java.util.List;

/** 保存 Dashboard 新版本所需的领域草稿。 */
public record DashboardDraft(
    String name,
    String description,
    Long activeDatasetId,
    Object theme,
    List<WidgetSpec> widgets,
    List<GlobalFilterSpec> globalFilters,
    List<InteractionSpec> interactions) {
}
