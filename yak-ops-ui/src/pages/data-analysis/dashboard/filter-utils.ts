import type {
  AnalysisAsset,
  DashboardGlobalFilter,
  DashboardWidget,
  DatasetField,
  DatasetFieldType,
  PublishedDataset,
} from './model';

export const isDateFieldType = (type?: DatasetFieldType) =>
  type === 'date' || type === 'datetime';

export const resolveWidgetDataset = (
  widget: DashboardWidget,
  datasets: PublishedDataset[],
  analyses: AnalysisAsset[],
): PublishedDataset | undefined => {
  const datasetId = widget.analysisId
    ? analyses.find((analysis) => analysis.id === widget.analysisId)?.datasetId
    : widget.inlineAnalysis?.datasetId;
  return datasetId ? datasets.find((dataset) => dataset.id === datasetId) : undefined;
};

export const resolveBindingField = (
  widgetId: string,
  fieldId: string,
  widgets: DashboardWidget[],
  datasets: PublishedDataset[],
  analyses: AnalysisAsset[],
): DatasetField | undefined => {
  const widget = widgets.find((item) => item.id === widgetId);
  if (!widget) return undefined;
  return resolveWidgetDataset(widget, datasets, analyses)?.fields.find((field) => field.key === fieldId);
};

export const resolveFilterFieldType = (
  filter: DashboardGlobalFilter,
  widgets: DashboardWidget[],
  datasets: PublishedDataset[],
  analyses: AnalysisAsset[],
): DatasetFieldType | undefined => {
  for (const binding of filter.bindings) {
    const field = resolveBindingField(
      binding.widgetId,
      binding.field,
      widgets,
      datasets,
      analyses,
    );
    if (field) return field.dataType;
  }
  return undefined;
};

/**
 * Stage 3 keeps the persisted filter domain small. Date controls are inferred from
 * their bound Dataset field; the date-filter prefix preserves intent while a new
 * date filter is being configured before all bindings are complete.
 */
export const isDateFilter = (
  filter: DashboardGlobalFilter,
  widgets: DashboardWidget[],
  datasets: PublishedDataset[],
  analyses: AnalysisAsset[],
) => filter.id.startsWith('date-filter-')
  || isDateFieldType(resolveFilterFieldType(filter, widgets, datasets, analyses));
