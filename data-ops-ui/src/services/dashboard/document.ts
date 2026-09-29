import { normalizeDashboardTheme } from './theme';
import type {
  DashboardCrossFilterRule,
  DashboardDocument,
  DashboardGlobalFilterBinding,
  DashboardInteraction,
  DashboardWidget,
} from './types';

const nonEmpty = (value: string | undefined) => Boolean(value?.trim());

const uniqueBy = <T,>(items: T[], keyOf: (item: T) => string) => {
  const seen = new Set<string>();
  return items.filter((item) => {
    const key = keyOf(item);
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

const normalizeBindings = (
  bindings: DashboardGlobalFilterBinding[],
  widgetIds: Set<string>,
) =>
  uniqueBy(
    bindings.filter(
      (binding) => widgetIds.has(binding.widgetId) && nonEmpty(binding.field),
    ),
    (binding) => `${binding.widgetId}\u0000${binding.field}`,
  );

const normalizeCrossFilters = (
  rules: DashboardCrossFilterRule[],
  widgetIds: Set<string>,
) => {
  const ids = new Set<string>();
  const pairs = new Set<string>();
  return rules.filter((rule) => {
    if (
      !nonEmpty(rule.id) ||
      !nonEmpty(rule.sourceField) ||
      !widgetIds.has(rule.targetWidgetId) ||
      !nonEmpty(rule.targetField) ||
      ids.has(rule.id)
    ) {
      return false;
    }
    const pair = `${rule.sourceField}\u0000${rule.targetWidgetId}\u0000${rule.targetField}`;
    if (pairs.has(pair)) return false;
    ids.add(rule.id);
    pairs.add(pair);
    return true;
  });
};

const normalizeWidget = (
  widget: DashboardWidget,
  widgetIds: Set<string>,
): DashboardWidget => {
  const spec = widget.inlineAnalysis;
  const behavior = spec?.dashboardBehavior;
  if (!spec || !behavior) return widget;
  const crossFilters = normalizeCrossFilters(
    Array.isArray(behavior.crossFilters) ? behavior.crossFilters : [],
    widgetIds,
  );
  return {
    ...widget,
    inlineAnalysis: {
      ...spec,
      dashboardBehavior: {
        ...behavior,
        crossFilters: crossFilters.length ? crossFilters : undefined,
      },
    },
  };
};

const normalizeInteractions = (
  interactions: DashboardInteraction[],
  widgetIds: Set<string>,
  filterIds: Set<string>,
) => {
  const ids = new Set<string>();
  const pairs = new Set<string>();
  return interactions.filter((interaction) => {
    if (
      !nonEmpty(interaction.id) ||
      !widgetIds.has(interaction.sourceWidgetId) ||
      !nonEmpty(interaction.sourceField) ||
      !filterIds.has(interaction.targetFilterId) ||
      ids.has(interaction.id)
    ) {
      return false;
    }
    const pair = `${interaction.sourceWidgetId}\u0000${interaction.sourceField}\u0000${interaction.targetFilterId}`;
    if (pairs.has(pair)) return false;
    ids.add(interaction.id);
    pairs.add(pair);
    return true;
  });
};

export const normalizeDashboardDocument = (
  document: DashboardDocument,
): DashboardDocument => {
  const widgets = uniqueBy(
    (document.widgets ?? []).filter((widget) => nonEmpty(widget.id)),
    (widget) => widget.id,
  );
  const widgetIds = new Set(widgets.map((widget) => widget.id));
  const normalizedWidgets = widgets.map((widget) =>
    normalizeWidget(widget, widgetIds),
  );
  const globalFilters = uniqueBy(
    (document.globalFilters ?? []).filter((filter) => nonEmpty(filter.id)),
    (filter) => filter.id,
  ).map((filter) => ({
    ...filter,
    bindings: normalizeBindings(
      Array.isArray(filter.bindings) ? filter.bindings : [],
      widgetIds,
    ),
  }));
  const filterIds = new Set(globalFilters.map((filter) => filter.id));
  const interactions = normalizeInteractions(
    Array.isArray(document.interactions) ? document.interactions : [],
    widgetIds,
    filterIds,
  );

  return {
    ...document,
    version: 1,
    theme: normalizeDashboardTheme(document.theme),
    widgets: normalizedWidgets,
    globalFilters,
    interactions,
  };
};

export const stripDashboardWidgetReferences = (
  document: DashboardDocument,
  widgetId: string,
  removeWidget: boolean,
): DashboardDocument =>
  normalizeDashboardDocument({
    ...document,
    widgets: (document.widgets ?? [])
      .filter((widget) => !removeWidget || widget.id !== widgetId)
      .map((widget) => {
        if (!widget.inlineAnalysis?.dashboardBehavior) return widget;
        const behavior = widget.inlineAnalysis.dashboardBehavior;
        const crossFilters = (
          Array.isArray(behavior.crossFilters) ? behavior.crossFilters : []
        ).filter(
          (rule) => widget.id !== widgetId && rule.targetWidgetId !== widgetId,
        );
        return {
          ...widget,
          inlineAnalysis: {
            ...widget.inlineAnalysis,
            dashboardBehavior: {
              ...behavior,
              crossFilters: crossFilters.length ? crossFilters : undefined,
            },
          },
        };
      }),
    globalFilters: (document.globalFilters ?? []).map((filter) => ({
      ...filter,
      bindings: (filter.bindings ?? []).filter(
        (binding) => binding.widgetId !== widgetId,
      ),
    })),
    interactions: (document.interactions ?? []).filter(
      (interaction) => interaction.sourceWidgetId !== widgetId,
    ),
  });
