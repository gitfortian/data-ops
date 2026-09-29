import type { ScreenComponent } from '@/components/screen-engine';
import {
  queryDataset,
  type DatasetQueryPayload,
  type DatasetQueryResult,
  type PublishedDataset,
} from '@/services/dataset';
import type { DigitalScreenComponentBinding } from '@/services/digital-screen';
import {
  buildScreenDatasetQueryPayload,
  canQueryScreenComponent,
} from './binding';
import { screenRuntimeComponentRegistry } from './registry/builtin-plugins';

export interface ScreenRuntimeQueryOptions {
  signal?: AbortSignal;
}

export const toScreenComponentData = (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
  dataset: PublishedDataset,
  result: DatasetQueryResult,
) => screenRuntimeComponentRegistry.adaptData(component, binding, { dataset, result });

export const queryScreenDatasetResult = (
  dataset: PublishedDataset,
  payload: DatasetQueryPayload,
  options?: ScreenRuntimeQueryOptions,
) => queryDataset(dataset.id, payload, { signal: options?.signal });

/** Compatibility helper for direct component callers. Runtime execution should use the batch executor. */
export const queryScreenComponentData = async (
  component: ScreenComponent,
  binding: DigitalScreenComponentBinding,
  dataset: PublishedDataset,
  options?: ScreenRuntimeQueryOptions,
) => {
  if (!canQueryScreenComponent(component, binding)) return undefined;
  const result = await queryScreenDatasetResult(
    dataset,
    buildScreenDatasetQueryPayload(component, binding),
    options,
  );
  return toScreenComponentData(component, binding, dataset, result);
};
