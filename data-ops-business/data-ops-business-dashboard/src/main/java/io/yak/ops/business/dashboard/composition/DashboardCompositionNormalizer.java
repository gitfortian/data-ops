package io.yak.ops.business.dashboard.composition;

import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.gateway.dataset.DashboardDatasetGateway;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Coordinates Dashboard composition normalization without owning individual policy rules. */
@Component
public class DashboardCompositionNormalizer {

  private final DashboardJsonPolicy json;
  private final DashboardWidgetPolicy widgets;
  private final DashboardFilterPolicy filters;
  private final DashboardInteractionPolicy interactions;
  private final DashboardDatasetGateway datasets;

  public DashboardCompositionNormalizer(
      DashboardJsonPolicy json,
      DashboardWidgetPolicy widgets,
      DashboardFilterPolicy filters,
      DashboardInteractionPolicy interactions,
      DashboardDatasetGateway datasets) {
    this.json = json;
    this.widgets = widgets;
    this.filters = filters;
    this.interactions = interactions;
    this.datasets = datasets;
  }

  public DashboardDraft normalize(DashboardDraft draft) {
    Objects.requireNonNull(draft, "draft");
    String name = required(draft.name(), "Dashboard 名称", 200);
    String description = optional(draft.description(), "Dashboard 描述", 2000);
    Long activeDatasetId = draft.activeDatasetId();
    if (activeDatasetId != null && activeDatasetId <= 0L) {
      activeDatasetId = null;
    }
    if (activeDatasetId != null) {
      datasets.requireExists(activeDatasetId);
    }

    Object theme = json.requireObject(draft.theme(), "Dashboard Theme", 16000);
    DashboardWidgetPolicy.Result widgetResult = widgets.normalize(draft.widgets());
    DashboardFilterPolicy.Result filterResult =
        filters.normalize(draft.globalFilters(), widgetResult.widgetKeys());

    return new DashboardDraft(
        name,
        description,
        activeDatasetId,
        theme,
        widgetResult.widgets(),
        filterResult.filters(),
        interactions.normalize(
            draft.interactions(), widgetResult.widgetKeys(), filterResult.filterKeys()));
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
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }
}
