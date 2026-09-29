import type { ProductType } from './types';

export const consumptionProductPath = (productType: ProductType, sourceIdentity: string) => (
  `/data-analysis/consumption/${encodeURIComponent(`${productType}:${sourceIdentity}`)}`
);

export const consumptionPathForAssetSource = (
  sourceType?: string,
  sourceId?: string,
): string | undefined => {
  if (!sourceId) return undefined;
  if (sourceType === 'DATASET') return consumptionProductPath('DATASET', sourceId);
  if (sourceType === 'DATA_SERVICE') return consumptionProductPath('DATA_SERVICE', sourceId);
  return undefined;
};
