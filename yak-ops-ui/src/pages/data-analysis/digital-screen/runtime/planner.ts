import type { ScreenTemplate } from '@/components/screen-engine';
import type { PublishedDataset } from '@/services/dataset';
import type { DigitalScreenBindings } from '@/services/digital-screen';
import {
  buildScreenDatasetQueryPayload,
  canQueryScreenComponent,
  isBindableScreenComponent,
} from './binding';
import type { ScreenRuntimeCandidate } from './model';
import { createScreenRuntimeQueryKey } from './query-key';

/** Builds executable candidates, including the stable query identity used by PR 4's executor. */
export const planScreenRuntimeQueries = (
  template: ScreenTemplate | undefined,
  bindings: DigitalScreenBindings,
  datasets: PublishedDataset[],
): ScreenRuntimeCandidate[] => {
  if (!template) return [];
  const datasetMap = new Map(datasets.map((dataset) => [dataset.id, dataset]));
  return template.components.flatMap((component) => {
    const binding = bindings[component.id];
    if (!binding || !canQueryScreenComponent(component, binding)) return [];
    const dataset = datasetMap.get(binding.datasetId);
    if (!dataset) return [];
    const payload = buildScreenDatasetQueryPayload(component, binding);
    return [{
      component,
      binding,
      dataset,
      payload,
      queryKey: createScreenRuntimeQueryKey(dataset, payload),
    }];
  });
};

/** Dataset ids the planner could execute once metadata is available. */
export const collectScreenRuntimeDatasetIds = (
  template: ScreenTemplate | undefined,
  bindings: DigitalScreenBindings,
) => {
  if (!template) return [];
  return [...new Set(template.components.flatMap((component) => {
    const binding = bindings[component.id];
    return binding && canQueryScreenComponent(component, binding)
      ? [binding.datasetId]
      : [];
  }))];
};

export const countBoundScreenComponents = (
  template: ScreenTemplate | undefined,
  bindings: DigitalScreenBindings,
) => template?.components.filter((component) => (
  isBindableScreenComponent(component) && Boolean(bindings[component.id])
)).length ?? 0;
