import type { DatasetQueryPayload, PublishedDataset } from '@/services/dataset';

const normalizeForStableJson = (value: unknown): unknown => {
  if (Array.isArray(value)) return value.map(normalizeForStableJson);
  if (!value || typeof value !== 'object') return value;
  return Object.keys(value as Record<string, unknown>)
    .sort()
    .reduce<Record<string, unknown>>((result, key) => {
      result[key] = normalizeForStableJson((value as Record<string, unknown>)[key]);
      return result;
    }, {});
};

export const stableRuntimeStringify = (value: unknown) => (
  JSON.stringify(normalizeForStableJson(value))
);

/** Query identity deliberately excludes component type so line/bar can share the same raw result. */
export const createScreenRuntimeQueryKey = (
  dataset: PublishedDataset,
  payload: DatasetQueryPayload,
) => `${dataset.id}@${dataset.currentVersionNo ?? 'current'}:${stableRuntimeStringify(payload)}`;
